package org.coda.resources;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.coda.service.LoadBalancerService;

@Path("/{any:.*}")
public class RoundRobin {

  @Context
  UriInfo uriInfo;

  @Inject
  LoadBalancerService loadBalancerService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response proxy(JsonNode payload) {
    return loadBalancerService.proxy(payload, uriInfo);
  }

}
