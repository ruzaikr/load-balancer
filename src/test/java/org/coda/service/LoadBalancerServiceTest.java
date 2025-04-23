package org.coda.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ProcessingException;
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
import org.coda.model.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
  Invocation.Builder invocationBuilder;

  LoadBalancerService service;

  @Mock
  JsonNode payload;

  @Captor
  ArgumentCaptor<URI> uriArgumentCaptor;

  @Mock
  Response successResponse;

  JsonNode successJsonNode;

  private final ObjectMapper mapper = new ObjectMapper();


  @BeforeEach
  void setUp() {
    when(appConfig.getBackends())
        .thenReturn(List.of("http://a", "http://b", "http://c"));

    when(client.target(uriArgumentCaptor.capture()))
        .thenReturn(webTarget);

    // backendHealthReader defaults to healthy
    when(backendHealthReader.isHealthy(anyString()))
        .thenReturn(true);

    service = new LoadBalancerService(appConfig, client, backendHealthReader);

    when(webTarget.request(MediaType.APPLICATION_JSON))
        .thenReturn(invocationBuilder);

    when(uriInfo.getPath())
        .thenReturn("/bounce");
  }

  private void stubSuccessResponse() {
    when(successResponse.getStatus())
        .thenReturn(Status.OK.getStatusCode());

    successJsonNode = mapper.createObjectNode().put("result", "ok");

    when(successResponse.readEntity(JsonNode.class))
        .thenReturn(successJsonNode);
  }

  @Test
  void successOnFirstBackend() {
    stubSuccessResponse();

    when(invocationBuilder.post(any(Entity.class)))
        .thenReturn(successResponse);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(successJsonNode, response.getEntity());
    }
  }

  @Test
  void successWhenNextBackendIsUnhealthy() {
    stubSuccessResponse();

    when(invocationBuilder.post(any(Entity.class)))
        .thenReturn(successResponse);

    when(backendHealthReader.isHealthy("http://a"))
        .thenReturn(false);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(successJsonNode, response.getEntity());
    }
  }

  @Test
  void retryOn5xxThenSuccess() {
    stubSuccessResponse();

    Response badGatewayResponse = mock(Response.class);
    when(badGatewayResponse.getStatus())
        .thenReturn(Status.BAD_GATEWAY.getStatusCode());
    when(invocationBuilder.post(any(Entity.class)))
        .thenReturn(badGatewayResponse)
        .thenReturn(successResponse);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(successJsonNode, response.getEntity());
    }
    verify(backendHealthReader).setToUnhealthy("http://a");
  }

  @Test
  void retryOnProcessingExceptionThenSuccess() {
    stubSuccessResponse();

    when(invocationBuilder.post(any(Entity.class)))
        .thenThrow(new ProcessingException("timeout"))
        .thenReturn(successResponse);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(successJsonNode, response.getEntity());
    }
    verify(backendHealthReader).setToUnhealthy("http://a");
  }

  @Test
  void returnOn4xxWithoutRetry() {
    Response notFoundResponse = mock(Response.class);
    JsonNode notFoundJsonNode = mapper.createObjectNode().put("error", "not found");
    when(notFoundResponse.getStatus())
        .thenReturn(Status.NOT_FOUND.getStatusCode());
    when(notFoundResponse.readEntity(JsonNode.class))
        .thenReturn(notFoundJsonNode);
    when(invocationBuilder.post(any(Entity.class)))
        .thenReturn(notFoundResponse);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
      assertEquals(notFoundJsonNode, response.getEntity());
    }
    verify(backendHealthReader, never()).setToUnhealthy(anyString());
  }

  @Test
  void allBackendsFailReturnServiceUnavailable() {
    Response badGatewayResponse = mock(Response.class);
    when(badGatewayResponse.getStatus())
        .thenReturn(Status.BAD_GATEWAY.getStatusCode());
    Response notImplementedResponse = mock(Response.class);
    when(notImplementedResponse.getStatus())
        .thenReturn(Status.NOT_IMPLEMENTED.getStatusCode());

    when(invocationBuilder.post(any(Entity.class)))
        .thenReturn(badGatewayResponse)
        .thenThrow(new ProcessingException("timeout"))
        .thenReturn(notImplementedResponse);

    try (Response response = service.proxy(payload, uriInfo)) {
      assertEquals(Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
      Object responseEntity = response.getEntity();
      assertInstanceOf(ErrorResponse.class, responseEntity);
      ErrorResponse errorResponse = (ErrorResponse) responseEntity;
      assertEquals(Status.SERVICE_UNAVAILABLE.getStatusCode(), errorResponse.getStatus());
      assertEquals("ServiceUnavailable", errorResponse.getError());
      assertTrue(errorResponse.getDetails().contains("Please try again"));
    }

  }

  @Test
  void roundRobinWrapsAround() {
    stubSuccessResponse();
    when(invocationBuilder.post(any(Entity.class))).thenReturn(successResponse);

    // call four times
    try (Response _ = service.proxy(payload, uriInfo);
        Response _ = service.proxy(payload, uriInfo);
        Response _ = service.proxy(payload, uriInfo);
        Response _ = service.proxy(payload, uriInfo)) {

      List<URI> allUriArgumentValues = uriArgumentCaptor.getAllValues();
      assertEquals(4, allUriArgumentValues.size());

      URI first = allUriArgumentValues.get(0);
      assertEquals(URI.create("http://a").resolve("/bounce"), first);

      URI second = allUriArgumentValues.get(1);
      assertEquals(URI.create("http://b").resolve("/bounce"), second);

      URI third = allUriArgumentValues.get(2);
      assertEquals(URI.create("http://c").resolve("/bounce"), third);

      URI fourth = allUriArgumentValues.get(3);
      assertEquals(URI.create("http://a").resolve("/bounce"), fourth);
    }
  }
}