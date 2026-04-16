"""Domain model for append-only intake decision trace events."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from datetime import timedelta
import re
from typing import Any

ALLOWED_STATUSES = {
    "recorded",
    "pending",
    "accepted",
    "approved",
    "rejected",
    "completed",
    "failed",
    "skipped",
}

EVENT_TYPE_PATTERN = re.compile(r"^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
ACTOR_PATTERN = re.compile(r"^[a-z0-9]+(?:[._-][a-z0-9]+)*$")


@dataclass(frozen=True)
class DecisionTraceEvent:
    """Single append-only event used to explain intake decisions."""

    trace_id: str
    session_id: str
    correlation_id: str
    event_type: str
    status: str
    actor: str
    summary: str
    decision_metadata: dict[str, Any] = field(default_factory=dict)
    input_summary: dict[str, Any] = field(default_factory=dict)
    artifact_summary: dict[str, Any] = field(default_factory=dict)
    governance_metadata: dict[str, Any] = field(default_factory=dict)
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    def to_record(self) -> dict[str, Any]:
        return {
            "traceId": self.trace_id,
            "sessionId": self.session_id,
            "correlationId": self.correlation_id,
            "eventType": self.event_type,
            "timestamp": self.timestamp,
            "status": self.status,
            "actor": self.actor,
            "summary": self.summary,
            "decisionMetadata": self.decision_metadata,
            "inputSummary": self.input_summary,
            "artifactSummary": self.artifact_summary,
            "governanceMetadata": self.governance_metadata,
        }

    @staticmethod
    def from_record(record: dict[str, Any]) -> "DecisionTraceEvent":
        trace_id = _required_record_value(record, "traceId")
        session_id = _required_record_value(record, "sessionId")
        correlation_id = _required_record_value(record, "correlationId")
        event_type = _required_record_value(record, "eventType")
        timestamp = _required_record_value(record, "timestamp")
        status = _required_record_value(record, "status")
        actor = _required_record_value(record, "actor")
        summary = _required_record_value(record, "summary")
        _validate_iso8601_timestamp(timestamp)
        _validate_event_contract(event_type=event_type, status=status, actor=actor)

        return DecisionTraceEvent(
            trace_id=trace_id,
            session_id=session_id,
            correlation_id=correlation_id,
            event_type=event_type,
            timestamp=timestamp,
            status=status,
            actor=actor,
            summary=summary,
            decision_metadata=dict(record.get("decisionMetadata") or {}),
            input_summary=dict(record.get("inputSummary") or {}),
            artifact_summary=dict(record.get("artifactSummary") or {}),
            governance_metadata=dict(record.get("governanceMetadata") or {}),
        )


def create_trace_event(
    *,
    trace_id: str,
    session_id: str,
    correlation_id: str,
    event_type: str,
    status: str,
    actor: str,
    summary: str,
    timestamp: str | None = None,
    decision_metadata: dict[str, Any] | None = None,
    input_summary: dict[str, Any] | None = None,
    artifact_summary: dict[str, Any] | None = None,
    governance_metadata: dict[str, Any] | None = None,
) -> DecisionTraceEvent:
    """Create a validated trace event with explicit metadata buckets."""

    required = {
        "trace_id": trace_id,
        "session_id": session_id,
        "correlation_id": correlation_id,
        "event_type": event_type,
        "status": status,
        "actor": actor,
        "summary": summary,
    }
    missing = [name for name, value in required.items() if not str(value).strip()]
    if missing:
        raise ValueError(f"Missing required fields: {', '.join(missing)}")

    if timestamp is not None:
        _validate_iso8601_timestamp(timestamp)
    _validate_event_contract(event_type=str(event_type), status=str(status), actor=str(actor))

    return DecisionTraceEvent(
        trace_id=str(trace_id),
        session_id=str(session_id),
        correlation_id=str(correlation_id),
        event_type=str(event_type),
        timestamp=timestamp if timestamp is not None else datetime.now(timezone.utc).isoformat(),
        status=str(status),
        actor=str(actor),
        summary=str(summary),
        decision_metadata=dict(decision_metadata or {}),
        input_summary=dict(input_summary or {}),
        artifact_summary=dict(artifact_summary or {}),
        governance_metadata=dict(governance_metadata or {}),
    )


def _required_record_value(record: dict[str, Any], key: str) -> str:
    value = record.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Invalid trace record: missing or empty {key}")
    return value


def _validate_iso8601_timestamp(timestamp: str) -> None:
    candidate = timestamp
    if candidate.endswith("Z"):
        candidate = f"{candidate[:-1]}+00:00"
    try:
        parsed = datetime.fromisoformat(candidate)
    except ValueError as exc:
        raise ValueError(f"Invalid trace record: timestamp is not ISO-8601 ({timestamp})") from exc
    offset = parsed.utcoffset()
    if parsed.tzinfo is None or offset is None or offset.total_seconds() != 0:
        raise ValueError(f"Invalid trace record: timestamp must be UTC ({timestamp})")


def _validate_event_contract(*, event_type: str, status: str, actor: str) -> None:
    if status not in ALLOWED_STATUSES:
        raise ValueError(
            f"Invalid trace record: status must be one of {sorted(ALLOWED_STATUSES)} (got {status})"
        )
    if not EVENT_TYPE_PATTERN.match(event_type):
        raise ValueError(
            "Invalid trace record: eventType must be lowercase and namespace-like "
            "(example: intake.received)"
        )
    if not ACTOR_PATTERN.match(actor):
        raise ValueError(
            "Invalid trace record: actor must be lowercase token(s) joined by '.', '_' or '-'"
        )
