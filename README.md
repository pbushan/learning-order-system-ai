# 🚀 AI-Driven Order Processing System (Spring Boot + RabbitMQ + Lambda)

This project is a hands-on sandbox to demonstrate:

- Building distributed systems with Spring Boot
- Event-driven architecture using RabbitMQ
- Serverless integration with AWS Lambda (LocalStack)
- Full Docker-based local environment
- **AI-assisted development workflows**
- **Agentic system design for autonomous task execution**

---

## 🎯 Why this project exists

This is not just a demo project.

It is a **sandbox for exploring how AI can:**

- Improve developer productivity
- Automate repetitive engineering tasks
- Introduce agent-driven workflows into real systems
- Reduce manual intervention in distributed systems

---

## 🧠 Key Concepts Demonstrated

- REST APIs (CRUD)
- Async messaging (RabbitMQ)
- Background processing (consumer app)
- Serverless decision-making (Lambda)
- Integration testing vs unit testing
- AI-assisted code evolution (coming soon)
- Agentic workflows (planned)

🧪 Interactive API Demo (Postman)

👉 Open directly in Postman:
https://web.postman.co/workspace/95c58454-8932-4dab-87a6-057495cd11e5/collection/856455-a7491325-6ef0-4058-a06d-fcb1353da7c8?action=share&source=copy-link&creator=856455

▶️ Step-by-step execution flow

Run the requests in this exact order:

Step 1 - Customer Setup
Step 2 - Order Creation
Step 3 - Submit Order (Core Flow --> Triggers Lambda + RabbitMQ)
Step 4 - Verify System Behavior
🔥 What happens when you submit an order

When you run:

POST Submit Order

The system:

Calls a Lambda function (shipping decision)
Publishes a message to RabbitMQ
A consumer service processes the message asynchronously
Writes a fulfillment record into MySQL
👀 What to observe during the demo

While running the collection:

Check RabbitMQ UI → message appears and gets consumed
Check MySQL tables → data persists and updates
Check consumer logs → background processing
REST API → Lambda → RabbitMQ → Consumer → MySQL


# Learning Order System (Spring Boot + MySQL + RabbitMQ + Lambda + Docker)

This project is designed to help a beginner understand, in a very practical way:

1. REST APIs using **GET, POST, PUT, DELETE**
2. Saving data into **MySQL**
3. Publishing a message into **RabbitMQ**
4. Running a **separate consumer app** that reads the message and writes into MySQL
5. Calling a **Lambda function** from the API
6. Understanding the difference between a **unit test** and an **automated integration test**
7. Running the full learning environment with **Docker containers**

---

## Business scenario: "Online Gift Store"

Imagine a small online gift shop.

### What the system does

- The store keeps a list of **customers**
- The store lets you create and manage **orders**
- When an order is **submitted**:
  - the API calls a **Lambda function** to decide the shipping type and estimated delivery days
  - the API publishes an **Order Submitted** message into **RabbitMQ**
  - a **separate consumer application** reads that message
  - the consumer writes a **fulfillment record** into **MySQL**

This gives you a simple but realistic business flow that shows:

- CRUD APIs
- database persistence
- asynchronous messaging
- background processing
- serverless function invocation
- testing basics

---

## Very simple story version

Think of it like this:

1. A customer exists in the system
2. Someone creates an order for that customer
3. The order is still just a draft
4. When the order is submitted:
   - the system asks a Lambda function:  
     **"How should this order be shipped?"**
   - the system sends a message saying:  
     **"A new order is ready for processing"**
5. A separate background worker receives that message
6. That worker writes a fulfillment record into the database

---

## Plain text architecture

```text
                    +------------------------------------+
                    |        IntelliJ / Your Laptop      |
                    |                                    |
                    |   Run this app locally:            |
                    |   order-api (Spring Boot)          |
                    +----------------+-------------------+
                                     |
                                     | REST API calls
                                     v
                        +------------+------------+
                        |        order-api        |
                        |   Spring Boot app       |
                        |                         |
                        | - Customers CRUD        |
                        | - Orders CRUD           |
                        | - Submit Order          |
                        +-----+-----------+-------+
                              |           |
                              |           |
                              |           +------------------------------+
                              |                                          |
                              v                                          v
                    +---------+---------+                    +-----------+-----------+
                    |      MySQL        |                    |       LocalStack       |
                    |   relational DB   |                    |   local AWS emulator   |
                    |                   |                    |                        |
                    | customers         |                    | Lambda: shipping-rule  |
                    | orders            |                    |                        |
                    | fulfillment_*     |                    +-----------+------------+
                    +-------------------+                                |
                                                                           |
                                                                           | Lambda response
                                                                           |
                              publish message                              |
                              to queue                                     |
                              +--------------------------------------------+
                              |
                              v
                    +---------+---------+
                    |      RabbitMQ     |
                    |   message broker  |
                    |  queue: order...  |
                    +---------+---------+
                              |
                              | message consumed
                              v
                    +---------+---------+
                    |   order-consumer  |
                    |   Spring Boot     |
                    |                   |
                    | reads queue       |
                    | writes fulfillment|
                    +---------+---------+
                              |
                              v
                    +---------+---------+
                    |       MySQL       |
                    +-------------------+
```

---

## Project layout

This zip contains **three separate repos/projects** plus a root guide:

```text
learning-order-system/
├── README.md
├── order-api/               <-- Main Spring Boot API app (open this in IntelliJ)
├── order-consumer/          <-- Separate Spring Boot consumer app
└── order-pricing-lambda/    <-- Separate Lambda repo
```

That matches your assumption that the **consumer app** and **Lambda function** are separate repos.

---

## Which project do you open in IntelliJ?

Open:

```text
learning-order-system/order-api
```

That is the main teaching app.

When you run it in IntelliJ, Spring Boot will use the included **docker-compose.yml** to start:

- MySQL
- RabbitMQ
- LocalStack (for Lambda)
- order-consumer

So your wife can focus on the concepts instead of manually starting infrastructure.

---

## What each repo does

### 1) order-api
Main teaching application.

It shows:

- CRUD for customers
- CRUD for orders
- submit order flow
- RabbitMQ publish
- Lambda invocation
- JPA + MySQL
- unit test
- automated integration test

### 2) order-consumer
Separate background application.

It:

- listens to RabbitMQ
- reads order submission messages
- writes fulfillment records into MySQL

### 3) order-pricing-lambda
Tiny Lambda function.

It decides shipping type based on order total:

- low amount -> STANDARD
- medium amount -> PRIORITY
- high amount -> EXPRESS

---

## API flow in beginner-friendly words

### Create customer
You create a customer.

Example:
- Priya Sharma
- priya@example.com

### Create order
You create an order for Priya.

Example:
- Teddy Bear
- quantity 2
- total amount 75.00

### Submit order
When you submit the order:

1. API loads the order from MySQL
2. API calls Lambda
3. Lambda returns shipping recommendation
4. API saves updated order info
5. API publishes a RabbitMQ message
6. Consumer app receives the message
7. Consumer app writes a fulfillment record into MySQL

---

## Tables in MySQL

### customers
Stores customer information

### orders
Stores order details

### fulfillment_records
Stores records written by the consumer after message processing

---

## REST API endpoints

Base URL when running locally:

```text
http://localhost:8080
```

### Customer endpoints

#### Create customer
```http
POST /api/customers
```

Body:
```json
{
  "name": "Priya Sharma",
  "email": "priya@example.com"
}
```

#### Get all customers
```http
GET /api/customers
```

#### Get one customer
```http
GET /api/customers/{id}
```

#### Update customer
```http
PUT /api/customers/{id}
```

#### Delete customer
```http
DELETE /api/customers/{id}
```

---

### Order endpoints

#### Create order
```http
POST /api/orders
```

Body:
```json
{
  "customerId": 1,
  "productName": "Teddy Bear",
  "quantity": 2,
  "totalAmount": 75.00
}
```

#### Get all orders
```http
GET /api/orders
```

#### Get one order
```http
GET /api/orders/{id}
```

#### Update order
```http
PUT /api/orders/{id}
```

#### Delete order
```http
DELETE /api/orders/{id}
```

#### Submit order
```http
POST /api/orders/{id}/submit
```

This is the most important teaching endpoint.

It demonstrates:

- database read
- Lambda invocation
- database update
- RabbitMQ publish

---

## Example learning walkthrough

Use this exact sequence.

### Step 1: Create a customer

```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Priya Sharma",
    "email":"priya@example.com"
  }'
```

### Step 2: Create an order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId":1,
    "productName":"Teddy Bear",
    "quantity":2,
    "totalAmount":75.00
  }'
```

### Step 3: Submit the order

```bash
curl -X POST http://localhost:8080/api/orders/1/submit
```

### Step 4: Read the order back

```bash
curl http://localhost:8080/api/orders/1
```

You will see fields like:

- status = SUBMITTED
- shippingType = PRIORITY
- estimatedDeliveryDays = 2

### Step 5: Explain what happened in plain English

When you hit submit:

- the API asked Lambda for shipping advice
- the API saved the updated order
- the API sent a message to RabbitMQ
- the consumer received the message
- the consumer stored a fulfillment record in MySQL

---

## RabbitMQ UI

RabbitMQ management UI:

```text
http://localhost:15672
```

Login:

```text
username: guest
password: guest
```

Use this to explain queues visually.

---

## MySQL access

MySQL runs in Docker on:

```text
localhost:3306
```

Credentials:

```text
database: learning_orders
username: appuser
password: apppass
```

You can inspect data using:

- MySQL Workbench
- DBeaver
- TablePlus
- DataGrip
- IntelliJ database tool window if available

---

## Lambda explanation for a beginner

A Lambda function is just a small piece of code that runs for one job.

In this project the Lambda's job is:

> "Based on order amount, tell us the shipping type and delivery time."

This makes it easy to explain serverless ideas:

- small focused logic
- remote function call
- independent deployment
- no full traditional server app needed for that tiny rule

For local learning, Lambda is emulated using **LocalStack**.

---

## Why RabbitMQ is useful

RabbitMQ helps when you do not want everything to happen in one single step.

Without RabbitMQ:
- the API would do all the work itself

With RabbitMQ:
- the API says "here is a new order"
- another app processes it in the background

This teaches:
- asynchronous processing
- decoupling
- event-driven architecture

---

## Unit test vs automated test

### Unit test
A **unit test** checks one small piece of logic in isolation.

Example in this project:
- `OrderSubmissionServiceTest`

That test mocks dependencies and checks:
- was Lambda invoked?
- was RabbitMQ publisher called?
- was the order status changed?

This is fast and focused.

### Automated integration test
An **automated integration test** checks that bigger parts of the system work together automatically.

Example in this project:
- `CustomerControllerIntegrationTest`

That test starts the Spring Boot application context and uses `MockMvc` to call the real REST API endpoint.

It checks:
- controller
- service
- repository
- JSON request/response behavior

This is still automated, but broader than a unit test.

### Easy beginner summary

```text
Unit test:
"Does this one class behave correctly?"

Automated integration test:
"Do multiple parts of the application work together correctly?"
```

---

## System requirements

## Required on both Windows and macOS

1. **Java 17**
2. **IntelliJ IDEA Community Edition**
3. **Docker Desktop**
4. **Docker Compose**
   - included with modern Docker Desktop
5. Internet access for the first build so Maven dependencies and Docker images can download

### Recommended
- 16 GB RAM minimum
- 20 GB free disk space
- modern CPU
- keep Docker Desktop running before you launch the app

---

## Ports used

Make sure these are free:

- `8080` -> order-api
- `3306` -> MySQL
- `5672` -> RabbitMQ AMQP
- `15672` -> RabbitMQ management UI
- `4566` -> LocalStack / Lambda
- `8081` -> order-consumer (inside Docker, optional if exposed later)

---

## How the Docker startup works

The `order-api` project contains a `docker-compose.yml`.

Spring Boot is configured with Docker Compose support, so when you start the `order-api` application from IntelliJ, it will automatically start the required containers.

That means these services come up for you:

- mysql
- rabbitmq
- localstack
- order-consumer

---

## macOS setup

### 1) Install Java 17
Common options:
- Amazon Corretto 17
- Eclipse Temurin 17

### 2) Install IntelliJ Community
Install and open it once.

### 3) Install Docker Desktop
Make sure Docker Desktop is running.

### 4) Open the main project
Open:

```text
learning-order-system/order-api
```

### 5) Let IntelliJ import Maven dependencies
Wait until indexing and Maven import finish.

### 6) Run the app
Open this class:

```text
com.example.orderapi.OrderApiApplication
```

Click **Run**.

### 7) What should happen
Spring Boot starts the local app and also starts Docker containers using `docker-compose.yml`.

### 8) Verify it works
Open:

- API: `http://localhost:8080/api/customers`
- RabbitMQ UI: `http://localhost:15672`

---

## Windows setup

### 1) Install Java 17
Install Temurin 17 or Corretto 17.

### 2) Install IntelliJ Community Edition

### 3) Install Docker Desktop
During installation, enable the recommended settings.

### 4) Start Docker Desktop
Wait until Docker says it is running.

### 5) Open the project in IntelliJ
Open:

```text
learning-order-system\order-api
```

### 6) Let IntelliJ finish Maven import

### 7) Run the Spring Boot main class
Run:

```text
com.example.orderapi.OrderApiApplication
```

### 8) Verify the app is up
Test in browser or Postman:

```text
http://localhost:8080/api/customers
```

RabbitMQ UI:

```text
http://localhost:15672
```

---

## If automatic container startup does not happen

Run manually from a terminal in `order-api`:

### macOS / Linux
```bash
docker compose up --build
```

### Windows PowerShell
```powershell
docker compose up --build
```

Then run `OrderApiApplication` from IntelliJ again.

---

## How to explain the architecture to a beginner

Here is a clean way to teach it.

### Part 1: REST API + MySQL
"Users send HTTP requests.  
The Spring Boot API receives them.  
The API stores and reads data from MySQL."

### Part 2: RabbitMQ
"When an order is submitted, instead of doing everything immediately, the API sends a message to RabbitMQ."

### Part 3: Consumer app
"A separate app listens for those messages and does background work."

### Part 4: Lambda
"The API asks a small function for a shipping recommendation."

### Part 5: Testing
"One test checks a single class.  
Another test checks multiple layers working together."

---

## Suggested teaching order

1. Start with **Customer CRUD**
2. Then explain **Order CRUD**
3. Then explain what happens during **Submit Order**
4. Show **RabbitMQ UI**
5. Show **MySQL tables**
6. Show the **consumer app logs**
7. Show the **Lambda code**
8. Show one **unit test**
9. Show one **integration test**

---

## Notes

- The consumer app and Lambda are intentionally split into separate folders to mimic separate repos.
- LocalStack is used so you do not need real AWS just to learn Lambda interaction.
- This is a teaching/demo system, so the design stays intentionally simple and readable.

---

## Quick start summary

1. Install Java 17
2. Install Docker Desktop
3. Open `order-api` in IntelliJ
4. Run `OrderApiApplication`
5. Use Postman or curl to create a customer
6. Create an order
7. Submit the order
8. Show the queue, Lambda, and fulfillment record flow

---
