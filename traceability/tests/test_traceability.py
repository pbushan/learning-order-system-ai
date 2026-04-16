from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from traceability import append_trace_event, create_trace_event, create_trace_id, read_trace_events


class TraceabilityFoundationTest(unittest.TestCase):
    def test_create_trace_id_uses_prefix_and_unique_suffix(self) -> None:
        first = create_trace_id("intake")
        second = create_trace_id("intake")

        self.assertTrue(first.startswith("intake-"))
        self.assertTrue(second.startswith("intake-"))
        self.assertNotEqual(first, second)

    def test_create_trace_event_requires_core_fields(self) -> None:
        with self.assertRaises(ValueError):
            create_trace_event(
                trace_id="",
                session_id="session-1",
                correlation_id="corr-1",
                event_type="intake.validation",
                status="accepted",
                actor="intake-service",
                summary="Validated user request",
            )

    def test_create_trace_event_accepts_explicit_timestamp_for_determinism(self) -> None:
        event = create_trace_event(
            trace_id="trace-123",
            session_id="session-abc",
            correlation_id="corr-1",
            event_type="intake.received",
            status="recorded",
            actor="intake-api",
            summary="Intake payload accepted",
            timestamp="2026-04-16T10:00:00+00:00",
        )
        self.assertEqual(event.timestamp, "2026-04-16T10:00:00+00:00")

    def test_create_trace_event_rejects_invalid_explicit_timestamp(self) -> None:
        with self.assertRaises(ValueError):
            create_trace_event(
                trace_id="trace-123",
                session_id="session-abc",
                correlation_id="corr-1",
                event_type="intake.received",
                status="recorded",
                actor="intake-api",
                summary="Intake payload accepted",
                timestamp="bad-ts",
            )

    def test_append_only_write_and_query_by_trace_and_session(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            trace_id = "trace-123"
            session_id = "session-abc"

            first = create_trace_event(
                trace_id=trace_id,
                session_id=session_id,
                correlation_id="corr-1",
                event_type="intake.received",
                status="recorded",
                actor="intake-api",
                summary="Intake payload accepted",
                input_summary={"title": "Need better intake trace"},
                governance_metadata={"piiSafe": True},
            )
            second = create_trace_event(
                trace_id=trace_id,
                session_id=session_id,
                correlation_id="corr-2",
                event_type="decision.generated",
                status="approved",
                actor="decision-engine",
                summary="Decision packet created",
                decision_metadata={"selectedOption": "A"},
                artifact_summary={"artifactType": "decision-packet", "artifactCount": 1},
            )
            third = create_trace_event(
                trace_id="trace-other",
                session_id="session-other",
                correlation_id="corr-3",
                event_type="decision.generated",
                status="approved",
                actor="decision-engine",
                summary="Other trace",
            )

            append_trace_event(first, path)
            append_trace_event(second, path)
            append_trace_event(third, path)

            raw_lines = path.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(raw_lines), 3)

            by_trace = read_trace_events(trace_id=trace_id, path=path)
            self.assertEqual(len(by_trace), 2)
            self.assertEqual(by_trace[0].event_type, "intake.received")
            self.assertEqual(by_trace[1].event_type, "decision.generated")

            by_session = read_trace_events(session_id=session_id, path=path)
            self.assertEqual(len(by_session), 2)
            self.assertTrue(all(event.session_id == session_id for event in by_session))

    def test_read_rejects_missing_required_camel_case_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            invalid_record = {
                "traceId": "trace-123",
                "session_id": "session-abc",
                "correlation_id": "corr-1",
                "event_type": "intake.received",
                "timestamp": "2026-04-16T10:00:00+00:00",
                "status": "recorded",
                "actor": "intake-api",
                "summary": "snake_case keys are not accepted",
            }
            path.write_text(f"{json.dumps(invalid_record)}\n", encoding="utf-8")

            with self.assertRaises(ValueError):
                read_trace_events(trace_id="trace-123", path=path)

    def test_read_rejects_invalid_timestamp(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            invalid_record = {
                "traceId": "trace-123",
                "sessionId": "session-abc",
                "correlationId": "corr-1",
                "eventType": "intake.received",
                "timestamp": "not-a-timestamp",
                "status": "recorded",
                "actor": "intake-api",
                "summary": "invalid timestamp",
            }
            path.write_text(f"{json.dumps(invalid_record)}\n", encoding="utf-8")

            with self.assertRaises(ValueError):
                read_trace_events(trace_id="trace-123", path=path)


if __name__ == "__main__":
    unittest.main()
