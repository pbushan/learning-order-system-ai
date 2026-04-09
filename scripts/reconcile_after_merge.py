#!/usr/bin/env python3
"""Minimal post-merge cleanup and local/remote reconciliation helper."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=check)


def current_branch() -> str:
    return run("git", "rev-parse", "--abbrev-ref", "HEAD").stdout.strip()


def branch_exists_local(branch: str) -> bool:
    return run("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}", check=False).returncode == 0


def branch_exists_remote(branch: str) -> bool:
    return run("git", "ls-remote", "--heads", "origin", branch, check=False).stdout.strip() != ""


def branch_merged_into_base(branch: str, base: str) -> bool:
    return run(
        "git",
        "merge-base",
        "--is-ancestor",
        f"origin/{branch}",
        f"origin/{base}",
        check=False,
    ).returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Reconcile local/remote state after merge.")
    parser.add_argument("--branch", required=True, help="Working branch to clean up.")
    parser.add_argument("--base", default="main", help="Base branch to reconcile (default: main).")
    args = parser.parse_args()

    result = {"branch": args.branch, "base": args.base, "merged": False, "cleaned": False}
    try:
        run("git", "fetch", "--all", "--prune")
        run("git", "checkout", args.base)
        run("git", "pull", "--ff-only")

        if branch_exists_remote(args.branch) and branch_merged_into_base(args.branch, args.base):
            result["merged"] = True
            run("git", "push", "origin", "--delete", args.branch, check=False)
        elif not branch_exists_remote(args.branch):
            result["merged"] = True

        if branch_exists_local(args.branch):
            if current_branch() == args.branch:
                run("git", "checkout", args.base)
            run("git", "branch", "-D", args.branch)
            result["cleaned"] = True

        status = run("git", "status", "--short", "--branch").stdout.strip()
        result["status"] = status
        print(json.dumps(result, ensure_ascii=True))
        return 0
    except subprocess.CalledProcessError as exc:
        print(
            json.dumps(
                {
                    "branch": args.branch,
                    "base": args.base,
                    "error": exc.stderr.strip() or exc.stdout.strip() or str(exc),
                },
                ensure_ascii=True,
            ),
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
