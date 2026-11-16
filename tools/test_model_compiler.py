"""Regression checks for the Blockbench -> Java item-model compiler and shipped pack."""
import base64
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import generate_resourcepack as compiler


class ModelCompilerTest(unittest.TestCase):
    def test_item_center_matches_bone_pivot(self):
        cube = {"from": [0, 0, 0], "to": [2, 4, 6],
                "faces": {"north": {"uv": [0, 0, 8, 16], "texture": 0, "rotation": 90}}}
        model, shift = compiler.cube_item_model([cube], [1, 2, 3], "test", 256, 256)
        self.assertEqual([0, 0, 0], shift)
        self.assertEqual([7, 6, 5], model["elements"][0]["from"])
        self.assertEqual([9, 10, 11], model["elements"][0]["to"])
        self.assertEqual([0, 0, .5, 1], model["elements"][0]["faces"]["north"]["uv"])
        self.assertEqual(90, model["elements"][0]["faces"]["north"]["rotation"])

    def test_missing_faces_are_not_materialized(self):
        cube = {"from": [0, 0, 0], "to": [1, 1, 1],
                "faces": {"north": {"uv": [0, 0, 1, 1], "texture": None}}}
        model, _ = compiler.cube_item_model([cube], [0, 0, 0], "test", 16, 16)
        self.assertEqual({}, model["elements"][0]["faces"])
        with self.assertRaises(ValueError):
            compiler._num("query.life_time")

    def test_bundled_pack_has_every_model_texture_and_no_stale_limbs(self):
        with zipfile.ZipFile(compiler.BUNDLED_ZIP) as pack:
            self.assertIsNone(pack.testzip())
            names = set(pack.namelist())
            self.assertFalse(any("\\" in name for name in names))
            for obsolete in ("grinch_arm_left", "grinch_arm_right", "grinch_leg_left", "grinch_leg_right"):
                self.assertNotIn("assets/ultimatechristmas/models/boss/" + obsolete + ".json", names)
            self.assertNotIn("assets/ultimatechristmas/textures/entity/grinch.png", names)
            for model_id, path in compiler.bbmodels():
                source = json.loads(Path(path).read_text(encoding="utf8"))
                expected_texture = base64.b64decode(source["textures"][0]["source"].split(",")[1])
                texture_path = "assets/ultimatechristmas/textures/item/" + model_id + ".png"
                self.assertEqual(expected_texture, pack.read(texture_path))
                rig = json.loads((Path(compiler.RIGS_DIR) / (model_id + ".json")).read_text())
                for bone in rig["bones"]:
                    if bone["model"] is None:
                        continue
                    namespace, key = bone["model"].split(":")
                    item = json.loads(pack.read("assets/" + namespace + "/items/" + key + ".json"))
                    self.assertEqual(bone["model"], item["model"]["model"])
                    model = json.loads(pack.read("assets/" + namespace + "/models/" + key + ".json"))
                    for element in model["elements"]:
                        for axis in range(3):
                            self.assertLessEqual(element["from"][axis], element["to"][axis])
                            self.assertGreaterEqual(element["from"][axis], -16)
                            self.assertLessEqual(element["to"][axis], 32)
                        for face in element["faces"].values():
                            self.assertTrue(all(0 <= value <= 16 for value in face["uv"]))
                    self.assertEqual("ultimatechristmas:item/" + model_id, model["textures"]["0"])

    def test_zip_bytes_do_not_change_with_file_timestamps(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "pack"
            source.mkdir()
            file = source / "pack.mcmeta"
            file.write_text('{"pack":{"pack_format":64}}')
            output = root / "pack.zip"
            with patch.object(compiler, "PACK_DIR", str(source)), patch.object(compiler, "BUNDLED_ZIP", str(output)):
                compiler.zip_pack()
                before = output.read_bytes()
                os.utime(file, (100000, 100000))
                compiler.zip_pack()
                self.assertEqual(before, output.read_bytes())


if __name__ == "__main__":
    unittest.main()
