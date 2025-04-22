package org.coda.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import org.coda.config.AppConfig;
import org.coda.health.BackendHealthReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadBalancerServiceTest {

  @Mock
  AppConfig appConfig;

  @Mock
  Client client;

  @Mock
  BackendHealthReader backendHealthReader;

  @Mock
  UriInfo uriInfo;

  @Mock
  WebTarget webTarget;

  @Mock
  Invocation.Builder invocationBuiler;

  LoadBalancerService service;

  @Mock
  JsonNode payload;

  @Mock
  Response successResponse;

  JsonNode successJsonNode;

  private final ObjectMapper mapper = new ObjectMapper();


  @BeforeEach
  void setUp() {
    when(client.target(any(URI.class))).thenReturn(webTarget);

    when(appConfig.getBackends()).thenReturn(List.of("http://a", "http://b", "http://c"));

    // backendHealthReader defaults to healthy
    when(backendHealthReader.isHealthy(anyString())).thenReturn(true);

    service = new LoadBalancerService(appConfig, client, backendHealthReader);

    when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(invocationBuiler);

    when(uriInfo.getPath()).thenReturn("/bounce");

    when(successResponse.getStatus()).thenReturn(Status.OK.getStatusCode());
    successJsonNode = mapper.createObjectNode().put("result", "ok");
    when(successResponse.readEntity(JsonNode.class)).thenReturn(successJsonNode);
  }

  @Test
  void successOnFirstBackend() {
    when(invocationBuiler.post(any(Entity.class))).thenReturn(successResponse);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(successJsonNode, response.getEntity());
    }
  }

  @Test
  void successWhenNextBackendIsUnhealthy() {
    when(invocationBuiler.post(any(Entity.class))).thenReturn(successResponse);

    when(backendHealthReader.isHealthy("http://a")).thenReturn(false);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(successJsonNode, response.getEntity());
    }
  }
}