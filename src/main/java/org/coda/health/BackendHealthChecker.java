package org.coda.health;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BackendHealthChecker implements BackendHealthReader, Runnable {
  private final List<String> backends;
  private final Client client;
  private final ConcurrentMap<String, Boolean> healthyMap;

  public BackendHealthChecker(List<String> backends, Client client) {
    this.backends = backends;
    this.client = client;
    this.healthyMap = new ConcurrentHashMap<>();
    backends.forEach(backend -> healthyMap.put(backend, true));
  }

  @Override
  public void run() {
    for (String backend : backends) {
      boolean isHealthy = checkOneBackend(backend);
      healthyMap.put(backend, isHealthy);
    }
  }

  @Override
  public boolean isHealthy(String backend) {
    return healthyMap.getOrDefault(backend, true);
  }

  @Override
  public void setToUnhealthy(String backend) {
    healthyMap.put(backend, false);
  }

  private boolean checkOneBackend(String backend) {
    try (Response response = client.target(backend)
                                   .path("/health")
                                   .request()
                                   .get()) {
      return response.getStatus() == Status.OK.getStatusCode();
    } catch (Exception exception) {
      return false;
    }
  }
}
