"""Convert UltimateChristmas's compiled Java bone items to Geyser v2 assets.

Run generate_resourcepack.py first, or use Maven (which builds both editions).
This is a resource-pack exporter, not an item-display protocol translator.
See docs/bedrock-geyser.md for the required Geyser display extension.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent
JAVA_PACK = ROOT / "build/resourcepack"
RIGS = ROOT / "src/main/resources/rigs"
OUTPUT = ROOT / "target/geyser"
NAMESPACE = "ultimatechristmas"
PACK_NAME = "UltimateChristmas-Bedrock.mcpack"
PACK_VERSION = [4, 1, 2]
UUID_NAMESPACE = uuid.UUID("e3f39072-5b5a-4900-b582-e3b727106f04")


def encode(value):
    return (json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Expected a PNG atlas")
    return struct.unpack(">II", data[16:24])


def resource_path(key, folder, extension):
    if not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_/.-]+", key) or ".." in key:
        raise ValueError("Invalid resource identifier: " + key)
    namespace, path = key.split(":", 1)
    return JAVA_PACK / "assets" / namespace / folder / (path + extension)


def convert_cube(element, width, height):
    """Blockbench Bedrock conventions: mirror X; reverse both top/bottom UV axes.

    Java item coordinates are centered at (8,8,8). The display extension's
    shared 'geyser' bones pivot at (0,8,0), with y-offset -0.5 blocks.
    Keep Y unchanged, center X/Z, and retain every asymmetric signed UV span.
    """
    if "rotation" in element:
        raise ValueError("Compile rotated cubes into rig leaves before Bedrock export")
    lo, hi = element["from"], element["to"]
    size = [hi[i] - lo[i] for i in range(3)]
    if any(n <= 0 for n in size):
        raise ValueError("Invalid cube dimensions")
    cube = {"origin": [8 - hi[0], lo[1], lo[2] - 8], "size": size, "uv": {}}
    for direction, face in element["faces"].items():
        if face.get("texture") != "#0":
            raise ValueError("Expected the compiler's single-atlas texture")
        u0, v0, u1, v1 = face["uv"]
        if not all(0 <= v <= 16 for v in (u0, v0, u1, v1)):
            raise ValueError("UV outside the texture atlas")
        if direction in ("up", "down"):
            u0, v0, u1, v1 = u1, v1, u0, v0
        uv = {"uv": [u0 * width / 16, v0 * height / 16],
              "uv_size": [(u1 - u0) * width / 16, (v1 - v0) * height / 16]}
        rotation = face.get("rotation", 0)
        if rotation not in (0, 90, 180, 270):
            raise ValueError("Unsupported face UV rotation")
        if rotation:
            uv["uv_rotation"] = rotation
        cube["uv"][direction] = uv
    return cube


def geometry(identifier, elements, width, height):
    cubes = [convert_cube(e, width, height) for e in elements]
    # Copy the host's bones, as with a Bedrock armor attachable. These inherit
    # the display extension's translation/rotation/scale; don't animate twice.
    bones = []
    names = ["geyser", "geyser_x", "geyser_y", "geyser_z"]
    for index, name in enumerate(names):
        bone = {"name": name, "pivot": [0, 8, 0]}
        if index:
            bone["parent"] = names[index - 1]
        bones.append(bone)
    # FIXED Java item displays append Y=180 in item-local space. The server's
    # 4.1.1 matrix cancels it, so the Bedrock item must supply the same half-turn.
    bones.append({"name": "uc_item", "parent": "geyser_z", "pivot": [0, 8, 0],
                  "rotation": [0, 180, 0], "cubes": cubes})
    return {"format_version": "1.21.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": width,
                        "texture_height": height, "visible_bounds_width": 16,
                        "visible_bounds_height": 16, "visible_bounds_offset": [0, .5, 0]},
        "bones": bones}]}


def attachable(identifier, geo, texture):
    return {"format_version": "1.10.0", "minecraft:attachable": {"description": {
        "identifier": identifier,
        # These are rig parts, not wearable armor. Only the display host draws
        # the 3D attachable; other contexts use the explicitly mapped atlas icon.
        "item": {identifier: "query.is_owner_identifier_any('geyser:item_display')"},
        "materials": {"default": "entity_alphatest"},
        "textures": {"default": texture}, "geometry": {"default": geo},
        "render_controllers": ["controller.render.ultimatechristmas.rig_part"]}}}


def zip_bytes(files):
    import io
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(files.items()):
            if name.startswith("/") or "\\" in name or ".." in Path(name).parts:
                raise ValueError("Unsafe archive entry")
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, content)
    return output.getvalue()


def build():
    files, definitions, icons, display_mappings, models = {}, [], {}, [], []
    seen = set()
    for rig_file in sorted(RIGS.glob("*.json")):
        rig = json.loads(rig_file.read_text(encoding="utf-8"))
        count = cubes_count = 0
        for bone in rig["bones"]:
            model_key = bone.get("model")
            if not model_key:
                continue
            if model_key in seen:
                raise ValueError("Duplicate item model: " + model_key)
            seen.add(model_key)
            item = json.loads(resource_path(model_key, "items", ".json").read_text(encoding="utf-8"))["model"]
            if item["type"] not in ("model", "minecraft:model"):
                raise ValueError("Expected a direct Java item model")
            model = json.loads(resource_path(item["model"], "models", ".json").read_text(encoding="utf-8"))
            texture_bytes = resource_path(model["textures"]["0"], "textures", ".png").read_bytes()
            width, height = png_size(texture_bytes)
            name = model_key.split(":", 1)[1].replace("/", "_")
            identifier = NAMESPACE + ":" + name
            geo = "geometry." + NAMESPACE + "." + name
            texture = "textures/entity/ultimatechristmas/" + rig_file.stem
            files[texture + ".png"] = texture_bytes
            files["models/entity/ultimatechristmas/" + name + ".geo.json"] = encode(
                geometry(geo, model["elements"], width, height))
            files["attachables/ultimatechristmas/" + name + ".attachable.json"] = encode(
                attachable(identifier, geo, texture))
            icon = NAMESPACE + "." + rig_file.stem
            icons[icon] = {"textures": texture}
            definitions.append({"type": "definition", "model": model_key,
                                "bedrock_identifier": identifier,
                                "display_name": "UltimateChristmas " + rig["name"] + " / " + bone["name"],
                                "bedrock_options": {"icon": icon, "creative_category": "none",
                                                    "allow_offhand": False, "display_handheld": False}})
            display_mappings.extend([
                "  " + name + ":", '    type: "minecraft:paper"',
                '    item-identifier: "' + identifier + '"', "    displayentityoptions:",
                "      y-offset: -0.5", "      vanilla-scale: false", "      vanilla-scale-multiplier: 1",
                "      hand: false", "      rotation: [0, 0, 0]", "      translation: [0, 0, 0]"])
            count += 1
            cubes_count += len(model["elements"])
        models.append({"id": rig["name"], "parts": count, "cubes": cubes_count,
                       "server_animations": sorted(rig["animations"])})
    if not definitions:
        raise ValueError("No compiled rig items; run tools/generate_resourcepack.py first")
    files["manifest.json"] = encode({"format_version": 2,
        "header": {"name": "UltimateChristmas - Grinch, Minions & Santa",
                   "description": "Bedrock rig-part resources for UltimateChristmas 4.1.1+ and GeyserDisplayEntity",
                   "uuid": str(uuid.uuid5(UUID_NAMESPACE, "header")), "version": PACK_VERSION,
                   "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": str(uuid.uuid5(UUID_NAMESPACE, "resources")),
                     "version": PACK_VERSION}],
        "metadata": {"authors": ["OreoStudios"], "url": "https://github.com/OreoStudios/UltimateChristmas"}})
    files["textures/item_texture.json"] = encode({"resource_pack_name": "UltimateChristmas",
        "texture_name": "atlas.items", "texture_data": icons})
    files["render_controllers/ultimatechristmas.rig_part.json"] = encode({"format_version": "1.8.0",
        "render_controllers": {"controller.render.ultimatechristmas.rig_part": {
            "geometry": "Geometry.default", "materials": [{"*": "Material.default"}],
            "textures": ["Texture.default"]}}})
    mapping = {"format_version": 2, "items": {"minecraft:paper": definitions}}
    display_yaml = "# Companion configuration for GeyserDisplayEntity; not a Geyser JSON mapping.\nmappings:\n"
    display_yaml += "\n".join(display_mappings) + "\n"
    report = {"models": models, "mapping_count": len(definitions),
              "animation_source": "UltimateChristmas server-side ItemDisplay transforms",
              "requires_display_entity_extension": True,
              "bedrock_client_visual_tested": False,
              "display_extension_reference_commit": "3d22bff3265f85812beaeb5e3b7e898ee4a58105",
              "limitations": ["Geyser alone does not translate Java item displays.",
                  "The referenced display extension does not preserve all affine rotations and interpolation; animated boss parity is not certified.",
                  "The pack supplies geometry and mappings, not a native Bedrock behavior pack or an animation protocol bridge."]}
    return files, mapping, display_yaml, report


def generate(output=OUTPUT, bundled_pack_dir=None):
    files, mapping, display_yaml, report = build()
    pack = zip_bytes(files)
    report["mcpack_sha256"] = hashlib.sha256(pack).hexdigest()
    bundle = {"packs/" + PACK_NAME: pack,
              "custom_mappings/ultimatechristmas.json": encode(mapping),
              "extensions/GeyserDisplayEntity/Mappings/ultimatechristmas.yml": display_yaml.encode(),
              "report.json": encode(report),
              "README.md": (ROOT / "docs/bedrock-geyser.md").read_bytes()}
    for relative, content in bundle.items():
        destination = output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)
    archive = output / "UltimateChristmas-Geyser-bundle.zip"
    bundle_zip = zip_bytes(bundle)
    archive.write_bytes(bundle_zip)
    if bundled_pack_dir is not None:
        bundled_pack_dir.mkdir(parents=True, exist_ok=True)
        (bundled_pack_dir / PACK_NAME).write_bytes(pack)
        (bundled_pack_dir / archive.name).write_bytes(bundle_zip)
        for relative, content in bundle.items():
            if relative.startswith("packs/"):
                continue
            destination = bundled_pack_dir / "geyser" / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
        print("Bundled Bedrock resources ->", bundled_pack_dir)
    print(f"Bedrock pack -> {output / 'packs' / PACK_NAME} ({len(mapping['items']['minecraft:paper'])} v2 mappings)")
    print("Geyser install bundle ->", archive)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    generate()
