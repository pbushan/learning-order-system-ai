# 🚀 AI-Driven Order Processing System (Spring Boot + RabbitMQ + Lambda)

This project is a hands-on sandbox to demonstrate:

- Building distributed systems with Spring Boot
- Event-driven architecture using RabbitMQ
- Serverless integration with AWS Lambda (LocalStack)
- Full Docker-based local environment
- **AI-assisted development workflows**
- **Agentic system design for autonomous task execution**
- A reviewable example of human-guided, agent-executed product delivery
- GitHub App based formal PR review automation
- LLM-backed pull request review experimentation

---

## 🎯 Why this project exists

This is not just a demo project.

It is a **sandbox for exploring how AI can:**

- Improve developer productivity
- Automate repetitive engineering tasks
- Introduce agent-driven workflows into real systems
- Reduce manual intervention in distributed systems

---

## 📌 Current Status (Steps 1–6)

This repo now includes an end-to-end portfolio workflow from intake to GitHub issue creation, plus repo-side scaffolding for approved issue pickup and PR-review handling.

### Step 1: Intake chat (backend + UI)

- Intake chat endpoint: `POST /api/intake/chat`
- LLM classifies requests as `bug` or `feature` and returns structured intake data.
- Intake requests/responses are logged as append-only JSONL audit entries.

### Step 2: Decomposition

- Decomposition endpoint: `POST /api/intake/decompose`
- Splits intake into PR-safe stories (small, explicit slices).
- Decomposition lifecycle is audit-logged.

### Step 3: GitHub issue creation

- Direct issue endpoint: `POST /api/github/issues/create-from-decomposition`
- Orchestrated endpoint used by UI: `POST /api/intake/complete-to-github`
- UI flow in `order-ui` now triggers complete-to-github automatically when intake completes (no manual Decompose button in the normal flow).
- Created issues include normalized metadata and labels:
  - `ai-generated`
  - `needs-human-approval`
  - `bug` or `feature`
  - `portfolio`
- GitHub issue creation audit events are append-only and non-blocking on logger failures.

### Step 4: Decision traceability foundation + intake UI timeline

- Shared traceability domain model and append-only store live in [`traceability/`](traceability/).
- A dedicated orchestration seam, [`IntakeTraceabilityAgent`](order-api/src/main/java/com/example/orderapi/service/IntakeTraceabilityAgent.java), records lifecycle events for:
  - intake session start/capture
  - bug-vs-feature classification
  - structured intake capture
  - decomposition outcome
  - GitHub payload preparation
  - GitHub issue creation success/failure
- Trace events are correlated under one `traceId` for a single intake journey and persisted to:
  - `traceability/audit/decision-trace.jsonl` (configurable via `app.intake.traceability.log-path`)
- Trace events are exposed by:
  - `GET /api/intake/trace/{traceId}`
- `order-ui` Intake chat includes a **Decision Trace** section with:
  - customer summary view
  - engineer detail view (trace ID + expanded event details)
  - issue links when available
- Guardrails:
  - summary-only rationale metadata
  - no raw chain-of-thought/prompt internals persisted or rendered

### Step 5: Approved issue pickup and execution scaffolding

- Approval conventions documented in [`docs/step5-approval-and-execution-conventions.md`](docs/step5-approval-and-execution-conventions.md).
- Scheduler discovers approved issues and applies label-driven eligibility:
  - required: `approved-for-dev`
  - excluded: `ai-in-progress`
- Step 5 runtime services:
  - [`ApprovedIssuePickupScheduler`](order-api/src/main/java/com/example/orderapi/service/ApprovedIssuePickupScheduler.java)
  - [`Step5IssueExecutionService`](order-api/src/main/java/com/example/orderapi/service/Step5IssueExecutionService.java)
  - [`scripts/auto_issue_executor.py`](scripts/auto_issue_executor.py)
- Work packet + PR scaffolding helpers:
  - [`scripts/build_work_packet.py`](scripts/build_work_packet.py)
  - [`scripts/prepare_pr_scaffold.py`](scripts/prepare_pr_scaffold.py)
- Step 5 audit events are written append-only to JSONL.

### Step 6A: PR review polling + reconciliation + terminal handoff

- Runtime polling service:
  - [`Step6PrReviewPollingService`](order-api/src/main/java/com/example/orderapi/service/Step6PrReviewPollingService.java)
- GitHub MCP-backed PR review/comment operations are exposed through:
  - [`GitHubIssueClientService`](order-api/src/main/java/com/example/orderapi/service/GitHubIssueClientService.java)
- Existing Step 6 helper scripts are retained for ad-hoc/manual use:
  - [`scripts/fetch_pr_review_feedback.py`](scripts/fetch_pr_review_feedback.py)
  - [`scripts/classify_review_comments.py`](scripts/classify_review_comments.py)
  - [`scripts/review_response_templates.py`](scripts/review_response_templates.py)
  - [`scripts/reconcile_after_merge.py`](scripts/reconcile_after_merge.py)

`Step 6A` was implemented in PR #112 and intentionally covered polling/reconciliation/terminal handoff without a full branch file-edit commit/push fix loop.

### Step 5.5: PR creation via MCP

- Step 5 executor keeps local branch + commit + push behavior.
- Pull request creation is delegated to GitHub MCP (`create_pull_request` tool).
- Runtime flow:
  - `auto_issue_executor.py` -> GitHub MCP -> GitHub
- This keeps PR creation on the MCP tool boundary while preserving the existing Step 5 orchestrator structure.

### Governance and scope boundaries

- This remains a portfolio project:
  - optimize for readable, reviewable, small-scope behavior
  - keep human-in-the-loop governance for normal merge decisions
  - defer non-critical hardening when happy-path behavior is already correct
- Security-sensitive fixes were applied in Step 5 fallback paths to avoid token-in-argv exposure and improve failure handling clarity.

## GitHub MCP Integration (Step 5 Extension)

### Architecture (implemented)

```text
Step 5 scheduler/executor + Step 6 PR polling (order-api + auto_issue_executor.py)
  -> GitHub MCP tool server (github-mcp container)
  -> GitHub API
```

MCP is used as a tool server boundary for runtime GitHub operations. This keeps app orchestration logic in the existing services/scripts while avoiding direct ad-hoc GitHub API coupling in the PR-creation step.

### Auth separation

- `APP_GITHUB_TOKEN`
  - used by application runtime
  - used by MCP server (`GITHUB_PERSONAL_ACCESS_TOKEN`)
  - fine-grained PAT
  - restricted to `pbushan/learning-order-system-ai`
- `CODEX_GITHUB_TOKEN`
  - used only by local Codex workflows
  - not used by application runtime
  - not passed into containers

### Setting up APP_GITHUB_TOKEN (Required)

1. Create a **fine-grained** PAT.
2. Restrict repository access to:
   - `pbushan/learning-order-system-ai`
3. Grant minimum permissions:
   - Issues: Read and write
   - Pull requests: Read and write
   - Contents: Read and write
   - Metadata: Read

Do **NOT** use a classic PAT for `APP_GITHUB_TOKEN`.

### Runtime notes

- Internal MCP URL: `http://github-mcp:8082`
- MCP runs on an internal Docker network and does not expose ports to avoid conflicts.
- Current app-side MCP operations:
  - issue creation
  - issue label updates
  - approved/in-progress issue discovery
  - Step 5 PR creation via MCP tool call (`create_pull_request`)
  - Step 6 PR listing + review/comment ingestion + reconciliation comments

### Token troubleshooting (Step 5/6 write path)

- If issue automation comments show `Resource not accessible by personal access token` or repeated `403` during branch push/write:
  - your `APP_GITHUB_TOKEN` does not currently have enough effective write scope for repository updates.
- Recreate `APP_GITHUB_TOKEN` as fine-grained and ensure:
  - repository access is restricted to `pbushan/learning-order-system-ai`
  - Contents: Read and write
  - Pull requests: Read and write
  - Issues: Read and write
  - Metadata: Read
- After updating `.env`, restart runtime:
  - `docker compose up -d --build`
- Then re-add `approved-for-dev` to any deferred issue you want retried.

## Step 6A: PR Review Polling and Reconciliation

### Purpose

Step 6A provides a bounded PR review loop for agent-managed PRs:
- polls eligible open PRs
- ingests human/bot review signals
- applies only conservative safe follow-ups
- posts reconciliation + terminal handoff comments
- stops polling explicitly (no infinite loop behavior)

### Runtime/auth separation

- Application runtime path:
  - `APP_GITHUB_TOKEN` -> GitHub MCP -> GitHub
  - used by Step 5 and Step 6 runtime services/scripts
- Local Codex path:
  - `CODEX_GITHUB_TOKEN` only
  - unchanged by Step 6
- Step 6 does not pass `CODEX_GITHUB_TOKEN` into containers.

### Runtime flow

```text
PR opened (agent-managed)
-> Step 6 waits/polls for review feedback
-> If review/comment exists: ingest + classify + reconcile
-> If no feedback after wait window: post lightweight self-review
-> Apply conservative safe follow-up only when deterministic
-> Post reconciliation comment
-> Post terminal handoff comment + terminal label
-> Stop polling this PR
-> Human reviews and merges
```

### Review sources ingested

- review summaries (`pull_request_read method=get_reviews`)
- review comments/threads (`pull_request_read method=get_review_comments`)
- top-level PR comments (`issue_read method=get_comments` on PR issue number)
- Step 6 self-review comments (explicit marker)

### Guardrails

- max cycles per PR (`APP_STEP6_MAX_CYCLES_PER_PR`, default `3`)
- max self-review attempts (`APP_STEP6_MAX_SELF_REVIEW_ATTEMPTS`, default `1`)
- processed item tracking (review/comment IDs)
- no-op stop when no actionable changes are found
- repeat-finding detection escalation to human review
- terminal marker label (`step6-terminal`) prevents re-polling
- open-PR only polling
- conservative classification bias:
  - ambiguous/risky findings -> `NEEDS_HUMAN`
- no auto-approval and no auto-merge in Step 6

### Final terminal behavior

Step 6A posts a final terminal handoff comment that states:
- polling has stopped for that PR
- safe automated reconciliation is complete for the bounded loop
- remaining non-trivial concerns require human review
- PR is ready for human merge review when appropriate

### What Step 6 automates

- PR polling for agent-managed open PRs
- review/comment ingestion and normalization
- deterministic finding classification
- conservative safe follow-up actions
- reconciliation summary comment
- explicit terminal handoff and stop marker

### What Step 6 does not automate

- infinite monitoring
- auto-merge
- auto-approval
- replacement of human review judgment
- broad autonomous refactors

### Step 6 config

- `APP_STEP6_ENABLED=true`
- `APP_STEP6_POLL_INTERVAL_MS=30000`
- `APP_STEP6_MAX_CYCLES_PER_PR=3`
- `APP_STEP6_MAX_SELF_REVIEW_ATTEMPTS=1`
- `APP_STEP6_WAIT_CYCLES_BEFORE_SELF_REVIEW=2`

### Step 6A testing notes

Validated locally in Docker runtime by:
- starting compose stack with MCP + order-api
- confirming Step 5 PR creation still works through MCP
- running Step 6 poller against open agent-managed PRs
- verifying review/comment ingestion through MCP methods
- verifying self-review fallback when no external feedback is present
- verifying terminal handoff comment + `step6-terminal` label stop reprocessing

## Step 6B: PR review fix execution loop

Step 6B extends Step 6A with conservative follow-up code execution for `SAFE_AUTO_FIX` findings.

Runtime call chain:

```text
Step6PrReviewPollingService
  -> build safe fix packet
  -> Step5IssueExecutionService.executeStep6SafeFix(...)
  -> scripts/auto_issue_executor.py (Step 6 mode)
  -> checkout same PR branch -> apply deterministic text fix -> commit -> push
  -> Step 6 reconciliation comment with actual changed commit
```

Step 6B remains conservative:
- only deterministic safe fix packets are executed
- no auto-approval and no auto-merge
- terminal handoff still marks the stop boundary for polling

---

## 🤖 Agentic Build Showcase

This repository now also serves as a portfolio example of **agentic software delivery**.

On this branch, an LLM agent was used to:

- inspect the existing repo structure and reconcile with the remote branch state
- add a separate browser UI in `order-ui/`
- connect that UI to the Spring Boot API and all existing order-management flows
- wire the UI into the `order-api` Docker Compose startup for IntelliJ-based development
- add a root `docker-compose.yml` so the entire stack can be tested without IntelliJ
- preserve reviewability by isolating the work on a dedicated feature branch

This is the collaboration model behind the change:

- Human defines the goal, constraints, and review expectations
- Agent inspects, implements, documents, and prepares the change
- Human reviews locally, approves, and merges

That hybrid workflow is intentional. It shows how LLMs can contribute beyond code generation by handling integration work, developer experience improvements, and documentation in a way that stays safe for human review.

---

## 🧠 Key Concepts Demonstrated

- REST APIs (CRUD)
- Async messaging (RabbitMQ)
- Background processing (consumer app)
- Serverless decision-making (Lambda)
- Integration testing vs unit testing
- AI-assisted code evolution
- Agentic workflows in practice

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
├── order-ui/                <-- Browser UI for the customer/order workflow plus the new product catalog tab
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
- order-ui

So your wife can focus on the concepts instead of manually starting infrastructure.

The UI will be available at:

`http://localhost:8081`

## Run the full stack without IntelliJ

If you want to test the branch entirely through Docker, run this from the repo root:

```bash
docker compose up --build
```

That root compose file stitches together:

- `order-api`
- `order-ui`
- `order-consumer`
- MySQL
- RabbitMQ
- LocalStack

Useful URLs in that mode:

- UI: `http://localhost:8081`
- API: `http://localhost:8080`
- RabbitMQ UI: `http://localhost:15672`
 
The backend now wires in `DataInitializer`, which seeds ten reference customers plus ten catalog products (complete with price, physical, shipping, status, and tags) whenever it starts. That guarantees the UI dashboards and product tab stay populated immediately after `docker compose up`.

This makes the branch easier to demo as a complete, portfolio-ready system because the backend, messaging, Lambda emulator, and UI can all be started with one command from the repo root.

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
- CRUD for products with pricing/shipping metadata
- `DataInitializer` seeds ten reference customers and catalog products on each backend start

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
  "productId": 1,
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

### Product endpoints

#### Create product
```http
POST /api/products
```

Body:
```json
{
  "sku": "WM-12345",
  "name": "Wireless Mouse",
  "description": "Ergonomic Bluetooth mouse",
  "category": "Electronics",
  "price": {
    "amount": 29.99,
    "currency": "USD"
  },
  "physical": {
    "weight": {
      "value": 0.2,
      "unit": "kg"
    },
    "dimensions": {
      "length": 10,
      "width": 6,
      "height": 4,
      "unit": "cm"
    }
  },
  "shipping": {
    "fragile": false,
    "hazmat": false,
    "requiresCooling": false,
    "maxStackable": 10
  },
  "status": {
    "active": true,
    "shippable": true
  },
  "tags": ["mouse", "wireless"]
}
```

The UI enforces every part of this payload, so the backend can rely on each product having price, physical, shipping, status, and tags metadata before saving it.

#### Get all products
```http
GET /api/products
```

#### Get one product
```http
GET /api/products/{id}
```

#### Update product
```http
PUT /api/products/{id}
```

#### Delete product
```http
DELETE /api/products/{id}
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
    "productId":1,
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
