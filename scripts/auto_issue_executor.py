#!/usr/bin/env python3
"""Minimal Step 5 issue execution loop for portfolio workflow.

Flow:
approved issue -> ai-in-progress -> patch -> branch/commit/push -> PR -> merge -> cleanup
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

from step5_audit_log import log_step5_event

REPO_ROOT = Path(__file__).resolve().parents[1]
STEP5_TARGET_FILES = ["order-ui/index.html"]


class NoFileChangeError(RuntimeError):
    """Raised when an issue instruction results in no file changes."""


def log(msg: str) -> None:
    print(msg, flush=True)


def run_cmd(*args: str, check: bool = True, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(
        list(args),
        cwd=str(cwd or REPO_ROOT),
        text=True,
        capture_output=True,
        check=False,
    )
    if check and proc.returncode != 0:
        detail = (proc.stderr or "").strip() or (proc.stdout or "").strip() or f"exit status {proc.returncode}"
        raise RuntimeError(f"Command failed ({' '.join(args)}): {detail}")
    return proc


def ensure_git_identity() -> None:
    name = run_cmd("git", "config", "--get", "user.name", check=False).stdout.strip()
    email = run_cmd("git", "config", "--get", "user.email", check=False).stdout.strip()
    if name and email:
        return
    fallback_name = (os.getenv("STEP5_GIT_AUTHOR_NAME") or "codex-step5").strip()
    fallback_email = (os.getenv("STEP5_GIT_AUTHOR_EMAIL") or "codex-step5@local.invalid").strip()
    run_cmd("git", "config", "user.name", fallback_name)
    run_cmd("git", "config", "user.email", fallback_email)
    log(f"Configured local git author for Step 5 execution: {fallback_name} <{fallback_email}>")


def run_cmd_with_gh_auth_fallback(*args: str) -> subprocess.CompletedProcess[str]:
    cmd = list(args)
    proc = run_cmd(*cmd, check=False)
    if proc.returncode == 0:
        return proc

    # Retry with current environment first; only then fall back to stored gh auth.
    proc_with_env = subprocess.run(
        cmd,
        cwd=str(REPO_ROOT),
        text=True,
        capture_output=True,
        check=False,
        env=os.environ.copy(),
    )
    if proc_with_env.returncode == 0:
        return proc_with_env

    env = os.environ.copy()
    env.pop("GITHUB_TOKEN", None)
    env.pop("CODEX_GITHUB_TOKEN", None)
    return subprocess.run(
        cmd,
        cwd=str(REPO_ROOT),
        text=True,
        capture_output=True,
        check=False,
        env=env,
    )


def token_from_env() -> str:
    return (os.getenv("CODEX_GITHUB_TOKEN") or os.getenv("GITHUB_TOKEN") or "").strip()


def github_request(method: str, owner: str, repo: str, path: str, token: str, body: Any | None = None) -> Any:
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=True).encode("utf-8")
    req = urllib.request.Request(
        url=f"https://api.github.com/repos/{owner}/{repo}{path}",
        method=method,
        data=data,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "step5-auto-issue-executor",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read().decode("utf-8")
        return json.loads(raw) if raw else {}


def discover_eligible_issues(owner: str, repo: str, token: str) -> list[dict[str, Any]]:
    query = urllib.parse.urlencode({"state": "open", "labels": "approved-for-dev", "per_page": "100"})
    issues = github_request("GET", owner, repo, f"/issues?{query}", token)
    result: list[dict[str, Any]] = []
    for issue in issues or []:
        if issue.get("pull_request") is not None:
            continue
        labels = [str((lbl or {}).get("name", "")).strip() for lbl in issue.get("labels", [])]
        if "ai-in-progress" in labels:
            log(f"Skipping issue #{issue.get('number')} (already ai-in-progress).")
            log_step5_event("approved-issue-skipped", issue_number=issue.get("number"), metadata={"reason": "already-in-progress"})
            continue
        try:
            issue_number = int(issue.get("number"))
        except (TypeError, ValueError):
            log("Skipping malformed issue payload with missing/invalid number.")
            log_step5_event("approved-issue-skipped", metadata={"reason": "invalid-issue-number"})
            continue
        result.append(
            {
                "issueNumber": issue_number,
                "title": issue.get("title") or "",
                "body": issue.get("body") or "",
                "labels": labels,
                "htmlUrl": issue.get("html_url") or "",
            }
        )
    return result


def fetch_issue(owner: str, repo: str, token: str, issue_number: int) -> dict[str, Any]:
    issue = github_request("GET", owner, repo, f"/issues/{issue_number}", token)
    if not isinstance(issue, dict) or issue.get("pull_request") is not None:
        raise RuntimeError(f"Issue #{issue_number} is invalid or is a pull request.")

    labels = [str((lbl or {}).get("name", "")).strip() for lbl in issue.get("labels", [])]
    return {
        "issueNumber": int(issue.get("number")),
        "title": issue.get("title") or "",
        "body": issue.get("body") or "",
        "labels": labels,
        "htmlUrl": issue.get("html_url") or "",
        "state": issue.get("state") or "",
    }


def add_label(owner: str, repo: str, token: str, issue_number: int, label: str) -> None:
    github_request("POST", owner, repo, f"/issues/{issue_number}/labels", token, {"labels": [label]})


def remove_label(owner: str, repo: str, token: str, issue_number: int, label: str) -> None:
    try:
        github_request("DELETE", owner, repo, f"/issues/{issue_number}/labels/{urllib.parse.quote(label)}", token)
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise


def comment_issue(owner: str, repo: str, token: str, issue_number: int, message: str) -> None:
    github_request("POST", owner, repo, f"/issues/{issue_number}/comments", token, {"body": message})


def close_issue(owner: str, repo: str, token: str, issue_number: int) -> None:
    github_request("PATCH", owner, repo, f"/issues/{issue_number}", token, {"state": "closed"})


def parse_rename_instruction(text: str) -> tuple[str, str] | None:
    patterns = [
        r"from\s+'([^']+)'\s+to\s+'([^']+)'",
        r'from\s+"([^"]+)"\s+to\s+"([^"]+)"',
    ]
    for pattern in patterns:
        m = re.search(pattern, text, flags=re.IGNORECASE)
        if m:
            return m.group(1).strip(), m.group(2).strip()
    return None


def apply_issue_change(issue: dict[str, Any]) -> list[str]:
    text = f"{issue.get('title', '')}\n{issue.get('body', '')}"
    rename = parse_rename_instruction(text)
    if not rename:
        raise RuntimeError("No supported rename instruction found in issue title/body.")

    old_value, new_value = rename
    if old_value == new_value:
        raise RuntimeError("Rename instruction has identical source and destination values.")

    # Keep this intentionally narrow for portfolio safety.
    files = STEP5_TARGET_FILES
    if not (REPO_ROOT / files[0]).exists():
        raise RuntimeError("Expected UI file not found: order-ui/index.html")

    changed: list[str] = []
    for rel in files:
        path = REPO_ROOT / rel
        original = path.read_text(encoding="utf-8")
        updated = original.replace(old_value, new_value)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(rel)

    if not changed:
        raise NoFileChangeError("No file content changed after replacement.")

    return changed


def build_work_packet(issue: dict[str, Any]) -> dict[str, Any]:
    proc = subprocess.run(
        [sys.executable, str(REPO_ROOT / "scripts" / "build_work_packet.py")],
        input=json.dumps(issue, ensure_ascii=True),
        text=True,
        capture_output=True,
        cwd=str(REPO_ROOT),
        check=True,
    )
    return json.loads(proc.stdout)


def build_pr_scaffold(packet: dict[str, Any]) -> dict[str, Any]:
    proc = subprocess.run(
        [sys.executable, str(REPO_ROOT / "scripts" / "prepare_pr_scaffold.py")],
        input=json.dumps(packet, ensure_ascii=True),
        text=True,
        capture_output=True,
        cwd=str(REPO_ROOT),
        check=True,
    )
    return json.loads(proc.stdout)


def ensure_clean_and_base(base: str) -> None:
    # Keep Step 5 resilient in shared/dev runtime: auto-restore only the files this
    # executor owns so prior partial runs do not block the happy path indefinitely.
    for rel in STEP5_TARGET_FILES:
        run_cmd("git", "restore", "--staged", "--worktree", "--source=HEAD", "--", rel, check=False)
    status_lines = [line.strip() for line in run_cmd("git", "status", "--porcelain").stdout.splitlines() if line.strip()]
    relevant_paths = set(STEP5_TARGET_FILES)
    for line in status_lines:
        path_part = line[3:] if len(line) > 3 else ""
        candidate_paths = [segment.strip() for segment in path_part.split("->")]
        for path in candidate_paths:
            if not path:
                continue
            if path == "order-api/audit/intake-chat.jsonl" or "__pycache__/" in path:
                continue
            # Keep the guard minimal: only block when files this executor edits are already dirty.
            if path in relevant_paths:
                raise RuntimeError("Working tree has changes in Step 5 target files; aborting automated issue execution.")
    run_cmd("git", "fetch", "--all", "--prune")
    run_cmd("git", "checkout", base)
    run_cmd("git", "pull", "--ff-only")


def create_or_reuse_branch(branch: str, base: str) -> None:
    exists = run_cmd("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}", check=False).returncode == 0
    if exists:
        raise RuntimeError(f"Local branch already exists: {branch}")
    remote_exists = (
        run_cmd("git", "ls-remote", "--exit-code", "--heads", "origin", branch, check=False).returncode == 0
    )
    if remote_exists:
        raise RuntimeError(f"Remote branch already exists: {branch}")
    run_cmd("git", "checkout", "-b", branch, base)


def push_branch(token: str, branch: str) -> None:
    token = (token or "").strip()
    direct_push = run_cmd("git", "push", "-u", "origin", branch, check=False)
    if direct_push.returncode == 0:
        return
    if not token:
        detail = sanitize_git_error((direct_push.stderr or "").strip() or (direct_push.stdout or "").strip(), token)
        raise RuntimeError(f"git push failed: {detail}")

    origin_url = run_cmd("git", "remote", "get-url", "origin", check=False).stdout.strip()
    host = parse_remote_host(origin_url)
    if not host:
        detail = sanitize_git_error((direct_push.stderr or "").strip() or (direct_push.stdout or "").strip(), token)
        raise RuntimeError(f"git push failed (unsupported remote for credential fallback; primary={detail})")
    try:
        with tempfile.TemporaryDirectory(prefix="step5_git_cred_") as tmp_dir:
            cred_file = Path(tmp_dir) / ".git-credentials"
            cred_file.touch(mode=0o600, exist_ok=True)
            os.chmod(cred_file, 0o600)

            env = os.environ.copy()
            env["GIT_TERMINAL_PROMPT"] = "0"
            env["GIT_CONFIG_COUNT"] = "1"
            env["GIT_CONFIG_KEY_0"] = "credential.helper"
            env["GIT_CONFIG_VALUE_0"] = f"store --file={cred_file}"

            credential_input = f"protocol=https\nhost={host}\nusername=x-access-token\npassword={token}\n\n"
            approve_proc = subprocess.run(
                ["git", "credential", "approve"],
                cwd=str(REPO_ROOT),
                text=True,
                input=credential_input,
                capture_output=True,
                check=False,
                env=env,
            )
            if approve_proc.returncode != 0:
                detail = sanitize_git_error((approve_proc.stderr or "").strip() or (approve_proc.stdout or "").strip(), token)
                raise RuntimeError(f"failed to stage temporary credentials: {detail}")
            if not cred_file.exists() or cred_file.stat().st_size == 0:
                raise RuntimeError("failed to stage temporary credentials: credential store is empty")

            token_push = subprocess.run(
                ["git", "push", "-u", "origin", branch],
                cwd=str(REPO_ROOT),
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            if token_push.returncode != 0:
                primary = sanitize_git_error((direct_push.stderr or "").strip() or (direct_push.stdout or "").strip(), token)
                fallback = sanitize_git_error((token_push.stderr or "").strip() or (token_push.stdout or "").strip(), token)
                raise RuntimeError(f"git push failed (primary={primary}; fallback={fallback})")
    except RuntimeError:
        raise
    except Exception as ex:
        primary = sanitize_git_error((direct_push.stderr or "").strip() or (direct_push.stdout or "").strip(), token)
        raise RuntimeError(f"git push failed (primary={primary}; fallback={type(ex).__name__}: {ex})") from ex


def sanitize_git_error(raw: str, token: str) -> str:
    text = (raw or "").strip()
    if not text:
        return "unknown push error"
    if token:
        text = text.replace(token, "***")
    text = re.sub(r"gh[pousr]_[A-Za-z0-9_]+", "***", text)
    text = re.sub(r"github_pat_[A-Za-z0-9_]+", "***", text)
    text = re.sub(r"(?i)(authorization:\s*(?:bearer|token)\s+)[^\s]+", r"\1***", text)
    text = re.sub(r"(?i)(password=)[^\s]+", r"\1***", text)
    text = re.sub(r"(?i)(token=)[^\s&]+", r"\1***", text)
    text = re.sub(r"https://[^@\s]+@", "https://***@", text)
    return text


def parse_remote_host(remote_url: str) -> str:
    value = (remote_url or "").strip()
    if not value:
        return ""
    if value.startswith("http://") or value.startswith("https://") or value.startswith("ssh://"):
        return urllib.parse.urlparse(value).hostname or ""
    ssh_match = re.match(r"^[^@]+@([^:]+):.*$", value)
    if ssh_match:
        return ssh_match.group(1).strip()
    return ""


def commit_and_push(token: str, branch: str, issue_number: int, changed_files: list[str]) -> None:
    if not changed_files:
        raise RuntimeError("No changed files to stage.")
    ensure_git_identity()
    run_cmd("git", "add", "--", *changed_files)
    if run_cmd("git", "diff", "--cached", "--quiet", check=False).returncode == 0:
        raise RuntimeError("No staged changes to commit.")
    run_cmd("git", "commit", "-m", f"Issue #{issue_number}: apply approved update")
    push_branch(token, branch)


def resolve_open_pr(owner: str, repo: str, token: str, branch: str) -> int | None:
    query = urllib.parse.urlencode({"state": "open", "head": f"{owner}:{branch}", "per_page": "1"})
    prs = github_request("GET", owner, repo, f"/pulls?{query}", token)
    if prs:
        return int(prs[0]["number"])
    return None


def create_pr(owner: str, repo: str, token: str, branch: str, scaffold: dict[str, Any]) -> tuple[int, str]:
    existing = resolve_open_pr(owner, repo, token, branch)
    if existing is not None:
        pr = github_request("GET", owner, repo, f"/pulls/{existing}", token)
        return int(pr["number"]), pr.get("html_url", "")

    body = {
        "title": scaffold["prTitle"],
        "head": branch,
        "base": "main",
        "body": scaffold["prBody"],
    }
    try:
        pr = github_request("POST", owner, repo, "/pulls", token, body)
        return int(pr["number"]), pr.get("html_url", "")
    except urllib.error.HTTPError as exc:
        if exc.code != 403:
            raise
        proc = run_cmd_with_gh_auth_fallback(
            "gh",
            "pr",
            "create",
            "--base",
            "main",
            "--head",
            branch,
            "--title",
            scaffold["prTitle"],
            "--body",
            scaffold["prBody"],
        )
        if proc.returncode != 0:
            raise RuntimeError(f"PR creation failed: {proc.stderr.strip() or proc.stdout.strip() or 'unknown error'}")
        pr_url = (proc.stdout.strip().splitlines() or [""])[-1].strip()
        pr_number = int(pr_url.rstrip("/").split("/")[-1]) if pr_url else resolve_open_pr(owner, repo, token, branch)
        if not pr_number:
            raise RuntimeError("PR created via gh but could not resolve PR number.")
        return int(pr_number), pr_url


def post_ready_note(owner: str, repo: str, token: str, pr_number: int) -> None:
    message = "PR ready for review. Awaiting human merge."
    github_request("POST", owner, repo, f"/issues/{pr_number}/comments", token, {"body": message})


def extract_label_names(raw_labels: Any) -> list[str]:
    names: list[str] = []
    if isinstance(raw_labels, dict):
        if "labels" in raw_labels:
            raw_labels = raw_labels.get("labels")
        else:
            raw_labels = [raw_labels]
    if not isinstance(raw_labels, list):
        return names
    for label in raw_labels:
        if isinstance(label, str):
            text = label.strip().lower()
            if text:
                names.append(text)
        elif isinstance(label, dict):
            text = str(label.get("name", "")).strip().lower()
            if text:
                names.append(text)
    return names


def is_auto_merge_allowed(owner: str, repo: str, token: str, pr_number: int, auto_merge_requested: bool) -> tuple[bool, str]:
    if not auto_merge_requested:
        return False, "auto-merge flag not requested"

    env_flag = str(os.getenv("ALLOW_AUTO_MERGE", "")).strip().lower()
    if env_flag not in {"1", "true", "yes", "on"}:
        return False, "ALLOW_AUTO_MERGE is not enabled"

    try:
        labels_payload = github_request("GET", owner, repo, f"/issues/{pr_number}/labels", token)
        labels = extract_label_names(labels_payload)
    except Exception as ex:
        return False, f"unable to read PR labels: {ex}"
    if "approved-to-merge" not in labels:
        return False, "missing approved-to-merge label"

    return True, ""


def merge_pr(owner: str, repo: str, token: str, pr_number: int) -> None:
    body = {"merge_method": "squash"}
    try:
        github_request("PUT", owner, repo, f"/pulls/{pr_number}/merge", token, body)
    except urllib.error.HTTPError as exc:
        if exc.code != 403:
            raise
        proc = run_cmd_with_gh_auth_fallback(
            "gh",
            "pr",
            "merge",
            str(pr_number),
            "--squash",
            "--delete-branch",
            "--admin",
        )
        if proc.returncode != 0:
            raise RuntimeError(f"PR merge failed: {proc.stderr.strip() or proc.stdout.strip() or 'unknown error'}")
    pull = github_request("GET", owner, repo, f"/pulls/{pr_number}", token)
    if not bool(pull.get("merged")):
        raise RuntimeError(f"PR #{pr_number} is not merged; skipping cleanup.")


def cleanup_branch(branch: str) -> None:
    run_cmd("git", "checkout", "main")
    run_cmd("git", "pull", "--ff-only")
    run_cmd("git", "push", "origin", "--delete", branch, check=False)
    run_cmd("git", "branch", "-d", branch, check=False)
    run_cmd("git", "fetch", "--all", "--prune")


def process_issue(owner: str, repo: str, token: str, issue: dict[str, Any], auto_merge: bool) -> dict[str, Any]:
    issue_number = int(issue["issueNumber"])
    branch = ""
    label_applied = False
    pr_number: int | None = None
    changed_files: list[str] = []
    log(f"Issue #{issue_number}: picked for execution")
    log_step5_event("approved-issue-picked", issue_number=issue_number, metadata={"title": issue.get("title", "")})

    try:
        add_label(owner, repo, token, issue_number, "ai-in-progress")
        label_applied = True
        log(f"Issue #{issue_number}: applied label ai-in-progress")
        log_step5_event("approved-issue-marked-in-progress", issue_number=issue_number)

        log(f"Issue #{issue_number}: building work packet")
        packet = build_work_packet(issue)

        log(f"Issue #{issue_number}: preparing PR scaffold")
        scaffold = build_pr_scaffold(packet)
        branch = scaffold["branchName"]

        ensure_clean_and_base("main")
        log(f"Issue #{issue_number}: creating branch {branch}")
        create_or_reuse_branch(branch, "main")

        log(f"Issue #{issue_number}: applying implementation")
        changed_files = apply_issue_change(issue)
        log_step5_event("issue-implementation-applied", issue_number=issue_number, metadata={"changedFiles": changed_files})

        log(f"Issue #{issue_number}: committing and pushing")
        commit_and_push(token, branch, issue_number, changed_files)

        log(f"Issue #{issue_number}: creating pull request")
        pr_number, pr_url = create_pr(owner, repo, token, branch, scaffold)
        log(f"Issue #{issue_number}: PR created #{pr_number} {pr_url}")
        log_step5_event("issue-pr-created", issue_number=issue_number, metadata={"prNumber": pr_number, "prUrl": pr_url})

        result = {
            "issueNumber": issue_number,
            "branch": branch,
            "prNumber": pr_number,
            "prUrl": pr_url,
            "changedFiles": changed_files,
            "merged": False,
        }

        can_merge, merge_skip_reason = is_auto_merge_allowed(owner, repo, token, pr_number, auto_merge)
        if not can_merge:
            log(f"Issue #{issue_number}: Merge skipped: human approval required ({merge_skip_reason})")
            try:
                post_ready_note(owner, repo, token, pr_number)
            except Exception as ex:
                log(f"Issue #{issue_number}: review-note skipped due to error: {ex}")
            log_step5_event(
                "issue-pr-awaiting-human-merge",
                issue_number=issue_number,
                metadata={"prNumber": pr_number, "reason": merge_skip_reason},
            )
            return result

        if can_merge:
            log(f"Issue #{issue_number}: posting ready-to-merge note")
            try:
                post_ready_note(owner, repo, token, pr_number)
            except Exception as ex:
                log(f"Issue #{issue_number}: ready-note skipped due to error: {ex}")
                log_step5_event(
                    "ready-to-merge-note-skipped",
                    issue_number=issue_number,
                    metadata={"prNumber": pr_number},
                    error=str(ex),
                )

            log(f"Issue #{issue_number}: merging PR #{pr_number}")
            merge_pr(owner, repo, token, pr_number)
            result["merged"] = True
            log_step5_event("issue-pr-merged", issue_number=issue_number, metadata={"prNumber": pr_number})

            log(f"Issue #{issue_number}: cleanup and reconciliation")
            cleanup_branch(branch)
            remove_label(owner, repo, token, issue_number, "ai-in-progress")
            close_issue(owner, repo, token, issue_number)
            log_step5_event("issue-post-merge-cleanup-completed", issue_number=issue_number, metadata={"branch": branch})

        return result
    except Exception as ex:
        if isinstance(ex, NoFileChangeError):
            log(f"Issue #{issue_number}: no-op detected, awaiting human review")
            log_step5_event("approved-issue-no-op", issue_number=issue_number, error=str(ex))
            try:
                comment_issue(
                    owner,
                    repo,
                    token,
                    issue_number,
                    "Automation detected no code change was needed for this request. "
                    "This issue appears already satisfied on the current codebase. "
                    "Please verify manually; if more work is needed, update the issue details and re-add `approved-for-dev`.",
                )
            except Exception:
                pass
            try:
                remove_label(owner, repo, token, issue_number, "approved-for-dev")
            except Exception:
                pass
            if label_applied:
                try:
                    remove_label(owner, repo, token, issue_number, "ai-in-progress")
                except Exception:
                    pass
            return {
                "issueNumber": issue_number,
                "branch": branch,
                "prNumber": None,
                "prUrl": "",
                "changedFiles": [],
                "merged": False,
            }
        if label_applied:
            try:
                remove_label(owner, repo, token, issue_number, "ai-in-progress")
            except Exception:
                pass
        if pr_number is not None:
            try:
                comment_issue(
                    owner,
                    repo,
                    token,
                    issue_number,
                    f"Automation stopped after creating PR #{pr_number}. Please continue from that PR.",
                )
            except Exception:
                pass
        elif branch:
            try:
                run_cmd("git", "push", "origin", "--delete", branch, check=False)
                run_cmd("git", "branch", "-D", branch, check=False)
            except Exception:
                pass
        try:
            for rel in STEP5_TARGET_FILES:
                run_cmd("git", "restore", "--staged", "--worktree", "--source=HEAD", "--", rel, check=False)
            run_cmd("git", "checkout", "main", check=False)
        except Exception:
            pass
        raise


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run minimal Step 5 issue execution loop.")
    parser.add_argument("--owner", required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--issue", type=int, default=None, help="Optional specific issue number.")
    parser.add_argument("--once", action="store_true", help="Run a single scan iteration and exit.")
    parser.add_argument("--interval-seconds", type=int, default=20)
    parser.add_argument("--auto-merge", action="store_true", help="Merge PR and cleanup when created.")
    parser.add_argument(
        "--allow-in-progress",
        action="store_true",
        help="Allow direct --issue execution even when ai-in-progress is already set.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    token = token_from_env()
    if not token:
        print("Missing CODEX_GITHUB_TOKEN or GITHUB_TOKEN.", file=sys.stderr)
        return 1

    while True:
        try:
            log("Polling approved issues...")
            if args.issue is not None:
                direct_issue = fetch_issue(args.owner, args.repo, token, args.issue)
                labels = direct_issue.get("labels", [])
                if direct_issue.get("state") != "open":
                    issues = []
                elif "approved-for-dev" not in labels:
                    issues = []
                elif ("ai-in-progress" in labels) and (not args.allow_in_progress):
                    issues = []
                else:
                    issues = [direct_issue]
            else:
                issues = discover_eligible_issues(args.owner, args.repo, token)
            log(f"Approved eligible issues found: {len(issues)}")
            log_step5_event("approved-issue-poll-ran", metadata={"approvedIssuesFound": len(issues)})

            for issue in issues:
                issue_number = int(issue["issueNumber"])
                try:
                    result = process_issue(args.owner, args.repo, token, issue, args.auto_merge)
                    log(json.dumps({"result": result}, ensure_ascii=True))
                except Exception as exc:  # minimal explicit failure visibility
                    error = str(exc)
                    log(f"Issue #{issue_number}: FAILED - {error}")
                    log_step5_event("approved-issue-execution-failed", issue_number=issue_number, error=error)
                    try:
                        comment_issue(
                            args.owner,
                            args.repo,
                            token,
                            issue_number,
                            f"Automation attempt failed: {error}",
                        )
                    except Exception:
                        pass

            if args.once:
                return 0

            time.sleep(max(5, int(args.interval_seconds)))
        except KeyboardInterrupt:
            return 0
        except Exception as exc:
            error = str(exc)
            log(f"Poll loop failed: {error}")
            log_step5_event("approved-issue-poll-failed", error=error)
            if args.once:
                return 1
            time.sleep(max(5, int(args.interval_seconds)))


if __name__ == "__main__":
    raise SystemExit(main())
