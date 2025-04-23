package org.coda.health;

public interface BackendHealthManager {

  boolean isHealthy(String backend);

  void setToUnhealthy(String backend);
}
