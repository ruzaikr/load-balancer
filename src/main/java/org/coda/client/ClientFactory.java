package org.coda.client;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.coda.config.AppConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;

public class ClientFactory {

  public static Client create(AppConfig appConfig) {
    return ClientBuilder.newBuilder()
        .register(JacksonFeature.class)
        .property(ClientProperties.CONNECT_TIMEOUT, appConfig.getConnectTimeoutMs())
        .property(ClientProperties.READ_TIMEOUT, appConfig.getReadTimeoutMs())
        .register(new LoggingFeature(
            Logger.getLogger(LoggingFeature.class.getName()),
            Level.INFO,
            LoggingFeature.Verbosity.HEADERS_ONLY,
            Integer.MAX_VALUE
        ))
        .build();
  }

}
