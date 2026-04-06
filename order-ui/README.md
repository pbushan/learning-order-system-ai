# order-ui

Separate browser UI for the learning order system.

## What it covers

- Customer create, list, update, delete
- Order create, list, update, delete
- Order submission flow
- Shipping decision visibility after submit

## How it runs

The UI is deployed by the Docker Compose file in [../order-api/docker-compose.yml](/Users/phanibushan/Downloads/IntellijProjects/learning-order-system-ai/order-api/docker-compose.yml).

When `order-api` starts from IntelliJ, Spring Boot starts Compose, Compose builds this UI image, and nginx serves the app on:

`http://localhost:8081`

All browser API calls go to `/api/*`, and nginx proxies them to the locally running Spring Boot app on port `8080`.

For branch-level testing without IntelliJ, the root [docker-compose.yml](/Users/phanibushan/Downloads/IntellijProjects/learning-order-system-ai/docker-compose.yml) runs both `order-api` and `order-ui` as containers and keeps the same public URL.
