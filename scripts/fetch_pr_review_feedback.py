#!/usr/bin/env python3
"""Fetch and normalize review feedback for the current working PR."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from step6_audit_log import log_step6_event


def fail(message: str) -> int:
    print(message, file=sys.stderr)
    log_step6_event("review-comments-retrieved", error=message)
    return 1


def github_get(path: str, token: str) -> Any:
    req = urllib.request.Request(
        url=f"https://api.github.com{path}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "step6-pr-review-helper",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def current_branch() -> str:
    return subprocess.check_output(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        text=True,
    ).strip()


def resolve_pr_number(owner: str, repo: str, token: str, pr_number: int | None) -> int:
    if pr_number is not None:
        return pr_number
    branch = current_branch()
    query = urllib.parse.urlencode({"state": "open", "head": f"{owner}:{branch}", "per_page": "1"})
    pulls = github_get(f"/repos/{owner}/{repo}/pulls?{query}", token)
    if not pulls:
        raise ValueError(f"No open PR found for branch '{branch}'.")
    return int(pulls[0]["number"])


def normalize_comment(comment: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": comment.get("id"),
        "type": "review-comment",
        "author": (comment.get("user") or {}).get("login", ""),
        "body": comment.get("body", "") or "",
        "path": comment.get("path", "") or "",
        "line": comment.get("line"),
        "url": comment.get("html_url", "") or "",
        "createdAt": comment.get("created_at", "") or "",
    }


def normalize_review(review: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": review.get("id"),
        "type": "review-summary",
        "author": (review.get("user") or {}).get("login", ""),
        "state": review.get("state", "") or "",
        "body": review.get("body", "") or "",
        "url": review.get("html_url", "") or "",
        "submittedAt": review.get("submitted_at", "") or "",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch normalized PR review feedback.")
    parser.add_argument("--owner", required=True, help="GitHub repository owner.")
    parser.add_argument("--repo", required=True, help="GitHub repository name.")
    parser.add_argument("--pr-number", type=int, default=None, help="PR number (optional).")
    args = parser.parse_args()

    token = os.getenv("CODEX_GITHUB_TOKEN", "").strip()
    if not token:
        return fail("CODEX_GITHUB_TOKEN is required.")

    try:
        pr_number = resolve_pr_number(args.owner, args.repo, token, args.pr_number)
        comments = github_get(
            f"/repos/{args.owner}/{args.repo}/pulls/{pr_number}/comments?per_page=100",
            token,
        )
        reviews = github_get(
            f"/repos/{args.owner}/{args.repo}/pulls/{pr_number}/reviews?per_page=100",
            token,
        )
    except urllib.error.HTTPError as exc:
        return fail(f"GitHub API error: {exc.code} {exc.reason}")
    except Exception as exc:
        return fail(f"Failed to fetch PR review feedback: {exc}")

    output = {
        "prNumber": pr_number,
        "comments": [normalize_comment(c) for c in comments or []],
        "reviews": [normalize_review(r) for r in reviews or []],
    }
    log_step6_event(
        "review-comments-retrieved",
        pr_number=pr_number,
        metadata={"commentCount": len(output["comments"]), "reviewCount": len(output["reviews"])},
    )
    print(json.dumps(output, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
