#!/usr/bin/env python3
"""Minimal Step 5 issue execution loop for portfolio workflow.

Flow:
approved issue -> ai-in-progress -> patch -> branch/commit/push -> PR -> merge -> cleanup
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
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


class UnsupportedInstructionError(RuntimeError):
    """Raised when an issue is valid but outside the minimal Step 5 auto-fix scope."""


class GitHubPermissionError(RuntimeError):
    """Raised when token permissions are insufficient for Step 5 write operations."""


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
    env.pop("APP_GITHUB_TOKEN", None)
    return subprocess.run(
        cmd,
        cwd=str(REPO_ROOT),
        text=True,
        capture_output=True,
        check=False,
        env=env,
    )


def token_from_env() -> str:
    return (os.getenv("APP_GITHUB_TOKEN") or "").strip()


def mcp_base_url() -> str:
    return (os.getenv("APP_GITHUB_MCP_BASE_URL") or "http://github-mcp:8082").rstrip("/")


def repository_from_env() -> tuple[str, str]:
    value = (os.getenv("APP_GITHUB_REPOSITORY") or "").strip()
    if not value or "/" not in value:
        return "", ""
    owner, repo = value.split("/", 1)
    return owner.strip(), repo.strip()


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
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        raw = ""
        try:
            raw = exc.read().decode("utf-8", errors="replace")
        except Exception:
            raw = ""
        detail = ""
        parsed_error: dict[str, Any] = {}
        if raw:
            try:
                parsed_candidate = json.loads(raw)
                if isinstance(parsed_candidate, dict):
                    parsed_error = parsed_candidate
                detail = str(parsed_error.get("message") or "").strip()
            except Exception:
                detail = raw.strip()
        if exc.code == 403:
            forbidden_type = classify_github_forbidden(detail, parsed_error, dict(exc.headers.items()) if exc.headers else {})
            if forbidden_type == "permission":
                raise GitHubPermissionError(
                    "APP_GITHUB_TOKEN lacks required write permissions for this operation. "
                    "Grant fine-grained token access to this repository with Contents: Read and write, "
                    "Pull requests: Read and write, Issues: Read and write, Metadata: Read."
                ) from exc
            if forbidden_type == "transient":
                suffix = f": {detail}" if detail else ""
                raise RuntimeError(f"GitHub API {method} {path} temporarily denied request (403){suffix}") from exc
        suffix = f": {detail}" if detail else ""
        raise RuntimeError(f"GitHub API {method} {path} failed with {exc.code}{suffix}") from exc


def is_permission_blocked_message(detail: str) -> bool:
    lower = (detail or "").lower()
    return any(
        marker in lower
        for marker in [
            "resource not accessible by personal access token",
            "resource not accessible by integration",
            "insufficient permission",
            "permission to",
            "write access to repository not granted",
            "must have admin rights",
        ]
    )


def classify_github_forbidden(detail: str, parsed: dict[str, Any], headers: dict[str, str]) -> str:
    lower = (detail or "").lower()
    if is_permission_blocked_message(detail):
        return "permission"

    documentation_url = str(parsed.get("documentation_url") or "").lower()
    errors = parsed.get("errors")
    errors_text = json.dumps(errors, ensure_ascii=True).lower() if isinstance(errors, list) else ""
    header_map = {str(k).lower(): str(v).lower() for k, v in (headers or {}).items()}
    rate_remaining = header_map.get("x-ratelimit-remaining", "")

    transient_markers = [
        "secondary rate limit",
        "abuse detection",
        "temporarily blocked",
        "saml",
        "sso",
        "rate limit",
    ]
    combined = " ".join([lower, documentation_url, errors_text])
    if rate_remaining == "0":
        return "transient"
    if any(marker in combined for marker in transient_markers):
        return "transient"
    return "unknown"


def github_repo_details(owner: str, repo: str, token: str) -> dict[str, Any]:
    req = urllib.request.Request(
        url=f"https://api.github.com/repos/{owner}/{repo}",
        method="GET",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "step5-auto-issue-executor",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        raw = ""
        try:
            raw = exc.read().decode("utf-8", errors="replace")
        except Exception:
            raw = ""
        detail = ""
        if raw:
            try:
                parsed = json.loads(raw)
                detail = str(parsed.get("message") or "").strip()
            except Exception:
                detail = raw.strip()
        suffix = f": {detail}" if detail else ""
        raise RuntimeError(f"GitHub API GET repository details failed with {exc.code}{suffix}") from exc


def validate_repo_permissions(owner: str, repo: str, token: str) -> None:
    details = github_repo_details(owner, repo, token)
    permissions = details.get("permissions") if isinstance(details, dict) else None
    role_name = str(details.get("role_name") or "").strip().lower() if isinstance(details, dict) else ""
    if role_name in {"read", "triage"}:
        raise GitHubPermissionError(
            "APP_GITHUB_TOKEN repository role does not allow writes. "
            "Grant repository write access and Contents: Read and write."
        )
    if not isinstance(permissions, dict):
        # Some fine-grained token contexts do not expose this field reliably.
        # Continue and let write operations surface explicit permission failures.
        log("Step 5 permission precheck: repository permissions payload unavailable; proceeding with runtime write checks.")
        return
    if not bool(permissions.get("push")):
        raise GitHubPermissionError(
            "APP_GITHUB_TOKEN does not have push permission for repository contents. "
            "Grant Contents: Read and write for this repo."
        )


def mcp_request(url: str, token: str, body: dict[str, Any], session_id: str | None = None) -> tuple[dict[str, str], str]:
    data = json.dumps(body, ensure_ascii=True).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
        "Authorization": f"Bearer {token}",
    }
    if session_id:
        headers["Mcp-Session-Id"] = session_id

    req = urllib.request.Request(url=url, method="POST", data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read().decode("utf-8")
        return dict(resp.headers.items()), raw


def parse_mcp_envelope(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if not text:
        raise RuntimeError("empty MCP response body")
    if text.startswith("{"):
        return json.loads(text)
    for line in text.splitlines():
        if line.startswith("data: "):
            payload = line[len("data: "):].strip()
            if payload:
                return json.loads(payload)
    raise RuntimeError(f"unsupported MCP response format: {text[:200]}")


def extract_mcp_text(envelope: dict[str, Any]) -> str:
    result = envelope.get("result") if isinstance(envelope, dict) else None
    if not isinstance(result, dict):
        return ""
    content = result.get("content")
    if not isinstance(content, list) or not content:
        return ""
    first = content[0]
    if isinstance(first, dict):
        return str(first.get("text", "")).strip()
    return ""


def call_mcp_tool(tool_name: str, arguments: dict[str, Any], token: str) -> dict[str, Any]:
    base_url = mcp_base_url()
    endpoint = f"{base_url}/"
    last_error = ""
    delay = 1.0

    for attempt in range(1, 4):
        try:
            init_headers, _ = mcp_request(
                endpoint,
                token,
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "protocolVersion": "2025-03-26",
                        "capabilities": {},
                        "clientInfo": {"name": "step5-auto-issue-executor", "version": "1.0"},
                    },
                },
            )
            session_id = init_headers.get("Mcp-Session-Id") or init_headers.get("mcp-session-id")
            if not session_id:
                raise RuntimeError("MCP session initialization did not return Mcp-Session-Id")

            mcp_request(
                endpoint,
                token,
                {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized",
                    "params": {},
                },
                session_id=session_id,
            )

            _, tool_raw = mcp_request(
                endpoint,
                token,
                {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "tools/call",
                    "params": {"name": tool_name, "arguments": arguments},
                },
                session_id=session_id,
            )
            envelope = parse_mcp_envelope(tool_raw)
            result = envelope.get("result") if isinstance(envelope, dict) else None
            if isinstance(result, dict) and result.get("isError"):
                message = extract_mcp_text(envelope) or str(result)
                raise RuntimeError(message)
            if not isinstance(result, dict):
                raise RuntimeError(f"missing MCP result payload: {envelope}")
            return result
        except Exception as ex:
            last_error = str(ex)
            if attempt == 3:
                break
            time.sleep(delay)
            delay *= 2

    raise RuntimeError(f"MCP tool call failed ({tool_name}): {last_error}")


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
        r"rename\s+'([^']+)'\s+to\s+'([^']+)'",
        r'rename\s+"([^"]+)"\s+to\s+"([^"]+)"',
        r"spelling\s+of\s+'([^']+)'\s+to\s+'([^']+)'",
        r'spelling\s+of\s+"([^"]+)"\s+to\s+"([^"]+)"',
        r"correct(?:\s+the)?\s+spelling\s+of\s+'([^']+)'\s+to\s+'([^']+)'",
        r'correct(?:\s+the)?\s+spelling\s+of\s+"([^"]+)"\s+to\s+"([^"]+)"',
    ]
    for pattern in patterns:
        m = re.search(pattern, text, flags=re.IGNORECASE)
        if m:
            return m.group(1).strip(), m.group(2).strip()
    return None


def apply_issue_change(issue: dict[str, Any]) -> list[str]:
    title = str(issue.get("title") or "")
    text = f"{title}\n{issue.get('body', '')}"
    rename = parse_rename_instruction(title)
    if not rename:
        # Keep matching conservative: only look at explicit "from ... to ..." instructions in body.
        rename = parse_rename_instruction(str(issue.get("body") or ""))
    if not rename:
        title_lower = title.lower()
        if title_lower.startswith("locate the source") or "read-only code inspection" in text.lower():
            raise UnsupportedInstructionError(
                "Issue requests inspection/planning only; Step 5 auto-execution currently applies code edits only."
            )
        if title_lower.startswith("add or update automated ui tests") or "ui tests" in title_lower:
            raise UnsupportedInstructionError(
                "Issue requests broader test work outside the minimal Step 5 text-replace automation scope."
            )
        raise UnsupportedInstructionError("No supported rename instruction found in issue title/body.")

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
    remote_exists = (
        run_cmd("git", "ls-remote", "--exit-code", "--heads", "origin", branch, check=False).returncode == 0
    )
    if exists and remote_exists:
        run_cmd("git", "checkout", branch)
        run_cmd("git", "reset", "--hard", f"origin/{branch}")
        return
    if exists and not remote_exists:
        run_cmd("git", "checkout", branch)
        run_cmd("git", "reset", "--hard", base)
        return
    if remote_exists:
        run_cmd("git", "checkout", "-b", branch, "--track", f"origin/{branch}")
        return
    run_cmd("git", "checkout", "-b", branch, base)


def push_branch(token: str, branch: str) -> None:
    token = (token or "").strip()
    direct_push = run_cmd("git", "push", "-u", "origin", branch, check=False)
    direct_stdout = (direct_push.stdout or "").strip()
    direct_stderr = (direct_push.stderr or "").strip()
    direct_error_raw = "\n".join([part for part in [direct_stderr, direct_stdout] if part]).strip()
    direct_error = sanitize_git_error(direct_error_raw, token)
    if direct_push.returncode == 0:
        return
    if not token:
        raise GitHubPermissionError("APP_GITHUB_TOKEN is missing for git push auth fallback.")
    if is_known_non_auth_push_error(direct_error_raw):
        snippet = direct_error[:180]
        raise RuntimeError(f"git push failed: non-auth push rejection ({snippet})")
    if not is_auth_push_error(direct_error_raw):
        snippet = direct_error[:180]
        raise RuntimeError(f"git push failed: unsupported auth failure signature ({snippet})")

    origin_url = run_cmd("git", "remote", "get-url", "origin", check=False).stdout.strip()
    host_name = "github.com" if "github.com" in origin_url.lower() else ""
    if not host_name:
        raise RuntimeError("git push failed: unsupported remote for auth fallback")
    try:
        env = os.environ.copy()
        env["GIT_TERMINAL_PROMPT"] = "0"
        token_url = authenticated_remote_url(origin_url, "x-access-token", token)
        token_push = subprocess.run(
            ["git", "push", "-u", token_url, branch],
            cwd=str(REPO_ROOT),
            text=True,
            capture_output=True,
            check=False,
            env=env,
        )
        if token_push.returncode != 0:
            fallback_raw = "\n".join([token_push.stderr or "", token_push.stdout or ""]).strip()
            details = sanitize_git_error(fallback_raw, token)
            if is_known_non_auth_push_error(fallback_raw):
                raise RuntimeError(f"git push failed: non-auth push rejection ({details[:220]})")
            if is_auth_push_error(fallback_raw):
                raise GitHubPermissionError(
                    "APP_GITHUB_TOKEN cannot push branch updates from runtime (git transport denied). "
                    f"push failure details: {details[:220]}"
                )
            raise RuntimeError(f"git push failed: auth fallback push failed ({details[:220]})")
    except RuntimeError:
        raise
    except Exception as ex:
        raise RuntimeError(f"git push failed: fallback exception ({type(ex).__name__})") from ex


def sanitize_git_error(raw: str, token: str) -> str:
    text = (raw or "").strip()
    if not text:
        return "unknown push error"
    if token:
        text = text.replace(token, "***")
        text = text.replace(urllib.parse.quote(token, safe=""), "***")
        text = text.replace(urllib.parse.quote_plus(token), "***")
        basic = base64.b64encode(f"x-access-token:{token}".encode("utf-8")).decode("ascii")
        text = text.replace(basic, "***")
    text = re.sub(r"(?i)(x-access-token:)[^\s@]+", r"\1***", text)
    text = re.sub(r"gh[pousr]_[A-Za-z0-9_]+", "***", text)
    text = re.sub(r"github_pat_[A-Za-z0-9_]+", "***", text)
    text = re.sub(r"(?i)(authorization:\s*(?:bearer|token)\s+)[^\s]+", r"\1***", text)
    text = re.sub(r"(?i)(authorization:\s*basic\s+)[^\s]+", r"\1***", text)
    text = re.sub(r"(?i)(password=)[^\s]+", r"\1***", text)
    text = re.sub(r"(?i)(token=)[^\s&]+", r"\1***", text)
    text = re.sub(r"(?i)((?:access|oauth)_?token[=:])[^\s&]+", r"\1***", text)
    text = re.sub(r"(?i)([?&](?:access_token|token|auth|password)=)[^&\s]+", r"\1***", text)
    text = re.sub(r"https://[^@\s]+@", "https://***@", text)
    return text


def authenticated_remote_url(origin_url: str, username: str, token: str) -> str:
    parsed = urllib.parse.urlparse((origin_url or "").strip())
    if parsed.scheme != "https" or not parsed.netloc:
        raise RuntimeError("git push failed: unsupported remote URL for token push fallback")
    safe_user = urllib.parse.quote((username or "x-access-token"), safe="")
    safe_token = urllib.parse.quote(token, safe="")
    netloc = f"{safe_user}:{safe_token}@{parsed.netloc}"
    return urllib.parse.urlunparse((parsed.scheme, netloc, parsed.path, "", "", ""))


def is_auth_push_error(raw: str) -> bool:
    text = (raw or "").lower()
    return any(
        marker in text
        for marker in [
            "authentication failed",
            "could not read username",
            "invalid username or password",
            "http basic: access denied",
            "bad credentials",
            "authentication required",
            "requested url returned error: 401",
            "requested url returned error: 403",
            "permission to",
            "write access to repository not granted",
            "resource not accessible by personal access token",
        ]
    )


def is_known_non_auth_push_error(raw: str) -> bool:
    text = (raw or "").lower()
    return any(
        marker in text
        for marker in [
            "non-fast-forward",
            "failed to push some refs",
            "protected branch hook declined",
            "[rejected]",
        ]
    )


def commit_and_push(token: str, branch: str, issue_number: int, changed_files: list[str]) -> str:
    if not changed_files:
        raise RuntimeError("No changed files to stage.")
    ensure_git_identity()
    run_cmd("git", "add", "--", *changed_files)
    if run_cmd("git", "diff", "--cached", "--quiet", check=False).returncode == 0:
        raise RuntimeError("No staged changes to commit.")
    message = f"Issue #{issue_number}: apply approved update"
    run_cmd("git", "commit", "-m", message)
    push_branch(token, branch)
    return message


def delete_branch_if_exists(branch: str) -> None:
    if not branch:
        return
    current_branch = run_cmd("git", "branch", "--show-current", check=False).stdout.strip()
    if current_branch == branch:
        run_cmd("git", "checkout", "main", check=False)
        current_branch = run_cmd("git", "branch", "--show-current", check=False).stdout.strip()
    if current_branch == branch:
        run_cmd("git", "checkout", "-B", "main", "origin/main", check=False)
        current_branch = run_cmd("git", "branch", "--show-current", check=False).stdout.strip()
    if current_branch == branch:
        log(f"Branch cleanup skipped: still on {branch}.")
        return
    remote_exists = run_cmd("git", "ls-remote", "--exit-code", "--heads", "origin", branch, check=False).returncode == 0
    if remote_exists:
        run_cmd("git", "push", "origin", "--delete", branch, check=False)
    local_exists = run_cmd("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}", check=False).returncode == 0
    if local_exists:
        run_cmd("git", "branch", "-D", branch, check=False)


def ensure_clean_for_step6(files: list[str]) -> None:
    for rel in files:
        run_cmd("git", "restore", "--staged", "--worktree", "--source=HEAD", "--", rel, check=False)
    status_lines = [line.strip() for line in run_cmd("git", "status", "--porcelain").stdout.splitlines() if line.strip()]
    relevant_paths = set(files)
    for line in status_lines:
        path_part = line[3:] if len(line) > 3 else ""
        candidate_paths = [segment.strip() for segment in path_part.split("->")]
        for path in candidate_paths:
            if not path:
                continue
            if path == "order-api/audit/intake-chat.jsonl" or "__pycache__/" in path:
                continue
            if path in relevant_paths:
                raise RuntimeError("Working tree has changes in Step 6 target files; aborting fix execution.")


def apply_step6_fix_packet(packet: dict[str, Any]) -> list[str]:
    action = str(packet.get("action", "")).strip().lower()
    if action != "replace-text":
        raise RuntimeError(f"Unsupported Step 6 action: {action}")

    from_text = str(packet.get("fromText", ""))
    to_text = str(packet.get("toText", ""))
    if not from_text or from_text == to_text:
        raise RuntimeError("Invalid Step 6 replace-text packet.")

    files_raw = packet.get("files")
    if not isinstance(files_raw, list) or not files_raw:
        raise RuntimeError("Step 6 packet must include non-empty files list.")
    files = [str(item).strip() for item in files_raw if str(item).strip()]
    if not files:
        raise RuntimeError("Step 6 packet files list is empty.")

    changed: list[str] = []
    for rel in files:
        path = REPO_ROOT / rel
        if not path.exists() or not path.is_file():
            continue
        original = path.read_text(encoding="utf-8")
        updated = original.replace(from_text, to_text)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(rel)
    return changed


def run_step6_fix(owner: str, repo: str, token: str, pr_number: int, branch: str, packet: dict[str, Any]) -> dict[str, Any]:
    files_raw = packet.get("files")
    files = [str(item).strip() for item in files_raw] if isinstance(files_raw, list) else []
    branch = branch.strip()
    if not branch:
        raise RuntimeError("Missing Step 6 target branch.")

    run_cmd("git", "fetch", "--all", "--prune")
    run_cmd("git", "checkout", branch)
    run_cmd("git", "pull", "--ff-only", "origin", branch)
    ensure_clean_for_step6(files)

    changed_files = apply_step6_fix_packet(packet)
    if not changed_files:
        return {
            "changed": False,
            "branch": branch,
            "changedFiles": [],
            "commitSha": "",
        }

    ensure_git_identity()
    run_cmd("git", "add", "--", *changed_files)
    if run_cmd("git", "diff", "--cached", "--quiet", check=False).returncode == 0:
        return {
            "changed": False,
            "branch": branch,
            "changedFiles": [],
            "commitSha": "",
        }

    finding_id = str(packet.get("findingId", "")).strip()
    message = f"PR #{pr_number}: apply Step 6 safe fix"
    if finding_id:
        message = f"PR #{pr_number}: apply Step 6 safe fix ({finding_id})"
    run_cmd("git", "commit", "-m", message)
    commit_sha = run_cmd("git", "rev-parse", "HEAD").stdout.strip()
    push_branch(token, branch)
    return {
        "changed": True,
        "branch": branch,
        "changedFiles": changed_files,
        "commitSha": commit_sha,
    }


def create_pr(owner: str, repo: str, token: str, branch: str, scaffold: dict[str, Any]) -> tuple[int, str]:
    env_owner, env_repo = repository_from_env()
    resolved_owner = owner or env_owner
    resolved_repo = repo or env_repo
    if not resolved_owner or not resolved_repo:
        raise RuntimeError("Missing repository owner/repo for MCP pull request creation.")

    result = call_mcp_tool(
        "create_pull_request",
        {
            "owner": resolved_owner,
            "repo": resolved_repo,
            "title": scaffold["prTitle"],
            "head": branch,
            "base": "main",
            "body": scaffold["prBody"],
        },
        token,
    )
    text = ""
    content = result.get("content")
    if isinstance(content, list) and content:
        first = content[0]
        if isinstance(first, dict):
            text = str(first.get("text", "")).strip()

    payload: dict[str, Any] = {}
    if text.startswith("{"):
        try:
            parsed = json.loads(text)
            if isinstance(parsed, dict):
                payload = parsed
        except json.JSONDecodeError:
            payload = {}

    pr_url = str(payload.get("url") or payload.get("html_url") or "").strip()
    if not pr_url and "http" in text:
        match = re.search(r"(https://github\\.com/[^\\s]+/pull/\\d+)", text)
        if match:
            pr_url = match.group(1)

    pr_number = 0
    if "number" in payload:
        try:
            pr_number = int(payload.get("number") or 0)
        except (TypeError, ValueError):
            pr_number = 0
    if pr_number <= 0 and pr_url:
        tail = pr_url.rstrip("/").split("/")[-1]
        if tail.isdigit():
            pr_number = int(tail)

    if pr_number <= 0:
        raise RuntimeError(f"MCP create_pull_request response missing PR number. raw={text[:300]}")
    return pr_number, pr_url


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
        validate_repo_permissions(owner, repo, token)
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
                log(f"Issue #{issue_number}: failed to post unsupported-scope comment.")
            try:
                remove_label(owner, repo, token, issue_number, "approved-for-dev")
            except Exception:
                log(f"Issue #{issue_number}: failed to remove approved-for-dev label after unsupported scope.")
            if label_applied:
                try:
                    remove_label(owner, repo, token, issue_number, "ai-in-progress")
                except Exception:
                    log(f"Issue #{issue_number}: failed to remove ai-in-progress label after unsupported scope.")
            return {
                "issueNumber": issue_number,
                "branch": branch,
                "prNumber": None,
                "prUrl": "",
                "changedFiles": [],
                "merged": False,
            }
        if isinstance(ex, UnsupportedInstructionError):
            log(f"Issue #{issue_number}: unsupported automation scope, deferring to human")
            log_step5_event("approved-issue-unsupported", issue_number=issue_number, error=str(ex))
            try:
                comment_issue(
                    owner,
                    repo,
                    token,
                    issue_number,
                    "Automation deferred this issue because it is outside the current minimal Step 5 implementation scope. "
                    "This workflow currently applies small deterministic text-replacement code changes. "
                    "Please handle this issue manually or split it into an executable rename-story and re-add `approved-for-dev`.",
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
                    log(f"Issue #{issue_number}: failed to cleanup branch after unsupported scope.")
            if branch:
                try:
                    delete_branch_if_exists(branch)
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
        if isinstance(ex, GitHubPermissionError):
            log(f"Issue #{issue_number}: token permission blocker, deferring until credentials are fixed")
            log_step5_event("approved-issue-permission-blocked", issue_number=issue_number, error=str(ex))
            try:
                comment_issue(
                    owner,
                    repo,
                    token,
                    issue_number,
                    "Automation is blocked by APP_GITHUB_TOKEN permissions for write operations. "
                    "Please update the app token to fine-grained permissions on this repo with: "
                    "Contents (Read and write), Pull requests (Read and write), Issues (Read and write), Metadata (Read). "
                    "After updating the token, re-add `approved-for-dev` to retry.",
                )
            except Exception:
                log(f"Issue #{issue_number}: failed to post permission-blocked comment.")
            try:
                remove_label(owner, repo, token, issue_number, "approved-for-dev")
            except Exception:
                log(f"Issue #{issue_number}: failed to remove approved-for-dev label after permission block.")
            if label_applied:
                try:
                    remove_label(owner, repo, token, issue_number, "ai-in-progress")
                except Exception:
                    log(f"Issue #{issue_number}: failed to remove ai-in-progress label after permission block.")
            if branch:
                try:
                    delete_branch_if_exists(branch)
                except Exception:
                    log(f"Issue #{issue_number}: failed to cleanup branch after permission block.")
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
                delete_branch_if_exists(branch)
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
    parser.add_argument("--step6-pr-number", type=int, default=None, help="Step 6: PR number for safe fix execution.")
    parser.add_argument("--step6-fix-on-branch", default="", help="Step 6: existing PR branch name to update.")
    parser.add_argument("--step6-fix-packet-json", default="", help="Step 6: JSON packet describing safe fix action.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    token = token_from_env()
    if not token:
        print("Missing APP_GITHUB_TOKEN.", file=sys.stderr)
        return 1

    if args.step6_pr_number is not None:
        try:
            if not args.step6_fix_packet_json.strip():
                raise RuntimeError("Missing Step 6 fix packet JSON.")
            packet = json.loads(args.step6_fix_packet_json)
            if not isinstance(packet, dict):
                raise RuntimeError("Step 6 fix packet must be a JSON object.")
            result = run_step6_fix(args.owner, args.repo, token, int(args.step6_pr_number), args.step6_fix_on_branch, packet)
            log_step5_event(
                "step6-safe-fix-executed",
                issue_number=None,
                metadata={
                    "prNumber": int(args.step6_pr_number),
                    "branch": result.get("branch", ""),
                    "changed": bool(result.get("changed", False)),
                    "changedFiles": result.get("changedFiles", []),
                    "commitSha": result.get("commitSha", ""),
                },
            )
            print(json.dumps({"step6FixResult": result}, ensure_ascii=True))
            return 0
        except Exception as exc:
            error = str(exc)
            log_step5_event(
                "step6-safe-fix-failed",
                issue_number=None,
                metadata={"prNumber": args.step6_pr_number, "branch": args.step6_fix_on_branch},
                error=error,
            )
            print(error, file=sys.stderr)
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
