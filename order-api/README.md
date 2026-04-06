# order-api

Main Spring Boot teaching app.

## Main ideas demonstrated

- Customer CRUD
- Order CRUD
- MySQL persistence
- RabbitMQ publishing
- Local Lambda invocation through LocalStack
- Browser UI served from a separate `order-ui` container
- Unit test
- Integration test

## Run in IntelliJ

Open this folder in IntelliJ and run:

`com.example.orderapi.OrderApiApplication`

Spring Boot should start Docker Compose services automatically, including the UI container.

## UI

The repository now includes a separate frontend folder:

`../order-ui`

When `order-api` starts from IntelliJ, Docker Compose will build and run that UI at:

`http://localhost:8081`

The UI proxies `/api/*` requests back to the Spring Boot app on `http://host.docker.internal:8080`, so it can call all customer and order endpoints without extra CORS setup.

## Important endpoint

`POST /api/orders/{id}/submit`

This endpoint is the heart of the demo.
