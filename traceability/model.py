"""Domain model for append-only intake decision trace events."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any


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
        return DecisionTraceEvent(
            trace_id=str(record.get("traceId") or ""),
            session_id=str(record.get("sessionId") or ""),
            correlation_id=str(record.get("correlationId") or ""),
            event_type=str(record.get("eventType") or ""),
            timestamp=str(record.get("timestamp") or ""),
            status=str(record.get("status") or ""),
            actor=str(record.get("actor") or ""),
            summary=str(record.get("summary") or ""),
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

    return DecisionTraceEvent(
        trace_id=str(trace_id),
        session_id=str(session_id),
        correlation_id=str(correlation_id),
        event_type=str(event_type),
        status=str(status),
        actor=str(actor),
        summary=str(summary),
        decision_metadata=dict(decision_metadata or {}),
        input_summary=dict(input_summary or {}),
        artifact_summary=dict(artifact_summary or {}),
        governance_metadata=dict(governance_metadata or {}),
    )
