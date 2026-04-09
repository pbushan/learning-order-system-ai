#!/usr/bin/env python3
"""Prepare minimal branch/PR scaffold metadata for an approved issue."""

from __future__ import annotations

import argparse
import json
import re
import sys
from typing import Any


def normalize_slug(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug[:40] or "work"


def build_pr_body(packet: dict[str, Any]) -> str:
    summary = str(packet.get("summary", "")).strip() or str(packet["title"]).strip()
    guidance = packet.get("prSafeGuidance") or []
    guidance_lines = "\n".join(f"- {line}" for line in guidance if str(line).strip())

    return (
        "## Summary\n"
        f"{summary}\n\n"
        "## PR-Safe Checklist\n"
        "- [x] Small, single-concern slice\n"
        "- [x] No unrelated refactors\n"
        "- [x] Behavior remains explicit and readable\n"
        "- [ ] Additional hardening deferred to a future pass if non-blocking\n\n"
        "## Implementation Guidance\n"
        f"{guidance_lines or '- Keep changes minimal and reviewable.'}\n\n"
        "## Governance Note\n"
        "This is a portfolio-scoped repository. Historically, this workflow uses a human merge gate "
        "after review, and this note is kept for governance traceability.\n"
    )


def build_scaffold(packet: dict[str, Any]) -> dict[str, Any]:
    issue_number = int(packet["issueNumber"])
    title = str(packet["title"]).strip()
    branch_name = str(packet.get("branchNameSuggestion", "")).strip()
    if not branch_name:
        branch_name = f"codex/issue-{issue_number}-{normalize_slug(title)}"

    return {
        "issueNumber": issue_number,
        "branchName": branch_name,
        "prTitle": f"Issue #{issue_number}: {title}",
        "prBody": build_pr_body(packet),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build Step 5 PR scaffold JSON.")
    parser.add_argument(
        "--work-packet-json",
        default="-",
        help="Path to work packet JSON input (default: stdin).",
    )
    args = parser.parse_args()

    raw = sys.stdin.read() if args.work_packet_json == "-" else open(
        args.work_packet_json, "r", encoding="utf-8"
    ).read()

    try:
        packet = json.loads(raw)
    except json.JSONDecodeError as exc:
        print(f"Invalid work packet JSON: {exc}", file=sys.stderr)
        return 1

    if not isinstance(packet, dict):
        print("Invalid work packet JSON: expected an object.", file=sys.stderr)
        return 1
    if "issueNumber" not in packet or "title" not in packet:
        print("Invalid work packet JSON: requires issueNumber and title.", file=sys.stderr)
        return 1

    try:
        issue_number = int(packet["issueNumber"])
    except (TypeError, ValueError):
        print("Invalid work packet JSON: issueNumber must be numeric.", file=sys.stderr)
        return 1
    if issue_number <= 0:
        print("Invalid work packet JSON: issueNumber must be > 0.", file=sys.stderr)
        return 1

    if not str(packet["title"]).strip():
        print("Invalid work packet JSON: title must be non-empty.", file=sys.stderr)
        return 1

    packet["issueNumber"] = issue_number
    print(json.dumps(build_scaffold(packet), ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
