package com.flagsmith.threads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.config.Retry;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RequestProcessor {

  private ExecutorService executor = Executors.newFixedThreadPool(3);
  @Getter
  private OkHttpClient client;
  @Getter
  @Setter
  private FlagsmithLogger logger;
  private Retry retries = new Retry(3);

  public RequestProcessor(OkHttpClient client, FlagsmithLogger logger) {
    this(client, logger, new Retry(3));
  }

  /**
   * Instantiate with client, logger and retries.
   *
   * @param client client instance
   * @param logger logger instance
   * @param retries retries
   */
  public RequestProcessor(OkHttpClient client, FlagsmithLogger logger, Retry retries) {
    this.client = client;
    this.logger = logger;
    this.retries = retries;
  }

  /**
   * Execute the request in async mode.
   *
   * @param request request to invoke
   * @param clazz class type of response
   * @param doThrow should throw Exception (boolean)
   * @param <T> Type inference for the response
   */
  public <T> Future<T> executeAsync(Request request, TypeReference<T> clazz, Boolean doThrow) {
    return executeAsync(request, clazz, doThrow, retries);
  }

  /**
   * Execute the response in async mode and do not unmarshall.
   *
   * @param request request to invoke
   * @param doThrow whether to throw exception or not
   */
  public Future<JsonNode> executeAsync(Request request, Boolean doThrow) {
    return executeAsync(request, new TypeReference<JsonNode>() {}, doThrow, retries);
  }

  /**
   * Execute the response in async mode.
   *
   * @param request Request object
   * @param clazz class type of response
   * @param doThrow should throw Exception
   * @param retries no of retries before failing
   * @param <T> Type inference for the response
   */
  public <T> Future<T> executeAsync(
      Request request, TypeReference<T> clazz, Boolean doThrow, Retry retries) {
    return submit(request, clazz, doThrow, retries);
  }

  /**
   * Execute the request in async mode, returning a future callers can compose on.
   *
   * @param request request to send
   * @param clazz type to unmarshal the response body into
   * @param doThrow whether a failed call completes the future exceptionally
   * @param retries retry policy, copied for this call
   * @param <T> response type
   * @return a future completed with the unmarshalled response, or null when the call failed and
   *     doThrow is false
   */
  public <T> CompletableFuture<T> submit(
      Request request, TypeReference<T> clazz, Boolean doThrow, Retry retries) {
    return submit(request,
        response -> MapperFactory.getMapper().readValue(response.body().string(), clazz),
        doThrow, retries);
  }

  /**
   * Execute the request in async mode, building the result from the successful response.
   *
   * @param request request to invoke
   * @param reader reads a successful response into the result
   * @param doThrow should throw Exception (boolean)
   * @param <T> Type inference for the response
   */
  public <T> CompletableFuture<T> submit(
      Request request, ResponseReader<T> reader, Boolean doThrow) {
    return submit(request, reader, doThrow, retries);
  }

  /**
   * Execute the request in async mode, building the result from the successful response.
   *
   * @param request Request object
   * @param reader reads a successful response into the result
   * @param doThrow should throw Exception
   * @param retries no of retries before failing
   * @param <T> Type inference for the response
   * @return a future completed with the reader's result, or null when the call failed and
   *     doThrow is false
   */
  public <T> CompletableFuture<T> submit(
      Request request, ResponseReader<T> reader, Boolean doThrow, Retry retries) {
    CompletableFuture<T> completableFuture = new CompletableFuture<>();
    Retry localRetry = retries.toBuilder().build();
    // run the execute method in a fixed thread with retries.
    executor.submit(() -> {
      // retry until local retry reaches 0
      try {
        Integer statusCode = null;
        do {
          Call call = getClient().newCall(request);
          localRetry.waitWithBackoff();
          Boolean throwOrNot = localRetry.getAttempts() == localRetry.getTotal()
              ? doThrow : Boolean.FALSE;
          try (Response response = call.execute()) {
            statusCode = response.code();
            if (response.isSuccessful()) {
              completableFuture.complete(reader.read(response));
              // break the while
              break;

            } else {
              getLogger().httpError(request, response, throwOrNot);
            }
          } catch (IOException e) {
            getLogger().httpError(request, e, throwOrNot);
          }

          localRetry.retryAttempted();
        } while (localRetry.isRetry(statusCode));
      } catch (Exception e) {
        throw new FlagsmithRuntimeError();
      } finally {
        if (!completableFuture.isDone()) {
          if (doThrow) {
            completableFuture.obtrudeException(new FlagsmithApiError());
          } else {
            completableFuture.complete(null);
          }
        }
      }
    });

    return completableFuture;
  }

  /**
   * Reads a successful response into a value, with access to the response headers.
   *
   * @param <T> the result type
   */
  @FunctionalInterface
  public interface ResponseReader<T> {

    T read(Response response) throws IOException;
  }

  public void close() {
    this.executor.shutdown();
  }
}
