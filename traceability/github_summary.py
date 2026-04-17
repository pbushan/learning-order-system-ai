"""Formatter for concise engineer-facing GitHub trace summary comments."""

from __future__ import annotations


def build_issue_trace_summary(
    *,
    trace_id: str,
    classification: str,
    issue_count: int,
    rationale_summary: str = "",
) -> str:
    normalized_classification = _normalize_classification(classification)
    normalized_trace_id = (trace_id or "").strip() or "trace-unavailable"
    is_decomposed = issue_count > 1
    decomposition_text = f"yes ({issue_count} issues)" if is_decomposed else "no"
    rationale = _trim_rationale(rationale_summary)

    lines = [
        "Generated via agent-assisted intake.",
        "",
        f"- Classification: `{normalized_classification}`",
        f"- Decomposed multi-issue set: {decomposition_text}",
        f"- Trace ID: `{normalized_trace_id}`",
    ]
    if rationale:
        lines.append(f"- Rationale summary: {rationale}")
    return "\n".join(lines)


def _normalize_classification(classification: str) -> str:
    value = (classification or "").strip().lower()
    if value in {"bug", "feature"}:
        return value
    return "unknown"


def _trim_rationale(rationale_summary: str) -> str:
    text = " ".join((rationale_summary or "").strip().split())
    if not text:
        return ""
    max_length = 180
    if len(text) <= max_length:
        return text
    return text[: max_length - 3].rstrip() + "..."
