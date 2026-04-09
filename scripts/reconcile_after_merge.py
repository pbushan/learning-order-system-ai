#!/usr/bin/env python3
"""Minimal post-merge cleanup and local/remote reconciliation helper."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys

from step6_audit_log import log_step6_event


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=check)


def current_branch() -> str:
    return run("git", "rev-parse", "--abbrev-ref", "HEAD").stdout.strip()


def branch_exists_local(branch: str) -> bool:
    return run("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}", check=False).returncode == 0


def remote_branch_exists(remote: str, branch: str) -> bool:
    return run("git", "show-ref", "--verify", "--quiet", f"refs/remotes/{remote}/{branch}", check=False).returncode == 0


def is_ancestor(ancestor_ref: str, descendant_ref: str) -> bool:
    return run(
        "git",
        "merge-base",
        "--is-ancestor",
        ancestor_ref,
        descendant_ref,
        check=False,
    ).returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Reconcile local/remote state after merge.")
    parser.add_argument("--branch", required=True, help="Working branch to clean up.")
    parser.add_argument("--base", default="main", help="Base branch to reconcile (default: main).")
    parser.add_argument("--remote", default="origin", help="Remote name (default: origin).")
    args = parser.parse_args()

    result = {"branch": args.branch, "base": args.base, "remote": args.remote, "merged": False, "cleaned": False}
    try:
        run("git", "fetch", "--all", "--prune")
        if remote_branch_exists(args.remote, args.base):
            run("git", "checkout", "-B", args.base, f"{args.remote}/{args.base}")
        else:
            run("git", "checkout", args.base)
        run("git", "pull", "--ff-only")

        remote_branch_ref = f"refs/remotes/{args.remote}/{args.branch}"
        remote_base_ref = f"refs/remotes/{args.remote}/{args.base}"
        local_branch_ref = f"refs/heads/{args.branch}"
        local_base_ref = f"refs/heads/{args.base}"

        if (
            remote_branch_exists(args.remote, args.branch)
            and remote_branch_exists(args.remote, args.base)
            and is_ancestor(remote_branch_ref, remote_base_ref)
        ):
            result["merged"] = True
            if args.branch != args.base:
                run("git", "push", args.remote, "--delete", args.branch, check=False)

        local_branch_is_merged = (
            branch_exists_local(args.branch)
            and branch_exists_local(args.base)
            and is_ancestor(local_branch_ref, local_base_ref)
        )

        if args.branch != args.base and branch_exists_local(args.branch) and (result["merged"] or local_branch_is_merged):
            if current_branch() == args.branch:
                run("git", "checkout", args.base)
            deleted = run("git", "branch", "-d", args.branch, check=False).returncode == 0
            result["cleaned"] = deleted

        status = run("git", "status", "--short", "--branch").stdout.strip()
        result["status"] = status
        log_step6_event(
            "post-merge-reconciliation-completed",
            metadata={"branch": args.branch, "base": args.base, "merged": result["merged"], "cleaned": result["cleaned"]},
        )
        print(json.dumps(result, ensure_ascii=True))
        return 0
    except subprocess.CalledProcessError as exc:
        error = exc.stderr.strip() or exc.stdout.strip() or str(exc)
        log_step6_event(
            "post-merge-reconciliation-completed",
            metadata={"branch": args.branch, "base": args.base},
            error=error,
        )
        print(
            json.dumps(
                {
                    "branch": args.branch,
                    "base": args.base,
                    "error": error,
                },
                ensure_ascii=True,
            ),
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
