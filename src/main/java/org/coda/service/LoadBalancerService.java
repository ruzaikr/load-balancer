package org.coda.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.coda.config.AppConfig;
import org.coda.health.BackendHealthReader;
import org.coda.model.ErrorResponse;

public class LoadBalancerService {

  private final List<String> backends;
  private final Client client;
  private final BackendHealthReader backendHealthReader;

  private final AtomicInteger counter = new AtomicInteger();
  private final Logger logger = Logger.getLogger(getClass().getName());

  @Inject
  public LoadBalancerService(AppConfig appConfig,
                             Client client,
                             BackendHealthReader backendHealthReader) {
    this.backends = appConfig.getBackends();
    this.client = client;
    this.backendHealthReader = backendHealthReader;
  }

  public Response proxy(JsonNode payload, UriInfo uriInfo) {
    int maxAttempts = backends.size();
    for (int attemptNum = 1; attemptNum <= maxAttempts; attemptNum++) {
      int nextRoundRobinIndex = getNextRoundRobinIndexAndIncrementCounter();
      String backend = backends.get(nextRoundRobinIndex);

      if (!backendHealthReader.isHealthy(backend)) {
        logger.log(Level.WARNING,
            "Attempt {0}/{1}: Backend {2} unavailable.",
            new Object[]{attemptNum, maxAttempts, backend});
        continue;
      }

      try (Response backendResponse = callBackend(backend, uriInfo.getPath(), payload)) {

        int backendResponseStatus = backendResponse.getStatus();
        if (isStatus5xx(backendResponseStatus)) {
          backendHealthReader.setToUnhealthy(backend);
          logger.log(Level.WARNING,
              "Attempt {0}/{1}: Backend {2} unavailable. Got: {3}",
              new Object[]{attemptNum, maxAttempts, backend, backendResponseStatus});
          continue;
        }

        return Response
            .status(backendResponseStatus)
            .entity(backendResponse.readEntity(JsonNode.class))
            .build();
      } catch (ProcessingException processingException) {
        backendHealthReader.setToUnhealthy(backend);
        logger.log(Level.WARNING,
            "Attempt {0}/{1}: Backend {2} unavailable. Reason: {3}",
            new Object[]{attemptNum, maxAttempts, backend, processingException.getMessage()});
      }
    }

    ErrorResponse errorResponse = new ErrorResponse(
        Status.SERVICE_UNAVAILABLE.getStatusCode(),
        "ServiceUnavailable",
        "Service unavailable. Please try again later."
    );

    return Response
        .status(Response.Status.SERVICE_UNAVAILABLE)
        .entity(errorResponse)
        .build();
  }

  private int getNextRoundRobinIndexAndIncrementCounter() {
    return counter.getAndUpdate(current ->
        current >= backends.size() - 1
            ? 0
            : current + 1
    );
  }

  private Response callBackend(String backend,
                               String path,
                               JsonNode payload) throws ProcessingException {
    URI uri = URI.create(backend)
                 .resolve(path);
    return client.target(uri)
                 .request(MediaType.APPLICATION_JSON)
                 .post(Entity.json(payload));
  }

  private boolean isStatus5xx(int statusCode) {
    return statusCode >= 500 && statusCode < 600;
  }
}
