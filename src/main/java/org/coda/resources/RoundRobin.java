package org.coda.resources;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/{any:.*}")
public class RoundRobin {

    private static final List<String> BACKENDS = List.of(
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083"
    );

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MS = 2_000; // 2 seconds

    @Context UriInfo uriInfo;

    private static final Client CLIENT = ClientBuilder.newBuilder()
            .register(JacksonFeature.class)
            .property(ClientProperties.CONNECT_TIMEOUT, TIMEOUT_MS)
            .property(ClientProperties.READ_TIMEOUT, TIMEOUT_MS)
            .register(new LoggingFeature(
                    Logger.getLogger(LoggingFeature.class.getName()),
                    Level.INFO,
                    LoggingFeature.Verbosity.HEADERS_ONLY,
                    Integer.MAX_VALUE
            ))
            .build();

    private static final Logger logger = Logger.getLogger(RoundRobin.class.getName());

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxy(JsonNode payload) {

        for (int attemptNum = 1; attemptNum <= MAX_RETRIES; attemptNum++) {
            int nextRRIndex = Math.abs(COUNTER.getAndIncrement() % BACKENDS.size());

            WebTarget target = CLIENT
                    .target(BACKENDS.get(nextRRIndex))
                    .path(uriInfo.getPath());

            Invocation invocation = target
                    .request(MediaType.APPLICATION_JSON)
                    .buildPost(Entity.json(payload));

            try (Response backendResp = invocation.invoke()) {
                int status = backendResp.getStatus();
                if (status >= 500 && status < 600) { // @todo: Do I need to use constants here or is it okay to keep it raw 500 600?
                    continue;
                }

                JsonNode responseBody = backendResp.readEntity(JsonNode.class);
                return Response
                        .status(backendResp.getStatus())
                        .entity(responseBody)
                        .build();
            } catch (ProcessingException exception) {
                logger.log(Level.WARNING,
                        "Attempt {0}/{1}: Backend {2} unavailable. Reason: {3}",
                        new Object[]{attemptNum, MAX_RETRIES, target.getUri(), exception.getMessage()});
            }
        }

        return Response
                .status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("{\"error\":\"Service unavailable.\"}")
                .build();

    }



}
