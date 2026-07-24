from __future__ import annotations

import subprocess
import unittest
from pathlib import Path


class ImmutableEvidenceCheckoutV0Test(unittest.TestCase):
    def test_physical_evidence_tree_is_not_treated_as_text(self) -> None:
        root = Path(__file__).resolve().parents[2]
        paths = (
            "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0001.txt",
            "docs/research/evidence/morimil-trimotor-v0.2-physical-20260722-043653-908d91aa/instrumentation-output.txt",
            "docs/research/evidence/morimil-trimotor-v0.2-physical-20260722-043653-908d91aa/bundle-trimotor-v0.2.json",
        )

        for path in paths:
            result = subprocess.run(
                ["git", "check-attr", "text", "--", path],
                cwd=root,
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertEqual(f"{path}: text: unset", result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
