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

It understands two things the vanilla item-model format cannot express, so that
hand-made Blockbench models render faithfully:

  * bone REST rotation (a group's own rotation) and per-cube rotation. Vanilla
    item models only allow axis-aligned boxes with limited-angle rotation, so a
    cube that carries its own rotation is promoted to its own leaf
    bone whose rest rotation the renderer applies in native ZYX order. The bone's
    rest rotation is written into the rig and composed on top of the animation.
  * box-UV textures at any resolution. A face's pixel UVs are rescaled into the
    0-16 space an item model expects, so 128x128 (or larger) textures work.

Run:  python tools/generate_resourcepack.py
"""

import base64
import hashlib
import json
import os
import math
import zipfile
from pathlib import Path

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
    def __init__(self, name, pivot, parent, rest):
        self.name = name
        self.pivot = [float(v) for v in pivot]
        self.parent = parent
        self.rest = [float(v) for v in rest]
        self.cube_ids = []


def group_meta(model):
    """uuid -> group record. Newer .bbmodel files keep a group's name, origin
    and rotation in a flat `groups` table, with the outliner holding only the
    uuid tree; older ones store them inline on the outliner node. Support both."""
    return {g["uuid"]: g for g in model.get("groups", []) if isinstance(g, dict)}


def read_bones(model):
    """Flatten the outliner into bones (parent before child) + cube membership."""
    elements = {e["uuid"]: e for e in model["elements"]}
    meta = group_meta(model)
    bones = []

    def info(node):
        """Name, pivot (origin) and rest rotation for an outliner group node,
        taken from the groups table when present, else from the node itself."""
        record = meta.get(node.get("uuid"), node)
        name = record.get("name", node.get("name"))
        pivot = record.get("origin", node.get("origin", [0, 0, 0]))
        rest = record.get("rotation", node.get("rotation", [0, 0, 0]))
        return name, pivot, rest

    def visit(node, parent_name):
        name, pivot, rest = info(node)
        bone = BoneNode(name, pivot, parent_name, rest)
        for child in node.get("children", []):
            if isinstance(child, str) and child in elements:
                bone.cube_ids.append(child)
        bones.append(bone)  # parent appended before its sub-groups
        for child in node.get("children", []):
            if isinstance(child, dict):
                visit(child, name)

    for node in model["outliner"]:
        if isinstance(node, dict):
            visit(node, None)
    return bones, elements


def resolution(model):
    res = model.get("resolution", {}) or {}
    return float(res.get("width", 16) or 16), float(res.get("height", 16) or 16)


def is_rotated(cube):
    rot = cube.get("rotation")
    return bool(rot) and any(abs(float(v)) > 1e-9 for v in rot)


# ------------------------------------------------------------- item models
def axis_shift(cubes, pivot):
    """A per-axis shift bringing pivot-relative cube coords into [-16, 32]."""
    shift = [0.0, 0.0, 0.0]
    for axis in range(3):
        lo = hi = None
        for cube in cubes:
            for index, corner in enumerate((cube["from"], cube["to"])):
                inflate = float(cube.get("inflate", 0)) * (-1 if index == 0 else 1)
                value = corner[axis] + inflate - pivot[axis] + 8
                lo = value if lo is None else min(lo, value)
                hi = value if hi is None else max(hi, value)
        if lo is None:
            continue
        if hi - lo > (COORD_MAX - COORD_MIN):
            raise SystemExit("a bone is too big (%.1f) for the item-model limit" % (hi - lo))
        if hi > COORD_MAX:
            shift[axis] = COORD_MAX - hi
        if lo + shift[axis] < COORD_MIN:
            shift[axis] = COORD_MIN - lo
    return shift


def cube_item_model(cubes, pivot, model_id, res_w, res_h):
    """A vanilla item model of some cubes, relative to `pivot`, with box-UV
    pixel coordinates rescaled into the 0-16 space an item model expects.
    Returns (model_json, shift) where `shift` was applied to the geometry and
    must be undone by the renderer via the bone's model_offset."""
    shift = axis_shift(cubes, pivot)
    sx, sy = 16.0 / res_w, 16.0 / res_h
    out = []
    for cube in cubes:
        faces = {}
        for direction, face in cube.get("faces", {}).items():
            uv = face.get("uv")
            if uv is None or face.get("texture") is None:
                continue
            faces[direction] = {
                "uv": [uv[0] * sx, uv[1] * sy, uv[2] * sx, uv[3] * sy],
                "texture": "#0",
            }
            if face.get("rotation", 0):
                faces[direction]["rotation"] = int(face["rotation"])
        inflate = float(cube.get("inflate", 0))
        entry = {
            # ItemDisplay centers an item at (8, 8, 8). Put the bone pivot at
            # that center so rotating the display cannot pull its cubes apart.
            "from": [cube["from"][i] - inflate - pivot[i] + 8 + shift[i] for i in range(3)],
            "to": [cube["to"][i] + inflate - pivot[i] + 8 + shift[i] for i in range(3)],
            "faces": faces,
        }
        out.append(entry)
    # Texture lives under textures/item/ (not entity/) so the vanilla block/item
    # atlas - whose "item" directory source scans every namespace - stitches it.
    # A texture the atlas never sees bakes as "missing" (magenta) on the model.
    model = {
        "ambientocclusion": False,
        "textures": {"0": "%s:item/%s" % (NAMESPACE, model_id),
                     "particle": "%s:item/%s" % (NAMESPACE, model_id)},
        "display": {"fixed": {"scale": [1, 1, 1]}},
        "elements": out,
    }
    return model, shift


# ----------------------------------------------------------------- animations
def read_animations(model):
    """Extract every animation as per-bone, per-channel keyframe tables."""
    meta = group_meta(model)
    uuid_to_name = {}

    def collect(node):
        if isinstance(node, dict):
            record = meta.get(node.get("uuid"), node)
            uuid_to_name[node.get("uuid")] = record.get("name", node.get("name"))
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
                if len(keyframe["data_points"]) != 1:
                    raise ValueError("Pre/post keyframes need baking before compilation")
                interpolation = keyframe.get("interpolation", "linear")
                if interpolation not in ("linear", "step", "catmullrom"):
                    raise ValueError("Bake unsupported interpolation: " + interpolation)
                point = keyframe["data_points"][0]
                frame = [float(keyframe["time"]),
                         _num(point.get("x", 0)), _num(point.get("y", 0)),
                         _num(point.get("z", 0)), interpolation]
                channels.setdefault(channel, []).append(frame)
            for frames in channels.values():
                frames.sort(key=lambda f: f[0])
            if channels:
                bones[bone_name] = channels
        clip = anim["name"]
        if clip.startswith("animation."):
            clip = clip[len("animation."):]  # match the idle/walk/attack config convention
        animations[clip] = {
            "length": float(anim.get("length", 1.0)),
            "loop": anim.get("loop", "loop") in ("loop", True),
            "hold": anim.get("loop") == "hold",
            "bones": bones,
        }
    return animations


def _num(value):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("Non-finite animation value: " + str(value))
    return result  # Reject unbaked expressions rather than silently zeroing them.


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


def round_list(values, places=4):
    return [round(float(v), places) for v in values]


def main():
    # Only these generated directories belong to this compiler. Remove their
    # previous outputs so obsolete cubes and textures cannot leak into the zip.
    base = Path(PACK_DIR).resolve() / "assets" / NAMESPACE
    expected = (Path(ROOT).parent / "build" / "resourcepack").resolve()
    if Path(PACK_DIR).resolve() != expected:
        raise ValueError("Unexpected resource-pack output directory")
    for relative in ("models/boss", "items/boss", "textures/item", "textures/entity"):
        folder = (base / relative).resolve()
        if not folder.is_relative_to(expected):
            raise ValueError("Generated path escapes resource pack")
        if folder.exists():
            for file in folder.iterdir():
                if file.is_file() and file.suffix in (".json", ".png"):
                    file.unlink()
    compiled = []
    for model_id, path in bbmodels():
        with open(os.path.normpath(path), encoding="utf-8") as handle:
            model = json.load(handle)
        if len(model.get("textures", [])) != 1:
            raise ValueError(model_id + ": expected one embedded texture atlas")

        bones, elements = read_bones(model)
        assigned = {cid for bone in bones for cid in bone.cube_ids}
        for element_id, element in elements.items():
            if not element.get("export", True):
                continue
            if element.get("type", "cube") != "cube" or element_id not in assigned:
                raise ValueError(model_id + ": only cubes assigned to bones can be compiled")
        if len({bone.name for bone in bones}) != len(bones):
            raise ValueError(model_id + ": duplicate bone names")
        res_w, res_h = resolution(model)
        base = os.path.join(PACK_DIR, "assets", NAMESPACE)
        write(os.path.join(base, "textures", "item", model_id + ".png"),
              texture_png(model))

        def emit_model(bone_model_id, cubes, pivot):
            item_model, shift = cube_item_model(cubes, pivot, model_id, res_w, res_h)
            write(os.path.join(base, "models", "boss", bone_model_id + ".json"),
                  json.dumps(item_model, indent=2))
            write(os.path.join(base, "items", "boss", bone_model_id + ".json"),
                  json.dumps({"model": {"type": "model",
                                        "model": "%s:boss/%s" % (NAMESPACE, bone_model_id)}},
                             indent=2))
            return "%s:boss/%s" % (NAMESPACE, bone_model_id), shift

        rig_bones = []
        for bone in bones:
            cubes = [elements[cid] for cid in bone.cube_ids
                     if elements[cid].get("export", True)]
            still = [c for c in cubes if not is_rotated(c)]
            rotated = [c for c in cubes if is_rotated(c)]

            model_key, shift = None, [0.0, 0.0, 0.0]
            if still:
                model_key, shift = emit_model(
                    "%s_%s" % (model_id, bone.name), still, bone.pivot)
            rig_bones.append({
                "name": bone.name,
                "parent": bone.parent,
                "pivot": round_list(bone.pivot),
                "rotation": round_list(bone.rest),
                "model": model_key,
                "model_offset": round_list(shift),
            })

            # Each rotated cube becomes a leaf bone pivoting at the cube's own
            # rotation origin; the renderer applies its rest rotation. All cube
            # rotations use the same ZYX order as Blockbench, including cubes
            # rotated around multiple axes (cheeks, fur and the folded hat).
            for index, cube in enumerate(rotated):
                child_name = "%s__c%d" % (bone.name, index)
                origin = cube.get("origin", [0, 0, 0])
                child_key, child_shift = emit_model(
                    "%s_%s" % (model_id, child_name), [cube], origin)
                rig_bones.append({
                    "name": child_name,
                    "source_element": cube["uuid"],
                    "parent": bone.name,
                    "pivot": round_list(origin),
                    "rotation": round_list(cube.get("rotation", [0, 0, 0])),
                    "model": child_key,
                    "model_offset": round_list(child_shift),
                })

        rig = {
            "name": model_id,
            "bones": rig_bones,
            "animations": read_animations(model),
        }
        write(os.path.join(RIGS_DIR, model_id + ".json"), json.dumps(rig, indent=2))
        compiled.append((model_id, len(rig_bones), list(rig["animations"].keys())))

    # Target the project's Paper 1.21.8 client format. Do not claim compatibility
    # with arbitrary past/future formats just to suppress their warning.
    write(os.path.join(PACK_DIR, "pack.mcmeta"), json.dumps({
        "pack": {"pack_format": 64,
                 "description": "UltimateChristmas remastered Grinch and minions"}}, indent=2))

    zip_pack()
    print("Compiled %d model(s):" % len(compiled))
    for model_id, bone_count, anims in compiled:
        print("  %-20s %2d bones, animations: %s"
              % (model_id, bone_count, ", ".join(anims) if anims else "(none)"))
    if compiled:
        print("Resource pack -> %s  (sha1 %s)" % (BUNDLED_ZIP, pack_sha1()))
        print("Runtime rigs  -> %s" % RIGS_DIR)
    from generate_bedrock_pack import generate
    generate(bundled_pack_dir=Path(BUNDLED_ZIP).parent)
    write_manifest()


def zip_pack():
    os.makedirs(os.path.dirname(BUNDLED_ZIP), exist_ok=True)
    with zipfile.ZipFile(BUNDLED_ZIP, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder, dirs, files in os.walk(PACK_DIR):
            dirs.sort()
            for name in sorted(files):
                full = os.path.join(folder, name)
                info = zipfile.ZipInfo(Path(full).relative_to(PACK_DIR).as_posix(),
                                       date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                zf.writestr(info, Path(full).read_bytes())


def write_manifest():
    resources = Path(MODELS_DIR).parent
    files = {}
    for folder in ("models", "rigs", "pack"):
        for path in sorted((resources / folder).rglob("*")):
            if path.is_file() and path.suffix in (".bbmodel", ".json", ".zip", ".mcpack", ".txt", ".yml", ".md"):
                files[path.relative_to(resources).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    revision = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
    write(resources / "model-assets.json", json.dumps({"revision": revision, "files": files}, indent=2))


def pack_sha1():
    digest = hashlib.sha1()
    with open(BUNDLED_ZIP, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    main()
