# Traceability Foundation

This folder provides a reusable, append-only decision trace model for intake workflows.

## Purpose

The module records concise, reviewable decision events that can later be rendered in UI views and GitHub summaries.

Non-goals for this foundation:
- no deep intake UI integration yet
- no GitHub summary comment integration yet
- no hidden chain-of-thought or raw reasoning logs

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
- `eventType` must be lowercase namespace-like format (example: `intake.received`)
- `actor` must be lowercase token format (example: `intake-api`)

## Persistence

- Append-only JSONL store
- Default log path: `traceability/audit/decision-trace.jsonl`
- Override with env var: `TRACEABILITY_LOG_PATH`

## Helpers

- `create_trace_id(prefix="trace")`
- `create_trace_event(...)`
- `append_trace_event(event, path=None)`
- `read_trace_events(trace_id=..., session_id=..., path=None)` (keyword-only filters)
