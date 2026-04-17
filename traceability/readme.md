# Traceability Foundation

This folder provides a reusable, append-only decision trace model for intake workflows.

## Purpose

The module records concise, reviewable decision events for the intake lifecycle and acts as the shared traceability source of truth.

Current implementation scope:
- shared domain model + append-only JSONL persistence in this folder
- backend orchestration via `IntakeTraceabilityAgent` (`order-api`)
- `traceId` propagation through intake -> decomposition -> GitHub issue creation
- trace read API: `GET /api/intake/trace/{traceId}`
- intake chat Decision Trace UI rendering in `order-ui`

Non-goals in the current phase:
- no standalone external traceability service
- no autonomous governance workflow engine
- no hidden chain-of-thought or raw reasoning logs
- no automatic GitHub summary comments yet

## Event Schema

Each JSONL line is one immutable event with these fields:

- `traceId`: stable id across one intake trace
- `sessionId`: session identifier for a user/flow run
- `correlationId`: per-step correlation id
- `eventType`: domain event name (for example `intake.received`)
- `timestamp`: UTC ISO-8601 timestamp
- `status`: lifecycle state (`recorded`, `approved`, `rejected`, and so on)
- `actor`: service or agent producing the event
- `summary`: short human-readable explanation
- `decisionMetadata`: structured choice metadata (no chain-of-thought)
- `inputSummary`: safe summary of intake inputs
- `artifactSummary`: produced artifacts summary
- `governanceMetadata`: guardrail/compliance markers

Schema contract notes:
- persisted keys are camelCase exactly as listed above
- snake_case aliases are intentionally not accepted by the reader
- reads fail fast for missing required fields or invalid timestamp formats
- timestamps must be UTC (`Z` or `+00:00`)
- allowed `status` values: `recorded`, `pending`, `accepted`, `approved`, `rejected`, `completed`, `failed`, `skipped`
- `eventType` must be lowercase token format (single token or namespaced, examples: `intake`, `intake.received`)
- `actor` must be lowercase token format (example: `intake-api`)

## Persistence

- Append-only JSONL store
- Default log path: `traceability/audit/decision-trace.jsonl`
- Override with env var: `TRACEABILITY_LOG_PATH`

`order-api` runtime can also override its path with:
- `app.intake.traceability.log-path`

## Helpers

- `create_trace_id(prefix="trace")`
- `create_trace_event(...)`
- `append_trace_event(event, path=None)`
- `read_trace_events(trace_id=..., session_id=..., path=None)` (keyword-only filters)
  - default mode skips malformed lines and returns valid matching events
  - set `strict=True` to fail fast on malformed JSON or invalid records

## Intake Lifecycle Events (current)

Typical event types written by `IntakeTraceabilityAgent`:
- `intake.session.started`
- `intake.classification.completed`
- `intake.structured-data.captured`
- `intake.decomposition.completed` or `intake.decomposition.failed`
- `intake.github.payload.prepared`
- `intake.github.issue-creation.completed` or `intake.github.issue-creation.failed`

All events are append-only and correlated by `traceId`.
