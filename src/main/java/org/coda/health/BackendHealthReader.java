package org.coda.health;

public interface BackendHealthReader {

  boolean isHealthy(String backend);

  void setToUnhealthy(String backend);
}
