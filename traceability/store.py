"""Append-only JSONL persistence for decision trace events."""

from __future__ import annotations

import json
import os
from pathlib import Path

from traceability.model import DecisionTraceEvent

DEFAULT_TRACE_LOG_PATH = "traceability/audit/decision-trace.jsonl"


def resolve_trace_log_path(path: str | Path | None = None) -> Path:
    configured = path if path is not None else os.getenv("TRACEABILITY_LOG_PATH", DEFAULT_TRACE_LOG_PATH)
    return Path(configured).resolve()


def append_trace_event(event: DecisionTraceEvent, path: str | Path | None = None) -> None:
    """Append one immutable event as a JSON line."""

    log_path = resolve_trace_log_path(path)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(event.to_record(), ensure_ascii=True))
        handle.write("\n")


def read_trace_events(
    *,
    trace_id: str | None = None,
    session_id: str | None = None,
    path: str | Path | None = None,
) -> list[DecisionTraceEvent]:
    """Read events filtered to one trace or one session."""

    if not trace_id and not session_id:
        raise ValueError("read_trace_events requires trace_id or session_id")

    log_path = resolve_trace_log_path(path)
    if not log_path.exists():
        return []

    matches: list[DecisionTraceEvent] = []
    with log_path.open("r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line:
                continue
            record = json.loads(line)
            if trace_id and str(record.get("traceId") or "") != trace_id:
                continue
            if session_id and str(record.get("sessionId") or "") != session_id:
                continue
            matches.append(DecisionTraceEvent.from_record(record))
    return matches
