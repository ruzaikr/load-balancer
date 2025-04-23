package org.coda.server;

import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.coda.client.ClientFactory;
import org.coda.config.AppConfig;
import org.coda.exception.GenericExceptionMapper;
import org.coda.health.BackendHealthChecker;
import org.coda.health.BackendHealthManager;
import org.coda.service.LoadBalancerService;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class ServerApp {

  private final AppConfig appConfig;
  private final Logger logger = Logger.getLogger(getClass().getName());

  public ServerApp(AppConfig appConfig) {
    this.appConfig = appConfig;
  }

  private ResourceConfig buildResourceConfig() {
    LoggingFeature loggingFeature = new LoggingFeature(
        Logger.getLogger(LoggingFeature.class.getName()),
        Level.INFO,
        LoggingFeature.Verbosity.HEADERS_ONLY,
        Integer.MAX_VALUE
    );

    Client client = ClientFactory.create(appConfig);

    BackendHealthChecker backendHealthChecker =
        new BackendHealthChecker(appConfig.getBackends(), client);
    ScheduledExecutorService scheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable, "health-checker");
          thread.setDaemon(true);
          return thread;
        });
    scheduledExecutorService.scheduleAtFixedRate(backendHealthChecker,
                                                 appConfig.getScheduleInitialDelay(),
                                                 appConfig.getSchedulePeriod(),
                                                 TimeUnit.SECONDS);

    return new ResourceConfig()
        .packages("org.coda.resources")
        .register(GenericExceptionMapper.class)
        .register(JacksonFeature.class)
        .register(loggingFeature)
        .register(new AbstractBinder() {
          @Override
          protected void configure() {
            bind(appConfig).to(AppConfig.class);
            bind(client).to(Client.class);
            bind(backendHealthChecker).to(BackendHealthManager.class);
            bindAsContract(LoadBalancerService.class).in(Singleton.class);
          }
        })
        .property("jersey.config.server.wadl.disableWadl", true);
  }

  private void start() throws IOException, InterruptedException {
    var resourceConfig = buildResourceConfig();
    var uri = appConfig.baseUriBuilder().build();
    HttpServer server = GrizzlyHttpServerFactory.createHttpServer(uri, resourceConfig, false);

    Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
    server.start();
    logger.info(String.format("Server started at %s", uri));
    Thread.currentThread().join();
  }

  public static void main(String[] args) throws Exception {
    var appConfig = AppConfig.load();
    new ServerApp(appConfig).start();
  }

}
