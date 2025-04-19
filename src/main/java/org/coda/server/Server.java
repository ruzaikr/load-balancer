package org.coda.server;

import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());

    private static final String PORT_ENV_VAR_NAME = "PORT";
    public static final String HOST_ENV_VAR_NAME = "HOST";
    public static final String SCHEME_ENV_VAR_NAME = "SCHEME";

    private static Integer getPort() {
        String portStr = System.getenv(PORT_ENV_VAR_NAME);
        if (portStr == null) {
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return null;
        }
        return port;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Integer port = getPort();
        if (port == null) {
            logger.severe(String.format("Could not retrieve %s environment variable", PORT_ENV_VAR_NAME));
            return;
        }

        String host = System.getenv().getOrDefault(HOST_ENV_VAR_NAME, "localhost");
        String scheme = System.getenv().getOrDefault(SCHEME_ENV_VAR_NAME, "http");

        URI baseUri = UriBuilder
                .fromPath("/")
                .scheme(scheme)
                .host(host)
                .port(port)
                .build();

        ResourceConfig config = new ResourceConfig()
                .packages("org.coda.resources")
                .register(JacksonFeature.class)
                .property("jersey.config.server.wadl.disableWadl", true);

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config, false);

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        server.start();
        logger.info(String.format("Server started at %s", baseUri));

        Thread.currentThread().join();
    }

}
