package com.flagsmith.threads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithException;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.Data;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Data
public class RequestProcessor {

  private ExecutorService executor = Executors.newFixedThreadPool(3);
  private OkHttpClient client;
  private FlagsmithLogger logger;
  private Integer retries = 3;

  public RequestProcessor(OkHttpClient client, FlagsmithLogger logger) {
    this(client, logger, 3);
  }

  /**
   * Instantiate with client, logger and retries.
   * @param client client instance
   * @param logger logger instance
   * @param retries retries
   */
  public RequestProcessor(OkHttpClient client, FlagsmithLogger logger, Integer retries) {
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
   * Execute the response in async mode.
   * @param request Request object
   * @param clazz class type of response
   * @param doThrow should throw Exception
   * @param retries no of retries before failing
   * @param <T> Type inference for the response
   * @return
   */
  public <T> Future<T> executeAsync(
      Request request, TypeReference<T> clazz, Boolean doThrow, Integer retries) {
    CompletableFuture<T> completableFuture = new CompletableFuture<>();
    Call call = getClient().newCall(request);
    // run the execute method in a fixed thread with retries.
    executor.submit(() -> {
      Integer localRetries = retries;
      // retry until local retry reaches 0
      try {
        while (localRetries > 0) {
          Boolean throwOrNot = localRetries == 1 ? doThrow : Boolean.FALSE;
          try (Response response = call.execute()) {
            if (response.isSuccessful()) {
              ObjectMapper mapper = MapperFactory.getMappper();
              completableFuture.complete(mapper.readValue(response.body().string(), clazz));
              // break the while
              break;

            } else {
              getLogger().httpError(request, response, throwOrNot);
            }
          } catch (IOException e) {
            getLogger().httpError(request, e, throwOrNot);
          }

          localRetries--;
        }
      } catch (FlagsmithException e) {
        // ignore this exception
      } finally {
        if (retries == 1) {
          if (doThrow) {
            completableFuture.exceptionally((v) -> null);
          } else {
            completableFuture.complete(null);
          }
        }
      }
    });

    return completableFuture;
  }

}
