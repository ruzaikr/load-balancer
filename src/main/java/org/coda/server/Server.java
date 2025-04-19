package org.coda.server;

import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) throws IOException, InterruptedException {
        String host = "localhost";
        int port = 8080;
        URI baseUri = UriBuilder
                .fromUri("http://" + host)
                .port(port)
                .build();

        ResourceConfig config = new ResourceConfig()
                .packages("org.coda.resources")
                .register(org.glassfish.jersey.jackson.JacksonFeature.class)
                .property("jersey.config.server.wadl.disableWadl", true);

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config, false);

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        server.start();
        logger.info("Server started at " + baseUri);

        Thread.currentThread().join();
    }

}
