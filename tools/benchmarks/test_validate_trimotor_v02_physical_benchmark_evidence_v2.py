from __future__ import annotations

import copy
import tempfile
import unittest
from pathlib import Path

import validate_trimotor_v02_physical_benchmark_evidence_v2 as validator


class Current8436EvidenceV2Test(unittest.TestCase):
    def test_frozen_manifest_passes(self) -> None:
        value = validator.load_manifest()
        validator.validate_manifest(value)

    def test_tampered_part_fails(self) -> None:
        value = validator.load_manifest()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for record in value["storage"]["archiveBase64Parts"]["parts"]:
                source = validator.root() / record["path"]
                target = root / record["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
            first = root / value["storage"]["archiveBase64Parts"]["parts"][0]["path"]
            first.write_bytes(first.read_bytes()[:-1] + b"A")
            with self.assertRaisesRegex(ValueError, "Base64 part mismatch"):
                validator.decode_archive(value, root)

    def test_activation_flag_fails(self) -> None:
        value = copy.deepcopy(validator.load_manifest())
        value["promotion"]["productionAuthorization"] = True
        with self.assertRaisesRegex(ValueError, "forbidden promotion flag"):
            validator.validate_manifest(value)

    def test_hybrid_boundary_cannot_be_erased(self) -> None:
        value = copy.deepcopy(validator.load_manifest())
        value["authorityBoundary"]["hybridAuthorityRequiredForThisResult"] = False
        with self.assertRaisesRegex(ValueError, "hybrid-authority limitation"):
            validator.validate_manifest(value)


if __name__ == "__main__":
    unittest.main()
