"""Traceability identifier helpers."""

from __future__ import annotations

import uuid


def create_trace_id(prefix: str = "trace") -> str:
    """Create a stable, human-readable trace id."""

    normalized = (prefix or "trace").strip().lower()
    return f"{normalized}-{uuid.uuid4().hex}"
