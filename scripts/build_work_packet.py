#!/usr/bin/env python3
"""Build a minimal Step 5 execution packet from approved issue metadata."""

from __future__ import annotations

import argparse
import json
import re
import sys
from typing import Any


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
        print(f"Invalid issue JSON: {exc}", file=sys.stderr)
        return 1

    if not isinstance(issue, dict):
        print("Invalid issue JSON: expected an object.", file=sys.stderr)
        return 1

    if "issueNumber" not in issue or "title" not in issue:
        print("Invalid issue JSON: requires issueNumber and title.", file=sys.stderr)
        return 1

    if not str(issue.get("title", "")).strip():
        print("Invalid issue JSON: title must be non-empty.", file=sys.stderr)
        return 1

    try:
        issue_number = int(issue["issueNumber"])
    except (TypeError, ValueError):
        print("Invalid issue JSON: issueNumber must be numeric.", file=sys.stderr)
        return 1

    if issue_number <= 0:
        print("Invalid issue JSON: issueNumber must be > 0.", file=sys.stderr)
        return 1

    issue["issueNumber"] = issue_number
    packet = build_work_packet(issue)
    print(json.dumps(packet, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
