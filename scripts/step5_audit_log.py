#!/usr/bin/env python3
"""Append-only Step 5 audit logging helper (JSONL)."""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def log_step5_event(
    operation: str,
    request_id: str = "",
    issue_number: int | None = None,
    metadata: dict[str, Any] | None = None,
    error: str = "",
) -> None:
    path = Path(os.getenv("STEP5_AUDIT_LOG_PATH", "order-api/audit/intake-chat.jsonl"))
    entry = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "requestId": request_id or "",
        "operation": operation or "",
        "issueNumber": issue_number,
        "metadata": metadata or {},
        "error": error or "",
    }
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=True))
            f.write("\n")
    except Exception:
        # Logging failures must never break workflow scripts.
        pass
