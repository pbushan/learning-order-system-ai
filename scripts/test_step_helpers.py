import unittest
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import build_work_packet
import classify_review_comments
import review_response_templates


class BuildWorkPacketHelpersTest(unittest.TestCase):
    def test_extract_section_lines_returns_bullets_and_plain_lines_until_next_section(self) -> None:
        body = (
            "Summary line\n\n"
            "## Acceptance Criteria\n"
            "- first condition\n"
            "second condition\n\n"
            "## Affected Components\n"
            "- order-ui\n"
            "- order-api\n"
            "## Notes\n"
            "ignored"
        )

        criteria = build_work_packet.extract_section_lines(body, "Acceptance Criteria")
        components = build_work_packet.extract_section_lines(body, "Affected Components")

        self.assertEqual(criteria, ["first condition", "second condition"])
        self.assertEqual(components, ["order-ui", "order-api"])

    def test_build_work_packet_uses_first_line_summary_and_slugged_branch(self) -> None:
        packet = build_work_packet.build_work_packet(
            {
                "issueNumber": 77,
                "title": "Fix intake payload validation for UI/API boundary",
                "body": "One-line summary\nMore details below\n\n## Affected Components\n- order-api\n",
            }
        )

        self.assertEqual(packet["summary"], "One-line summary")
        self.assertEqual(packet["affectedComponents"], ["order-api"])
        self.assertTrue(packet["branchNameSuggestion"].startswith("codex/issue-77-fix-intake-payload-validation-for-ui-api"))


class Step6DeterministicHelpersTest(unittest.TestCase):
    def test_classify_comment_marks_security_text_as_blocking(self) -> None:
        result = classify_review_comments.classify_comment(
            {"id": 5, "body": "Potential security issue: auth bypass path is exposed.", "url": "http://example"}
        )

        self.assertEqual(result["classification"], "blocking-address-now")
        self.assertEqual(result["reason"], "security-critical risk")

    def test_classify_comment_defaults_to_non_blocking_for_general_feedback(self) -> None:
        result = classify_review_comments.classify_comment({"id": 6, "body": "Could be a little cleaner"})

        self.assertEqual(result["classification"], "non-blocking-defer")
        self.assertEqual(result["reason"], "No blocking criterion matched for portfolio scope.")

    def test_ready_to_merge_note_includes_fixed_and_default_deferred_text(self) -> None:
        note = review_response_templates.ready_to_merge_note("trace read API test added", "")

        self.assertIn("- Fixed: trace read API test added", note)
        self.assertIn("- Intentionally deferred: None.", note)
        self.assertIn("This PR is ready to merge.", note)


if __name__ == "__main__":
    unittest.main()
