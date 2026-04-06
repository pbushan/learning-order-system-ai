# order-api

Main Spring Boot teaching app.

## Main ideas demonstrated

- Customer CRUD
- Order CRUD
- MySQL persistence
- RabbitMQ publishing
- Local Lambda invocation through LocalStack
- Unit test
- Integration test

## Run in IntelliJ

Open this folder in IntelliJ and run:

`com.example.orderapi.OrderApiApplication`

Spring Boot should start Docker Compose services automatically.

## Important endpoint

`POST /api/orders/{id}/submit`

This endpoint is the heart of the demo.
