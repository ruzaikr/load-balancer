# load-balancer

A simple JSON-proxy REST service with round-robin load balancing and health checks.

## Features

- Round-robin forwarding of JSON `POST` requests to multiple backends  
- Periodic health checks to mark backends up/down  
- Retries on 5xx and connection failures  
- Transparent pass-through of 2xx and 4xx responses

## Prerequisites

- Java 22  
- Maven 3.6+  

## Configuration

Edit `src/main/resources/application.properties`:

```properties
server.scheme=http
server.host=localhost
server.port=8080

client.connectTimeoutMs=5000
client.readTimeoutMs=5000

schedule.initialDelay=5    # seconds before first health check
schedule.period=10         # seconds between health checks

loadbalancer.backends=http://localhost:8081,http://localhost:8082,http://localhost:8083
```

## Running the Server in IntelliJ IDEA

Run `main` in `src/main/java/org/coda/server/ServerApp.java`.

Log output will confirm startup and health-checker scheduling.

## Testing

Key test classes:

- `BackendHealthCheckerTest` – simulates healthy/unhealthy backends
- `LoadBalancerServiceTest` – verifies proxy logic, retries, round-robin wrap