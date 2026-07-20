import argparse
import json
import os
import tempfile
import unittest
from pathlib import Path

import certify_deliberative_v01 as cert


class DeliberativeArtifactCertifierTest(unittest.TestCase):
    def test_manifest_digest_matches_kotlin_golden_vector(self) -> None:
        manifest = cert.build_manifest(
            artifact_sha256=(
                "sha256:04d7d9f07ebd00056060c36766c601bb"
                "620eddd6bede91184f87c9cca5016697"
            ),
            artifact_size_bytes=16,
            tokenizer_sha256="sha256:" + "a" * 64,
            source_revision="0" * 40,
            source_snapshot_sha256="sha256:" + "b" * 64,
            conversion_recipe_sha256="sha256:" + "c" * 64,
        )

        self.assertEqual(
            "sha256:9e0863f34ed35090d60a66a57f51dc7"
            "22e4976ac34eaa730d16b23c4b6603747",
            cert.manifest_digest(manifest),
        )

    def test_certify_and_verify_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact, snapshot, recipe = self._fixture(root)
            manifest_path = root / "manifest.json"
            report_path = root / "report.json"

            result = cert.certify(
                argparse.Namespace(
                    artifact=artifact,
                    source_snapshot=snapshot,
                    tokenizer_relative_path="tokenizer/tokenizer.model",
                    recipe=recipe,
                    source_revision="1" * 40,
                    manifest_out=manifest_path,
                    report_out=report_path,
                    license_accepted=True,
                    overwrite=False,
                )
            )
            self.assertEqual(0, result)

            verify_result = cert.verify(
                argparse.Namespace(
                    artifact=artifact,
                    source_snapshot=snapshot,
                    tokenizer_relative_path="tokenizer/tokenizer.model",
                    recipe=recipe,
                    source_revision="1" * 40,
                    manifest=manifest_path,
                    report=report_path,
                )
            )
            self.assertEqual(0, verify_result)

            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertEqual(cert.ARTIFACT_VERSION, manifest["artifactVersion"])
            self.assertEqual(cert.ARCHITECTURE_ID, manifest["architectureId"])
            self.assertEqual(cert.SNAPSHOT_DIGEST_PROFILE, report["snapshotDigestProfile"])
            self.assertFalse(report["signed"])

    def test_verify_rejects_mutated_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact, snapshot, recipe = self._fixture(root)
            manifest_path = root / "manifest.json"
            report_path = root / "report.json"

            cert.certify(
                argparse.Namespace(
                    artifact=artifact,
                    source_snapshot=snapshot,
                    tokenizer_relative_path="tokenizer/tokenizer.model",
                    recipe=recipe,
                    source_revision="2" * 40,
                    manifest_out=manifest_path,
                    report_out=report_path,
                    license_accepted=True,
                    overwrite=False,
                )
            )
            artifact.write_bytes(artifact.read_bytes() + b"mutation")

            with self.assertRaisesRegex(
                cert.CertificationError,
                "manifest_does_not_match_local_inputs",
            ):
                cert.verify(
                    argparse.Namespace(
                        artifact=artifact,
                        source_snapshot=snapshot,
                        tokenizer_relative_path="tokenizer/tokenizer.model",
                        recipe=recipe,
                        source_revision="2" * 40,
                        manifest=manifest_path,
                        report=report_path,
                    )
                )

    def test_snapshot_digest_is_independent_of_creation_order(self) -> None:
        with tempfile.TemporaryDirectory() as left_dir, tempfile.TemporaryDirectory() as right_dir:
            left = Path(left_dir)
            right = Path(right_dir)
            self._write_snapshot(left, reverse=False)
            self._write_snapshot(right, reverse=True)

            self.assertEqual(
                cert.canonical_snapshot_digest(left),
                cert.canonical_snapshot_digest(right),
            )

    def test_placeholder_source_revision_is_rejected_for_certification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact, snapshot, recipe = self._fixture(root)

            with self.assertRaisesRegex(
                cert.CertificationError,
                "source_revision_placeholder_forbidden",
            ):
                cert.expected_manifest_from_inputs(
                    artifact=artifact,
                    source_snapshot=snapshot,
                    tokenizer_relative_path="tokenizer/tokenizer.model",
                    recipe=recipe,
                    source_revision="0" * 40,
                )

    def test_recipe_with_unknown_field_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "recipe.json"
            value = dict(cert.RECIPE_EXPECTED)
            value["unexpected"] = True
            path.write_text(json.dumps(value), encoding="utf-8")

            with self.assertRaisesRegex(
                cert.CertificationError,
                "conversion_recipe_contract_mismatch",
            ):
                cert.validate_recipe(path)

    @unittest.skipIf(not hasattr(os, "symlink"), "symlinks unavailable")
    def test_snapshot_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.bin"
            target.write_bytes(b"target")
            link = root / "link.bin"
            try:
                link.symlink_to(target)
            except OSError:
                self.skipTest("symlink creation unavailable")

            with self.assertRaisesRegex(
                cert.CertificationError,
                "snapshot_symlink_not_allowed",
            ):
                cert.canonical_snapshot_digest(root)

    def _fixture(self, root: Path) -> tuple[Path, Path, Path]:
        artifact = root / cert.ARTIFACT_FILENAME
        artifact.write_bytes(b"litertlm-test-artifact")

        snapshot = root / "source-snapshot"
        self._write_snapshot(snapshot, reverse=False)

        recipe = root / "recipe.json"
        recipe.write_text(
            json.dumps(cert.RECIPE_EXPECTED, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        return artifact, snapshot, recipe

    def _write_snapshot(self, root: Path, *, reverse: bool) -> None:
        entries = [
            ("config.json", b'{"model":"gemma-3-1b-it"}\n'),
            ("tokenizer/tokenizer.model", b"tokenizer-bytes"),
            ("weights/model.safetensors", b"weights-fixture"),
        ]
        if reverse:
            entries.reverse()
        for relative, content in entries:
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)


if __name__ == "__main__":
    unittest.main()
