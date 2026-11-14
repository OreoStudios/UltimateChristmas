"""
Compiles every .bbmodel in src/main/resources/models/ into:

  * a resource pack (one item model per BONE, so bones animate independently),
    zipped to src/main/resources/pack/UltimateChristmas-Models.zip; and
  * a runtime rig at src/main/resources/rigs/<id>.json, holding the bone
    hierarchy and every animation, which the plugin's own display renderer plays.

This is general: it reads whatever bones and whatever named animations a model
contains, so dropping a new .bbmodel here and re-running picks it up. The
.bbmodel stays the single source of truth - pose and animate in Blockbench,
re-run this, and both the pack and the plugin follow.

Run:  python tools/generate_resourcepack.py
"""

import base64
import hashlib
import json
import os
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.normpath(os.path.join(ROOT, "..", "src", "main", "resources", "models"))
RIGS_DIR = os.path.normpath(os.path.join(ROOT, "..", "src", "main", "resources", "rigs"))
PACK_DIR = os.path.normpath(os.path.join(ROOT, "..", "build", "resourcepack"))
BUNDLED_ZIP = os.path.normpath(os.path.join(
    ROOT, "..", "src", "main", "resources", "pack", "UltimateChristmas-Models.zip"))

NAMESPACE = "ultimatechristmas"
COORD_MIN, COORD_MAX = -16.0, 32.0  # vanilla item-model element coordinate limit


def bbmodels():
    if not os.path.isdir(MODELS_DIR):
        return
    for name in sorted(os.listdir(MODELS_DIR)):
        if name.endswith(".bbmodel"):
            yield name[:-len(".bbmodel")], os.path.join(MODELS_DIR, name)


# ----------------------------------------------------------------- bone tree
class BoneNode:
    def __init__(self, name, pivot, parent):
        self.name = name
        self.pivot = pivot
        self.parent = parent
        self.cube_ids = []


def read_bones(model):
    """Flatten the outliner into bones (parent before child) + cube membership."""
    elements = {e["uuid"]: e for e in model["elements"]}
    bones = []

    def visit(node, parent_name):
        bone = BoneNode(node["name"], node.get("origin", [0, 0, 0]), parent_name)
        for child in node.get("children", []):
            if isinstance(child, str):
                if child in elements:
                    bone.cube_ids.append(child)
            # sub-groups handled after, so parent is appended first
        bones.append(bone)
        for child in node.get("children", []):
            if isinstance(child, dict):
                visit(child, node["name"])

    for node in model["outliner"]:
        if isinstance(node, dict):
            visit(node, None)
    return bones, elements


def fit_shift(cubes, pivot):
    """Vertical shift bringing a bone's pivot-relative cubes into [-16, 32]."""
    ys = []
    for cube in cubes:
        ys += [cube["from"][1] - pivot[1], cube["to"][1] - pivot[1]]
    if not ys:
        return 0.0
    lo, hi = min(ys), max(ys)
    if hi - lo > (COORD_MAX - COORD_MIN):
        raise SystemExit("a bone is too tall for the item-model limit")
    shift = 0.0
    if hi > COORD_MAX:
        shift = COORD_MAX - hi
    if lo + shift < COORD_MIN:
        shift = COORD_MIN - lo
    return shift


def bone_item_model(bone, elements, model_id):
    """A vanilla item model of one bone's cubes, relative to its pivot."""
    cubes = [elements[cid] for cid in bone.cube_ids]
    shift = fit_shift(cubes, bone.pivot)
    out = []
    for cube in cubes:
        fx, fy, fz = cube["from"]
        tx, ty, tz = cube["to"]
        px, py, pz = bone.pivot
        faces = {d: {"uv": f["uv"], "texture": "#0"} for d, f in cube["faces"].items()}
        entry = {
            "from": [fx - px, fy - py + shift, fz - pz],
            "to": [tx - px, ty - py + shift, tz - pz],
            "faces": faces,
        }
        if cube.get("rotation"):
            entry["rotation"] = cube["rotation"]
        out.append(entry)
    return {
        "textures": {"0": "%s:entity/%s" % (NAMESPACE, model_id),
                     "particle": "%s:entity/%s" % (NAMESPACE, model_id)},
        "display": {"fixed": {"scale": [1, 1, 1]}},
        "elements": out,
    }, shift


# ----------------------------------------------------------------- animations
def read_animations(model):
    """Extract every animation as per-bone, per-channel keyframe tables."""
    # bone uuid -> name, so animators (keyed by uuid) map back to bone names.
    uuid_to_name = {}

    def collect(node):
        if isinstance(node, dict):
            uuid_to_name[node["uuid"]] = node["name"]
            for child in node.get("children", []):
                collect(child)

    for node in model["outliner"]:
        collect(node)

    animations = {}
    for anim in model.get("animations", []):
        bones = {}
        for bone_uuid, animator in anim.get("animators", {}).items():
            bone_name = uuid_to_name.get(bone_uuid, animator.get("name"))
            if not bone_name:
                continue
            channels = {}
            for keyframe in animator.get("keyframes", []):
                channel = keyframe["channel"]
                if channel not in ("rotation", "position", "scale"):
                    continue
                point = keyframe["data_points"][0]
                frame = [float(keyframe["time"]),
                         _num(point.get("x", 0)), _num(point.get("y", 0)),
                         _num(point.get("z", 0))]
                channels.setdefault(channel, []).append(frame)
            for frames in channels.values():
                frames.sort(key=lambda f: f[0])
            if channels:
                bones[bone_name] = channels
        animations[anim["name"]] = {
            "length": float(anim.get("length", 1.0)),
            "loop": anim.get("loop", "loop") in ("loop", True, "hold"),
            "hold": anim.get("loop") == "hold",
            "bones": bones,
        }
    return animations


def _num(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0  # Blockbench allows math expressions; we only author numbers.


# --------------------------------------------------------------------- write
def texture_png(model):
    _, _, data = model["textures"][0]["source"].partition(",")
    return base64.b64decode(data)


def write(path, data):
    full = os.path.normpath(path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    binary = isinstance(data, (bytes, bytearray))
    with open(full, "wb" if binary else "w", encoding=None if binary else "utf-8") as handle:
        handle.write(data)


def main():
    compiled = []
    for model_id, path in bbmodels():
        with open(os.path.normpath(path), encoding="utf-8") as handle:
            model = json.load(handle)

        bones, elements = read_bones(model)
        base = os.path.join(PACK_DIR, "assets", NAMESPACE)
        write(os.path.join(base, "textures", "entity", model_id + ".png"),
              texture_png(model))

        rig_bones = []
        for bone in bones:
            model_key = None
            if bone.cube_ids:
                bone_model_id = "%s_%s" % (model_id, bone.name)
                item_model, _shift = bone_item_model(bone, elements, model_id)
                write(os.path.join(base, "models", "boss", bone_model_id + ".json"),
                      json.dumps(item_model, indent=2))
                write(os.path.join(base, "items", "boss", bone_model_id + ".json"),
                      json.dumps({"model": {"type": "model",
                                            "model": "%s:boss/%s" % (NAMESPACE, bone_model_id)}},
                                 indent=2))
                model_key = "%s:boss/%s" % (NAMESPACE, bone_model_id)
            rig_bones.append({
                "name": bone.name,
                "parent": bone.parent,
                "pivot": [round(v, 4) for v in bone.pivot],
                "model": model_key,
            })

        rig = {
            "name": model_id,
            "bones": rig_bones,
            "animations": read_animations(model),
        }
        write(os.path.join(RIGS_DIR, model_id + ".json"), json.dumps(rig, indent=2))
        compiled.append((model_id, len(rig_bones), list(rig["animations"].keys())))

    write(os.path.join(PACK_DIR, "pack.mcmeta"), json.dumps({
        "pack": {"pack_format": 46,
                 "supported_formats": {"min_inclusive": 46, "max_inclusive": 99},
                 "description": "UltimateChristmas boss models"}}, indent=2))

    zip_pack()
    print("Compiled %d model(s):" % len(compiled))
    for model_id, bone_count, anims in compiled:
        print("  %-16s %2d bones, animations: %s"
              % (model_id, bone_count, ", ".join(anims) if anims else "(none)"))
    if compiled:
        print("Resource pack -> %s  (sha1 %s)" % (BUNDLED_ZIP, pack_sha1()))
        print("Runtime rigs  -> %s" % RIGS_DIR)


def zip_pack():
    os.makedirs(os.path.dirname(BUNDLED_ZIP), exist_ok=True)
    with zipfile.ZipFile(BUNDLED_ZIP, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder, _dirs, files in os.walk(PACK_DIR):
            for name in files:
                full = os.path.join(folder, name)
                zf.write(full, os.path.relpath(full, PACK_DIR))


def pack_sha1():
    digest = hashlib.sha1()
    with open(BUNDLED_ZIP, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    main()
