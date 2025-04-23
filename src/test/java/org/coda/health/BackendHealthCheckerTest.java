package org.coda.health;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackendHealthCheckerTest {

  @Mock
  BackendHealthChecker backendHealthChecker;

  @Mock
  Client client;

  @Mock
  WebTarget webTarget;

  @Mock
  Invocation.Builder invocationBuilder;

  @Mock
  Response successResponse;

  @Mock
  Response errorResponse;

  @BeforeEach
  void setUp() {
    backendHealthChecker = new BackendHealthChecker(List.of("http://a"), client);
  }

  private void commonStub() {
    when(client.target("http://a")).thenReturn(webTarget);
    when(webTarget.path("/health")).thenReturn(webTarget);
    when(webTarget.request()).thenReturn(invocationBuilder);
  }

  @Test
  void isHealthy() {
    assertTrue(backendHealthChecker.isHealthy("http://a"));
  }

  @Test
  void runMarksHealthyWhen200() {
    commonStub();

    when(invocationBuilder.get()).thenReturn(successResponse);
    when(successResponse.getStatus()).thenReturn(Status.OK.getStatusCode());

    backendHealthChecker.run();
    assertTrue(backendHealthChecker.isHealthy("http://a"));
  }

  @Test
  void runMarksUnhealthyOnNon200() {
    commonStub();

    when(invocationBuilder.get()).thenReturn(errorResponse);
    when(errorResponse.getStatus()).thenReturn(500);

    backendHealthChecker.run();
    assertFalse(backendHealthChecker.isHealthy("http://a"));
  }

  @Test
  void isHealthyDefaultsTrueWhenMissingKey() {
    assertTrue(backendHealthChecker.isHealthy("unknown"));
  }
}