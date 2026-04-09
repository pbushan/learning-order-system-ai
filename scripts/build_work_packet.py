#!/usr/bin/env python3
"""Build a minimal Step 5 execution packet from approved issue metadata."""

from __future__ import annotations

import argparse
import json
import re
import sys
from typing import Any

from step5_audit_log import log_step5_event


def fail(error: str) -> int:
    print(error, file=sys.stderr)
    log_step5_event("work-packet-created", error=error)
    return 1


def normalize_slug(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug[:40] or "work"


def extract_section_lines(body: str, heading: str) -> list[str]:
    if not body:
        return []

    pattern = re.compile(
        rf"(?im)^##\s*{re.escape(heading)}\s*$([\s\S]*?)(?=^#{{2,}}\s+|\Z)"
    )
    match = pattern.search(body)
    if not match:
        return []

    lines = []
    for raw_line in match.group(1).splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("- "):
            line = line[2:].strip()
        lines.append(line)
    return lines


def build_work_packet(issue: dict[str, Any]) -> dict[str, Any]:
    issue_number = int(issue["issueNumber"])
    title = str(issue.get("title", "")).strip()
    body = str(issue.get("body", "")).strip()

    summary = body.splitlines()[0].strip() if body else ""
    acceptance_criteria = extract_section_lines(body, "Acceptance Criteria")
    affected_components = extract_section_lines(body, "Affected Components")

    return {
        "issueNumber": issue_number,
        "title": title,
        "summary": summary,
        "acceptanceCriteria": acceptance_criteria,
        "affectedComponents": affected_components,
        "branchNameSuggestion": f"codex/issue-{issue_number}-{normalize_slug(title)}",
        "prSafeGuidance": [
            "Keep the patch single-concern and reviewable.",
            "Prefer explicit behavior over broad abstractions.",
            "Split larger work into additional small PR slices.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build Step 5 work packet JSON.")
    parser.add_argument(
        "--issue-json",
        default="-",
        help="Path to approved issue JSON input (default: stdin).",
    )
    args = parser.parse_args()

    if args.issue_json == "-":
        raw = sys.stdin.read()
    else:
        with open(args.issue_json, "r", encoding="utf-8") as f:
            raw = f.read()

    try:
        issue = json.loads(raw)
    except json.JSONDecodeError as exc:
        return fail(f"Invalid issue JSON: {exc}")

    if not isinstance(issue, dict):
        return fail("Invalid issue JSON: expected an object.")

    if "issueNumber" not in issue or "title" not in issue:
        return fail("Invalid issue JSON: requires issueNumber and title.")

    if not str(issue.get("title", "")).strip():
        return fail("Invalid issue JSON: title must be non-empty.")

    try:
        issue_number = int(issue["issueNumber"])
    except (TypeError, ValueError):
        return fail("Invalid issue JSON: issueNumber must be numeric.")

    if issue_number <= 0:
        return fail("Invalid issue JSON: issueNumber must be > 0.")

    issue["issueNumber"] = issue_number
    packet = build_work_packet(issue)
    log_step5_event(
        "work-packet-created",
        issue_number=issue_number,
        metadata={"packet": packet},
    )
    print(json.dumps(packet, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
