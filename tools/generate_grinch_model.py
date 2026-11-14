"""
Generates an animated Blockbench `.bbmodel` of the Grinch.

The model is a bone-rigged blocky humanoid: a green Grinch in a red Santa coat
and hat. It carries three looping/holding animations - idle, walk and attack -
authored as keyframes so both Blockbench and the plugin's own display-entity
renderer can play them.

The .bbmodel is the single source of truth: tools/generate_resourcepack.py reads
the bones, geometry and animations straight out of it to build the resource pack
and the runtime rig. So re-pose or re-animate in Blockbench, re-run that tool,
and the plugin follows.

Run:  python tools/generate_grinch_model.py
Out:  src/main/resources/models/grinch.bbmodel
"""

import base64
import json
import os
import struct
import uuid
import zlib

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "src", "main", "resources", "models")

# name -> (pixel x, pixel y, rgba) in a 16x16 palette texture.
PALETTE = {
    "skin":      (1, 1, (90, 170, 70, 255)),
    "skin_dark": (3, 1, (60, 130, 50, 255)),
    "coat":      (5, 1, (196, 30, 58, 255)),
    "coat_dark": (7, 1, (150, 20, 44, 255)),
    "trim":      (9, 1, (240, 240, 240, 255)),
    "black":     (11, 1, (20, 20, 20, 255)),
    "eye":       (13, 1, (240, 220, 80, 255)),
    "buckle":    (15, 1, (220, 180, 40, 255)),
}
TEXTURE_SIZE = 16


def build_texture_png():
    pixels = [[(0, 0, 0, 0) for _ in range(TEXTURE_SIZE)] for _ in range(TEXTURE_SIZE)]
    for _name, (px, py, rgba) in PALETTE.items():
        for dx in (-1, 0):
            for dy in (-1, 0):
                x, y = px + dx, py + dy
                if 0 <= x < TEXTURE_SIZE and 0 <= y < TEXTURE_SIZE:
                    pixels[y][x] = rgba
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xffffffff)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", TEXTURE_SIZE, TEXTURE_SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    return png


def uv_for(colour):
    px, py, _ = PALETTE[colour]
    return [px, py, px + 0.5, py + 0.5]


# --------------------------------------------------------------- model build
ELEMENTS = []


def cube(name, origin, size, colour, faces=None):
    fx, fy, fz = origin
    sx, sy, sz = size
    per_face = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        per_face[face] = {"uv": uv_for((faces or {}).get(face, colour)), "texture": 0}
    element_id = str(uuid.uuid4())
    ELEMENTS.append({
        "name": name, "box_uv": False, "rescale": False, "locked": False,
        "type": "cube", "uuid": element_id,
        "from": [fx, fy, fz], "to": [fx + sx, fy + sy, fz + sz],
        "autouv": 0, "color": 0,
        "origin": [fx + sx / 2, fy, fz + sz / 2], "faces": per_face,
    })
    return element_id


class Bone:
    def __init__(self, name, pivot):
        self.name = name
        self.uuid = str(uuid.uuid4())
        self.pivot = pivot
        self.cubes = []
        self.children = []

    def add(self, *cube_ids):
        self.cubes.extend(cube_ids)
        return self

    def child(self, bone):
        self.children.append(bone)
        return bone

    def to_outliner(self):
        return {
            "name": self.name, "origin": list(self.pivot), "color": 0,
            "uuid": self.uuid, "export": True, "isOpen": True, "locked": False,
            "visibility": True, "autouv": 0,
            "children": self.cubes + [child.to_outliner() for child in self.children],
        }


def build_bones():
    root = Bone("root", (0, 0, 0))

    leg_r = Bone("leg_right", (-2, 12, 0)).add(
        cube("boot_r", (-4, 0, -2), (4, 3, 4), "black"),
        cube("shin_r", (-4, 3, -2), (4, 9, 4), "skin_dark"))
    leg_l = Bone("leg_left", (2, 12, 0)).add(
        cube("boot_l", (0, 0, -2), (4, 3, 4), "black"),
        cube("shin_l", (0, 3, -2), (4, 9, 4), "skin_dark"))

    body = Bone("body", (0, 12, 0)).add(
        cube("coat", (-4, 12, -2), (8, 11, 4), "coat"),
        cube("belt", (-4, 13, -2.1), (8, 2, 4.2), "black", faces={"north": "buckle"}),
        cube("coat_trim", (-4, 12, -2.1), (8, 1, 4.2), "trim"))

    arm_r = Bone("arm_right", (-4, 21, 0)).add(
        cube("sleeve_r", (-7, 12, -2), (3, 9, 4), "coat"),
        cube("cuff_r", (-7, 12, -2.1), (3, 1, 4.2), "trim"),
        cube("hand_r", (-7, 9, -2), (3, 3, 4), "skin"))
    arm_l = Bone("arm_left", (4, 21, 0)).add(
        cube("sleeve_l", (4, 12, -2), (3, 9, 4), "coat"),
        cube("cuff_l", (4, 12, -2.1), (3, 1, 4.2), "trim"),
        cube("hand_l", (4, 9, -2), (3, 3, 4), "skin"))

    head = Bone("head", (0, 23, 0)).add(
        cube("head", (-4, 23, -4), (8, 8, 8), "skin"),
        cube("eye_r", (-3, 27, -4.1), (2, 1, 0.2), "eye"),
        cube("eye_l", (1, 27, -4.1), (2, 1, 0.2), "eye"),
        cube("mouth", (-2, 25, -4.1), (4, 1, 0.2), "black"),
        cube("brow", (-4, 28, -4.1), (8, 1, 0.3), "skin_dark"))
    hat = Bone("hat", (0, 31, 0)).add(
        cube("hat_brim", (-4.2, 30.5, -4.2), (8.4, 1.5, 8.4), "trim"),
        cube("hat_1", (-4, 31, -4), (8, 2, 8), "coat"),
        cube("hat_2", (-3, 33, -3), (6, 2, 6), "coat"),
        cube("hat_3", (-2, 35, -2), (4, 2, 4), "coat"),
        cube("hat_tip", (0, 37, 1), (2, 2, 2), "coat"),
        cube("hat_pom", (-0.5, 38.5, 1.5), (3, 3, 3), "trim"))
    head.child(hat)

    for bone in (leg_r, leg_l, body, arm_r, arm_l, head):
        root.child(bone)
    return root


def flatten(bone, into):
    into[bone.name] = bone
    for child in bone.children:
        flatten(child, into)
    return into


# ----------------------------------------------------------------- animations
# Each animation: length seconds, loop mode, and per-bone rotation keyframes in
# Blockbench's convention (degrees, about the bone pivot). time in seconds.
def animations(bones):
    def rot(*frames):
        return [{"channel": "rotation", "time": t,
                 "data_points": [{"x": str(x), "y": str(y), "z": str(z)}],
                 "uuid": str(uuid.uuid4()), "interpolation": "catmullrom", "color": -1}
                for (t, x, y, z) in frames]

    def pos(*frames):
        return [{"channel": "position", "time": t,
                 "data_points": [{"x": str(x), "y": str(y), "z": str(z)}],
                 "uuid": str(uuid.uuid4()), "interpolation": "linear", "color": -1}
                for (t, x, y, z) in frames]

    specs = {
        "idle": {"length": 3.0, "loop": "loop", "bones": {
            "head": rot((0, 0, 0, 0), (1.5, 4, 3, 0), (3.0, 0, 0, 0)),
            "arm_right": rot((0, 0, 0, 2), (1.5, -3, 0, 2), (3.0, 0, 0, 2)),
            "arm_left": rot((0, 0, 0, -2), (1.5, -3, 0, -2), (3.0, 0, 0, -2)),
            "body": pos((0, 0, 0, 0), (1.5, 0, 0.4, 0), (3.0, 0, 0, 0)),
        }},
        "walk": {"length": 1.0, "loop": "loop", "bones": {
            "leg_right": rot((0, 32, 0, 0), (0.5, -32, 0, 0), (1.0, 32, 0, 0)),
            "leg_left": rot((0, -32, 0, 0), (0.5, 32, 0, 0), (1.0, -32, 0, 0)),
            "arm_right": rot((0, -28, 0, 2), (0.5, 28, 0, 2), (1.0, -28, 0, 2)),
            "arm_left": rot((0, 28, 0, -2), (0.5, -28, 0, -2), (1.0, 28, 0, -2)),
            "head": rot((0, 0, 0, 0), (0.5, 3, 0, 0), (1.0, 0, 0, 0)),
        }},
        "attack": {"length": 0.6, "loop": "once", "bones": {
            "arm_right": rot((0, 0, 0, 2), (0.15, -150, 0, 2), (0.35, -150, 0, 2), (0.6, 0, 0, 2)),
            "arm_left": rot((0, 0, 0, -2), (0.2, -120, 0, -2), (0.4, -120, 0, -2), (0.6, 0, 0, -2)),
            "body": rot((0, 0, 0, 0), (0.15, -8, 0, 0), (0.6, 0, 0, 0)),
            "head": rot((0, 0, 0, 0), (0.15, -10, 0, 0), (0.6, 0, 0, 0)),
        }},
    }

    out = []
    for name, spec in specs.items():
        animators = {}
        for bone_name, keyframes in spec["bones"].items():
            animators[bones[bone_name].uuid] = {
                "name": bone_name, "type": "bone", "keyframes": keyframes}
        out.append({
            "uuid": str(uuid.uuid4()), "name": name, "loop": spec["loop"],
            "override": False, "length": spec["length"], "snapping": 24,
            "selected": False, "anim_time_update": "", "blend_weight": "",
            "start_delay": "", "loop_delay": "", "animators": animators,
        })
    return out


def main():
    root = build_bones()
    bones = flatten(root, {})
    png = build_texture_png()
    data_uri = "data:image/png;base64," + base64.b64encode(png).decode("ascii")

    model = {
        "meta": {"format_version": "4.5", "model_format": "free", "box_uv": False},
        "name": "grinch", "model_identifier": "grinch",
        "visible_box": [2, 3, 0.5], "variable_placeholders": "",
        "resolution": {"width": TEXTURE_SIZE, "height": TEXTURE_SIZE},
        "elements": ELEMENTS,
        "outliner": [root.to_outliner()],
        "textures": [{
            "name": "grinch.png", "folder": "block", "namespace": "", "id": "0",
            "particle": True, "render_mode": "default", "visible": True, "mode": "bitmap",
            "width": TEXTURE_SIZE, "height": TEXTURE_SIZE,
            "uv_width": TEXTURE_SIZE, "uv_height": TEXTURE_SIZE,
            "frame_time": 1, "frame_interpolate": False,
            "frame_order_type": "loop", "frame_order": "",
            "uuid": str(uuid.uuid4()), "source": data_uri,
        }],
        "animations": animations(bones),
    }

    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.normpath(os.path.join(OUT_DIR, "grinch.bbmodel"))
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(model, handle, indent=2)

    reloaded = json.load(open(path, encoding="utf-8"))
    print("Wrote %s" % path)
    print("  %d cubes, %d bones, %d animations (%s)"
          % (len(reloaded["elements"]), len(bones),
             len(reloaded["animations"]),
             ", ".join(a["name"] for a in reloaded["animations"])))


if __name__ == "__main__":
    main()
