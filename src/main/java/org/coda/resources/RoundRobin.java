package org.coda.resources;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.jackson.JacksonFeature;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/{any:.*}")
public class RoundRobin {

    private static final List<String> BACKENDS = List.of(
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083"
    );

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    @Context UriInfo uriInfo;

    private static final Client CLIENT = ClientBuilder.newBuilder()
            .register(JacksonFeature.class)
            .build();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxy(JsonNode payload) {
        int nextIndex = Math.abs(COUNTER.getAndIncrement() % BACKENDS.size());

        WebTarget target = CLIENT
                .target(BACKENDS.get(nextIndex))
                .path(uriInfo.getPath());

        Invocation invocation = target
                .request(MediaType.APPLICATION_JSON)
                .buildPost(Entity.json(payload));

        try (Response backendResp = invocation.invoke()) {

            JsonNode responseBody = backendResp.readEntity(JsonNode.class);

            return Response.status(backendResp.getStatus())
                    .entity(responseBody)
                    .build();
        }
    }



}
