from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path

from traceability import append_trace_event, create_trace_event, create_trace_id, read_trace_events
from traceability.store import resolve_trace_log_path


class TraceabilityFoundationTest(unittest.TestCase):
    def test_resolve_trace_log_path_prefers_explicit_path_then_env(self) -> None:
        explicit = resolve_trace_log_path("traceability/audit/custom.jsonl")
        self.assertTrue(str(explicit).endswith("traceability/audit/custom.jsonl"))

        original = os.environ.get("TRACEABILITY_LOG_PATH")
        try:
            os.environ["TRACEABILITY_LOG_PATH"] = "traceability/audit/from-env.jsonl"
            resolved = resolve_trace_log_path()
            self.assertTrue(str(resolved).endswith("traceability/audit/from-env.jsonl"))
        finally:
            if original is None:
                os.environ.pop("TRACEABILITY_LOG_PATH", None)
            else:
                os.environ["TRACEABILITY_LOG_PATH"] = original

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

    def test_create_trace_event_rejects_non_utc_timestamp(self) -> None:
        with self.assertRaises(ValueError):
            create_trace_event(
                trace_id="trace-123",
                session_id="session-abc",
                correlation_id="corr-1",
                event_type="intake.received",
                status="recorded",
                actor="intake-api",
                summary="Intake payload accepted",
                timestamp="2026-04-16T10:00:00+05:00",
            )

    def test_create_trace_event_rejects_invalid_status(self) -> None:
        with self.assertRaises(ValueError):
            create_trace_event(
                trace_id="trace-123",
                session_id="session-abc",
                correlation_id="corr-1",
                event_type="intake.received",
                status="done",
                actor="intake-api",
                summary="Intake payload accepted",
                timestamp="2026-04-16T10:00:00+00:00",
            )

    def test_create_trace_event_rejects_invalid_event_type(self) -> None:
        with self.assertRaises(ValueError):
            create_trace_event(
                trace_id="trace-123",
                session_id="session-abc",
                correlation_id="corr-1",
                event_type="IntakeReceived",
                status="recorded",
                actor="intake-api",
                summary="Intake payload accepted",
                timestamp="2026-04-16T10:00:00+00:00",
            )

    def test_create_trace_event_accepts_single_token_event_type(self) -> None:
        event = create_trace_event(
            trace_id="trace-123",
            session_id="session-abc",
            correlation_id="corr-1",
            event_type="intake",
            status="recorded",
            actor="intake-api",
            summary="Intake payload accepted",
            timestamp="2026-04-16T10:00:00+00:00",
        )
        self.assertEqual(event.event_type, "intake")

    def test_create_trace_event_rejects_invalid_actor(self) -> None:
        with self.assertRaises(ValueError):
            create_trace_event(
                trace_id="trace-123",
                session_id="session-abc",
                correlation_id="corr-1",
                event_type="intake.received",
                status="recorded",
                actor="Intake API",
                summary="Intake payload accepted",
                timestamp="2026-04-16T10:00:00+00:00",
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
                read_trace_events(trace_id="trace-123", path=path, strict=True)

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
                read_trace_events(trace_id="trace-123", path=path, strict=True)

    def test_read_rejects_non_utc_timestamp(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            invalid_record = {
                "traceId": "trace-123",
                "sessionId": "session-abc",
                "correlationId": "corr-1",
                "eventType": "intake.received",
                "timestamp": "2026-04-16T10:00:00+03:00",
                "status": "recorded",
                "actor": "intake-api",
                "summary": "non utc timestamp",
            }
            path.write_text(f"{json.dumps(invalid_record)}\n", encoding="utf-8")

            with self.assertRaises(ValueError):
                read_trace_events(trace_id="trace-123", path=path, strict=True)

    def test_read_skips_invalid_lines_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            valid_record = {
                "traceId": "trace-123",
                "sessionId": "session-abc",
                "correlationId": "corr-1",
                "eventType": "intake.received",
                "timestamp": "2026-04-16T10:00:00+00:00",
                "status": "recorded",
                "actor": "intake-api",
                "summary": "valid event",
            }
            path.write_text(
                "{bad-json}\n"
                f"{json.dumps(valid_record)}\n"
                f"{json.dumps({**valid_record, 'traceId': 'trace-456', 'status': 'bad'})}\n",
                encoding="utf-8",
            )
            events = read_trace_events(trace_id="trace-123", path=path)
            self.assertEqual(len(events), 1)
            self.assertEqual(events[0].trace_id, "trace-123")

    def test_read_strict_mode_fails_on_invalid_lines(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            path.write_text("{bad-json}\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                read_trace_events(trace_id="trace-123", path=path, strict=True)

    def test_read_filters_with_trace_and_session_together(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "decision-trace.jsonl"
            event = create_trace_event(
                trace_id="trace-123",
                session_id="session-abc",
                correlation_id="corr-1",
                event_type="intake.received",
                status="recorded",
                actor="intake-api",
                summary="match",
                timestamp="2026-04-16T10:00:00+00:00",
            )
            other_session = create_trace_event(
                trace_id="trace-123",
                session_id="session-other",
                correlation_id="corr-2",
                event_type="intake.received",
                status="recorded",
                actor="intake-api",
                summary="other session",
                timestamp="2026-04-16T10:00:01+00:00",
            )
            append_trace_event(event, path)
            append_trace_event(other_session, path)

            events = read_trace_events(trace_id="trace-123", session_id="session-abc", path=path)
            self.assertEqual(len(events), 1)
            self.assertEqual(events[0].correlation_id, "corr-1")


if __name__ == "__main__":
    unittest.main()
