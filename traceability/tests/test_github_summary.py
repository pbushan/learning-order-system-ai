from __future__ import annotations

import unittest

from traceability import build_issue_trace_summary


class GitHubTraceSummaryTest(unittest.TestCase):
    def test_build_issue_trace_summary_includes_expected_engineer_fields(self) -> None:
        comment = build_issue_trace_summary(
            trace_id="trace-123",
            classification="Feature",
            issue_count=3,
            rationale_summary="Classification mapped to requested product enhancement.",
        )

        self.assertIn("Generated via agent-assisted intake.", comment)
        self.assertIn("- Classification: `feature`", comment)
        self.assertIn("- Decomposed multi-issue set: yes (3 issues)", comment)
        self.assertIn("- Trace ID: `trace-123`", comment)
        self.assertIn("- Rationale summary: Classification mapped to requested product enhancement.", comment)

    def test_build_issue_trace_summary_uses_none_provided_for_empty_rationale(self) -> None:
        comment = build_issue_trace_summary(
            trace_id="trace-456",
            classification="maintenance",
            issue_count=1,
            rationale_summary="   ",
        )

        self.assertIn("- Classification: `unknown`", comment)
        self.assertIn("- Decomposed multi-issue set: no", comment)
        self.assertIn("- Rationale summary: none provided", comment)

    def test_build_issue_trace_summary_trims_rationale(self) -> None:
        comment = build_issue_trace_summary(
            trace_id="trace-789",
            classification="bug",
            issue_count=1,
            rationale_summary=(
                "This rationale intentionally contains enough words to exceed the configured maximum length "
                "so the formatter keeps the GitHub comment concise and suitable for engineers reviewing the "
                "issue context without introducing noise."
            ),
        )

        self.assertTrue(comment.endswith("..."))


if __name__ == "__main__":
    unittest.main()
