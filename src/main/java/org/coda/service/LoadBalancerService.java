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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.coda.client.ClientFactory;
import org.coda.config.AppConfig;

public class LoadBalancerService {

  private final List<String> backends;
  private final ConcurrentMap<String, Boolean> healthyMap;
  private final Client client;
  private final int maxRetries;
  private final AtomicInteger counter = new AtomicInteger();
  private final Logger logger = Logger.getLogger(getClass().getName());

  @Inject
  public LoadBalancerService(AppConfig appConfig) {
    this.backends = appConfig.getBackends();
    this.client = ClientFactory.create(appConfig);
    this.maxRetries = appConfig.getMaxRetries();

    this.healthyMap = new ConcurrentHashMap<>();
    backends.forEach(backend -> healthyMap.put(backend, true));

    Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "health-checker");
      thread.setDaemon(true);
      return thread;
    }).scheduleAtFixedRate(this::updateHealthStatus, 0, 10, TimeUnit.SECONDS);
  }

  private void updateHealthStatus() {
    for (String backend : backends) {
      boolean isHealthy;
      try (Response response = client
          .target(backend)
          .path("/health")
          .request()
          .get()) {
        isHealthy = (response.getStatus() == Status.OK.getStatusCode());
      } catch (Exception exception) {
        isHealthy = false;
      }
      healthyMap.put(backend, isHealthy);
    }
  }

  public Response proxy(JsonNode payload, UriInfo uriInfo) {
    for (int attemptNum = 1; attemptNum <= maxRetries; attemptNum++) {
      int nextRoundRobinIndex = getNextRoundRobinIndexAndIncrementCounter();
      String backend = backends.get(nextRoundRobinIndex);

      if (!healthyMap.get(backend)) {
        continue;
      }

      URI targetUri = URI.create(backends.get(nextRoundRobinIndex))
          .resolve(uriInfo.getPath());

      try (Response backendResponse = client.target(targetUri)
                                            .request(MediaType.APPLICATION_JSON)
                                            .post(Entity.json(payload))) {

        int backendResponseStatus = backendResponse.getStatus();
        // If received 5xx, try next backend instance
        if (backendResponseStatus >= 500 && backendResponseStatus < 600) {
          continue;
        }

        JsonNode backendResponseBody = backendResponse.readEntity(JsonNode.class);
        return Response
            .status(backendResponseStatus)
            .entity(backendResponseBody)
            .build();
      } catch (ProcessingException processingException) {
        healthyMap.put(backend, false);
        logger.log(Level.WARNING,
            "Attempt {0}/{1}: Backend {2} unavailable. Reason: {3}",
            new Object[]{attemptNum, maxRetries, targetUri, processingException.getMessage()});
      }
    }

    return Response
        .status(Response.Status.SERVICE_UNAVAILABLE)
        .entity(
            "{\"error\":\"Service unavailable.\"}") // @todo: Is it okay to build the response body like this?
        .build();
  }

  private int getNextRoundRobinIndexAndIncrementCounter() {
    return counter.getAndUpdate(current ->
        current >= backends.size() - 1
            ? 0
            : current + 1
    );
  }
}
