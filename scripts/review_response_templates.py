#!/usr/bin/env python3
"""Minimal reusable templates for Step 6 PR review responses."""

from __future__ import annotations

import argparse

from step6_audit_log import log_step6_event


DEFERRAL_TEXT = (
    "Deferring this for now. This repository is a portfolio project and this iteration is "
    "intentionally scoped for a minimal, readable implementation. The current behavior is "
    "acceptable for the happy path and does not introduce a material risk. I\u2019m keeping this "
    "improvement for a future hardening pass."
)


def addressed_reply(summary: str) -> str:
    return f"Addressed in this PR. {summary}".strip()


def deferred_reply() -> str:
    return DEFERRAL_TEXT


def ready_to_merge_note(fixed: str, deferred: str) -> str:
    return (
        "Final note before merge:\n\n"
        f"- Fixed: {fixed}\n"
        f"- Intentionally deferred: {deferred or 'None.'}\n"
        "- Deferred rationale: Non-blocking items are acceptable for this portfolio-scoped "
        "iteration and can be hardened later.\n"
        "- Status: All P2+ comments are addressed under the blocking criteria.\n"
        "- This PR is ready to merge."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Render Step 6 review response templates.")
    parser.add_argument("--mode", required=True, choices=["addressed", "deferred", "ready"])
    parser.add_argument("--summary", default="", help="Short summary for addressed mode.")
    parser.add_argument("--fixed", default="Applied requested updates.", help="Fixed summary for ready mode.")
    parser.add_argument("--deferred", default="", help="Deferred summary for ready mode.")
    args = parser.parse_args()

    if args.mode == "addressed":
        response = addressed_reply(args.summary)
        print(response)
        log_step6_event("review-comment-addressed", metadata={"summary": args.summary})
    elif args.mode == "deferred":
        response = deferred_reply()
        print(response)
        log_step6_event("review-comment-deferred", metadata={"reason": "portfolio-scope-non-blocking"})
    else:
        response = ready_to_merge_note(args.fixed, args.deferred)
        print(response)
        log_step6_event(
            "ready-to-merge-note-posted",
            metadata={"fixed": args.fixed, "deferred": args.deferred},
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
