package com.flagsmith.threads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RequestProcessor {

  private ExecutorService executor = Executors.newFixedThreadPool(3);
  private OkHttpClient client;
  private FlagsmithLogger logger;
  private Retry retries = new Retry(3);

  public RequestProcessor(OkHttpClient client, FlagsmithLogger logger) {
    this(client, logger, new Retry(3));
  }

  /**
   * Instantiate with client, logger and retries.
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
   * @param request request to invoke
   * @param clazz class type of response
   * @param doThrow should throw Exception (boolean)
   * @param <T> Type inference for the response
   * @return
   */
  public <T> Future<T> executeAsync(Request request, TypeReference<T> clazz, Boolean doThrow) {
    return executeAsync(request, clazz, doThrow, retries);
  }

  /**
   * Execute the response in async mode and do not unmarshall.
   * @param request request to invoke
   * @param doThrow whether to throw exception or not
   * @return
   */
  public Future<JsonNode> executeAsync(Request request, Boolean doThrow) {
    return executeAsync(request, new TypeReference<JsonNode>() {}, doThrow, retries);
  }

  /**
   * Execute the response in async mode.
   * @param request Request object
   * @param clazz class type of response
   * @param doThrow should throw Exception
   * @param retries no of retries before failing
   * @param <T> Type inference for the response
   * @return
   */
  public <T> Future<T> executeAsync(
      Request request, TypeReference<T> clazz, Boolean doThrow, Retry retries) {
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
              ObjectMapper mapper = MapperFactory.getMapper();
              completableFuture.complete(mapper.readValue(response.body().string(), clazz));
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

  public void close() {
    this.executor.shutdown();
  }

  public FlagsmithLogger getLogger() {
    return logger;
  }

  public OkHttpClient getClient() {
    return client;
  }
}
