## Summary

This pull request adds a reviewable, agent-built UI and developer workflow improvement to the learning order system.

## What The Agent Observed

- `main` was aligned with `origin/main`
- `order-api` already exposed customer CRUD, order CRUD, and order submission endpoints
- Docker Compose startup already existed inside `order-api/`, but there was no separate UI project and no root-level orchestration for full branch testing

## Decision The Agent Made

- Added a separate `order-ui/` frontend so the UI is isolated from the backend codebase
- Integrated the UI into `order-api/docker-compose.yml` so IntelliJ startup builds and serves the frontend automatically
- Added a root `docker-compose.yml` and `order-api/Dockerfile` so the whole system can be run and reviewed without IntelliJ
- Updated documentation so the branch clearly presents the work as agentic, human-reviewed software delivery

## Why This Change Is Needed

- It makes the system easier to demo, evaluate, and review
- It provides a clean UI for exercising the app's API capabilities
- It improves local testing by removing the requirement to launch from IntelliJ every time
- It better reflects the repository's goal of showcasing AI-assisted and agent-driven engineering workflows

## Agentic Work Disclosure

This change was produced with LLM assistance. The agent inspected the repository, implemented the UI and Docker integration, updated documentation, and prepared the work for human review and approval.

## Review Notes

- The UI lives in `order-ui/`
- IntelliJ-based startup continues to work through `order-api/docker-compose.yml`
- Full-stack branch testing is available through the root `docker-compose.yml`
- The change is isolated on a feature branch for safe human review
