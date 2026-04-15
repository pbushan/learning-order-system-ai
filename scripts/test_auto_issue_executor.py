import tempfile
import unittest
from pathlib import Path

import auto_issue_executor as executor


class Step5BodyRenameParsingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.original_repo_root = executor.REPO_ROOT
        self.original_target_files = list(executor.STEP5_TARGET_FILES)

        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo_root = Path(self.temp_dir.name)
        ui_dir = self.repo_root / "order-ui"
        ui_dir.mkdir(parents=True, exist_ok=True)
        (ui_dir / "index.html").write_text(
            "<div>Orders wrkbench</div>\n<h2>Orders wrkbench</h2>\n",
            encoding="utf-8",
        )

        executor.REPO_ROOT = self.repo_root
        executor.STEP5_TARGET_FILES = ["order-ui/index.html"]

    def tearDown(self) -> None:
        executor.REPO_ROOT = self.original_repo_root
        executor.STEP5_TARGET_FILES = self.original_target_files
        self.temp_dir.cleanup()

    def test_issue_body_spelling_pattern_is_executable(self) -> None:
        issue = {
            "title": "Fix spelling error on Orders workbench tab label",
            "body": "Correct the spelling of 'wrkbench' to 'workbench' on the Orders tab label.",
        }

        changed = executor.apply_issue_change(issue)

        self.assertEqual(changed, ["order-ui/index.html"])
        updated = (self.repo_root / "order-ui/index.html").read_text(encoding="utf-8")
        self.assertIn("Orders workbench", updated)
        self.assertNotIn("Orders wrkbench", updated)

    def test_body_should_be_phrase_is_not_treated_as_deterministic(self) -> None:
        issue = {
            "title": "Fix spelling error on Orders workbench tab label",
            "body": "'wrkbench' should be 'workbench' in the tab label.",
        }

        with self.assertRaises(executor.UnsupportedInstructionError):
            executor.apply_issue_change(issue)


if __name__ == "__main__":
    unittest.main()
