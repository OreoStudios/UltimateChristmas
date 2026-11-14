"""
Reads every generated .nbs back and checks it against the format spec.

Catches the failure modes that matter for NoteBlockAPI playback: a bad header,
a note outside the note block's two playable octaves, a layer index past the
declared layer count, or a truncated/overlong file.

Run:  python tools/verify_music.py
"""

import os
import struct
import sys

MUSIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "..", "src", "main", "resources", "music")

INSTRUMENTS = ["harp", "bass", "basedrum", "snare", "hat", "guitar", "flute",
               "bell", "chime", "xylophone", "iron_xylophone", "cow_bell",
               "didgeridoo", "bit", "banjo", "pling"]


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, count):
        if self.pos + count > len(self.data):
            raise EOFError("read past end of file at byte %d" % self.pos)
        chunk = self.data[self.pos:self.pos + count]
        self.pos += count
        return chunk

    def byte(self):
        return struct.unpack("<b", self.take(1))[0]

    def ubyte(self):
        return struct.unpack("<B", self.take(1))[0]

    def short(self):
        return struct.unpack("<h", self.take(2))[0]

    def int(self):
        return struct.unpack("<i", self.take(4))[0]

    def string(self):
        return self.take(self.int()).decode("ascii", "replace")


def verify(path):
    problems = []
    with open(path, "rb") as fh:
        reader = Reader(fh.read())

    legacy_length = reader.short()
    if legacy_length != 0:
        problems.append("expected new-format marker 0, got %d" % legacy_length)
    version = reader.byte()
    if version != 5:
        problems.append("expected version 5, got %d" % version)
    reader.byte()                                  # vanilla instrument count
    declared_length = reader.short()
    declared_layers = reader.short()
    name = reader.string()
    reader.string()                                # author
    original_author = reader.string()
    reader.string()                                # description
    tempo = reader.short()
    if not 25 <= tempo <= 6000:
        problems.append("implausible tempo %d (ticks/sec * 100)" % tempo)
    reader.byte(); reader.byte(); reader.byte()    # auto-save, interval, time sig
    for _ in range(5):
        reader.int()                               # stats block
    reader.string()                                # imported file name
    loop_on = reader.byte()
    reader.byte()                                  # max loop count
    reader.short()                                 # loop start tick

    notes, tick = [], -1
    while True:
        jump = reader.short()
        if jump == 0:
            break
        tick += jump
        layer = -1
        while True:
            layer_jump = reader.short()
            if layer_jump == 0:
                break
            layer += layer_jump
            instrument = reader.ubyte()
            key = reader.ubyte()
            velocity = reader.ubyte()
            panning = reader.ubyte()
            reader.short()                         # fine pitch
            notes.append((tick, layer, instrument, key, velocity, panning))

    layer_names = []
    for _ in range(declared_layers):
        layer_names.append(reader.string())
        reader.byte()                              # locked
        reader.byte()                              # volume
        reader.ubyte()                             # stereo
    custom_count = reader.byte()
    if custom_count != 0:
        problems.append("unexpected custom instrument count %d" % custom_count)
    if reader.pos != len(reader.data):
        problems.append("%d trailing bytes after end of song"
                        % (len(reader.data) - reader.pos))

    if not notes:
        problems.append("no notes in file")
    actual_length = max((n[0] for n in notes), default=-1) + 1
    if actual_length != declared_length:
        problems.append("header length %d but last tick implies %d"
                        % (declared_length, actual_length))

    max_layer = max((n[1] for n in notes), default=-1)
    if max_layer >= declared_layers:
        problems.append("note on layer %d but only %d layers declared"
                        % (max_layer, declared_layers))

    for tick, layer, instrument, key, velocity, panning in notes:
        if not 33 <= key <= 57:
            problems.append("tick %d layer %d: key %d is outside the playable "
                            "range 33-57 (F#3-F#5)" % (tick, layer, key))
        if not 0 <= instrument <= 15:
            problems.append("tick %d: unknown instrument id %d" % (tick, instrument))
        if not 0 < velocity <= 100:
            problems.append("tick %d: velocity %d out of range" % (tick, velocity))
        if panning != 100:
            problems.append("tick %d: unexpected panning %d" % (tick, panning))

    collisions = {}
    for tick, layer, _, _, _, _ in notes:
        collisions[(tick, layer)] = collisions.get((tick, layer), 0) + 1
    dropped = sum(count - 1 for count in collisions.values() if count > 1)
    if dropped:
        problems.append("%d notes share a tick+layer slot and were lost" % dropped)

    used = sorted({INSTRUMENTS[n[2]] for n in notes})
    return dict(name=name, original=original_author, tempo=tempo,
                seconds=actual_length / (tempo / 100.0), notes=len(notes),
                layers=declared_layers, layer_names=layer_names,
                loop=bool(loop_on), instruments=used, problems=problems)


def main():
    directory = os.path.normpath(MUSIC_DIR)
    files = sorted(f for f in os.listdir(directory) if f.endswith(".nbs"))
    if not files:
        print("no .nbs files found in %s" % directory)
        return 1

    failures = 0
    for filename in files:
        try:
            info = verify(os.path.join(directory, filename))
        except Exception as exc:                            # noqa: BLE001
            print("FAIL %-34s %s: %s" % (filename, type(exc).__name__, exc))
            failures += 1
            continue
        status = "ok  " if not info["problems"] else "FAIL"
        print("%s %-34s %5.1fs %4d notes %d layers  %s"
              % (status, filename, info["seconds"], info["notes"],
                 info["layers"], ",".join(info["instruments"])))
        for problem in info["problems"][:6]:
            print("       - %s" % problem)
        if len(info["problems"]) > 6:
            print("       - ... and %d more" % (len(info["problems"]) - 6))
        if info["problems"]:
            failures += 1

    print("\n%d/%d files valid" % (len(files) - failures, len(files)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
