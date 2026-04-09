#!/usr/bin/env python3
"""Classify PR review comments as blocking-now or defer for portfolio scope."""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any


BLOCKING_RULES = [
    ("security-critical risk", ["security", "auth bypass", "injection", "secret leak"]),
    ("data corruption risk", ["data corruption", "corrupt", "data loss", "destructive"]),
    ("runtime crash on normal input", ["runtime crash", "nullpointer", "exception on normal", "crash"]),
    ("happy-path contract break", ["happy path broken", "contract break", "breaking behavior", "regression"]),
]


def classify_comment(comment: dict[str, Any]) -> dict[str, Any]:
    body = str(comment.get("body", "")).lower()
    matched_reason = ""
    for reason, keywords in BLOCKING_RULES:
        if any(keyword in body for keyword in keywords):
            matched_reason = reason
            break

    is_blocking = bool(matched_reason)
    return {
        "id": comment.get("id"),
        "classification": "blocking-address-now" if is_blocking else "non-blocking-defer",
        "reason": matched_reason or "No blocking criterion matched for portfolio scope.",
        "url": comment.get("url", "") or "",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Classify normalized review comments.")
    parser.add_argument(
        "--input-json",
        default="-",
        help="Path to normalized review feedback JSON (default: stdin).",
    )
    args = parser.parse_args()

    raw = sys.stdin.read() if args.input_json == "-" else open(args.input_json, "r", encoding="utf-8").read()
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        print(f"Invalid input JSON: {exc}", file=sys.stderr)
        return 1

    comments = payload.get("comments", []) if isinstance(payload, dict) else []
    if not isinstance(comments, list):
        print("Invalid input JSON: comments must be a list.", file=sys.stderr)
        return 1

    output = {
        "prNumber": payload.get("prNumber") if isinstance(payload, dict) else None,
        "classifications": [classify_comment(c if isinstance(c, dict) else {}) for c in comments],
    }
    print(json.dumps(output, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
