"""
Minimal writer for the Note Block Studio (.nbs) binary format, version 5.

Format reference: https://opennbs.org/nbs
All integers are little-endian. Strings are a 4-byte length followed by ASCII.

Used by tools/generate_music.py to build the bundled carol soundtrack.
"""

import struct

# ---------------------------------------------------------------- instruments
HARP = 0
DOUBLE_BASS = 1
BASS_DRUM = 2
SNARE = 3
CLICK = 4
GUITAR = 5
FLUTE = 6
BELL = 7
CHIME = 8
XYLOPHONE = 9
IRON_XYLOPHONE = 10
COW_BELL = 11
DIDGERIDOO = 12
BIT = 13
BANJO = 14
PLING = 15

# A note block can only play two octaves, F#3..F#5 in MIDI terms (54..78).
# NBS stores "key" where 0 == A0 == MIDI 21, so key = midi - 21.
KEY_OFFSET = 21
MIN_MIDI = 54
MAX_MIDI = 78

_PITCH_CLASS = {
    "C": 0, "C#": 1, "DB": 1, "D": 2, "D#": 3, "EB": 3, "E": 4, "FB": 4,
    "F": 5, "F#": 6, "GB": 6, "G": 7, "G#": 8, "AB": 8, "A": 9, "A#": 10,
    "BB": 10, "B": 11, "CB": 11,
}


def midi(name):
    """'F#4' -> MIDI note number. Octave 4 is the octave containing middle C."""
    name = name.strip()
    octave_start = len(name)
    while octave_start > 0 and (name[octave_start - 1].isdigit() or name[octave_start - 1] == "-"):
        octave_start -= 1
    letter, octave = name[:octave_start].upper(), int(name[octave_start:])
    if letter not in _PITCH_CLASS:
        raise ValueError("bad note name: %r" % name)
    return _PITCH_CLASS[letter] + (octave + 1) * 12


def fold(note):
    """Fold a MIDI note into the note block's playable two octaves."""
    while note < MIN_MIDI:
        note += 12
    while note > MAX_MIDI:
        note -= 12
    return note


# ---------------------------------------------------------------- note writing
def _str(value):
    data = value.encode("ascii", "replace")
    return struct.pack("<i", len(data)) + data


class Note:
    __slots__ = ("tick", "layer", "instrument", "key", "velocity", "panning")

    def __init__(self, tick, layer, instrument, key, velocity, panning):
        self.tick = tick
        self.layer = layer
        self.instrument = instrument
        self.key = key
        self.velocity = velocity
        self.panning = panning


class Layer:
    def __init__(self, name="", volume=100, panning=100):
        self.name = name
        self.volume = volume
        self.panning = panning  # 0 = left, 100 = centre, 200 = right


class Song:
    """Accumulates notes on a tick/layer grid and serialises to .nbs v5."""

    def __init__(self, name, author="UltimateChristmas", original_author="",
                 description="", bpm=120, ticks_per_beat=4, loop=True):
        self.name = name
        self.author = author
        self.original_author = original_author
        self.description = description
        self.ticks_per_beat = ticks_per_beat
        # NBS stores tempo as ticks-per-second * 100.
        self.tempo = int(round(bpm / 60.0 * ticks_per_beat * 100))
        self.loop = loop
        self.notes = []
        self.layers = []
        self.collisions = []
        self._slots = set()

    def layer(self, name="", volume=100, panning=100):
        self.layers.append(Layer(name, volume, panning))
        return len(self.layers) - 1

    def add(self, tick, layer, instrument, note, velocity=100, panning=100):
        """Place one note. A layer holds at most one note per tick, so a second
        note on the same tick+layer would be silently dropped by the format -
        record it instead of losing it quietly."""
        if note is None:
            return
        tick = int(tick)
        if (tick, layer) in self._slots:
            self.collisions.append((tick, layer))
            return
        self._slots.add((tick, layer))
        key = fold(note if isinstance(note, int) else midi(note)) - KEY_OFFSET
        self.notes.append(Note(tick, layer, instrument, key, velocity, panning))

    @property
    def length(self):
        return max((n.tick for n in self.notes), default=0) + 1

    def to_bytes(self):
        header = b""
        header += struct.pack("<h", 0)              # legacy length marker -> new format
        header += struct.pack("<b", 5)              # nbs version
        header += struct.pack("<b", 16)             # vanilla instrument count
        header += struct.pack("<h", self.length)
        header += struct.pack("<h", max(1, len(self.layers)))
        header += _str(self.name)
        header += _str(self.author)
        header += _str(self.original_author)
        header += _str(self.description)
        header += struct.pack("<h", self.tempo)
        header += struct.pack("<b", 0)              # auto-save off
        header += struct.pack("<b", 10)             # auto-save interval
        header += struct.pack("<b", 4)              # time signature
        header += struct.pack("<i", 0)              # minutes spent
        header += struct.pack("<i", 0)              # left clicks
        header += struct.pack("<i", 0)              # right clicks
        header += struct.pack("<i", len(self.notes))
        header += struct.pack("<i", 0)              # note blocks removed
        header += _str("")                          # imported midi file name
        header += struct.pack("<b", 1 if self.loop else 0)
        header += struct.pack("<b", 0)              # 0 = loop forever
        header += struct.pack("<h", 0)              # loop start tick

        # Notes are stored as a sparse grid walked with "jumps to next" counters.
        grid = {}
        for n in self.notes:
            grid.setdefault(n.tick, {})[n.layer] = n

        body = b""
        prev_tick = -1
        for tick in sorted(grid):
            body += struct.pack("<h", tick - prev_tick)
            prev_tick = tick
            prev_layer = -1
            for layer in sorted(grid[tick]):
                n = grid[tick][layer]
                body += struct.pack("<h", layer - prev_layer)
                prev_layer = layer
                body += struct.pack("<b", n.instrument)
                body += struct.pack("<b", n.key)
                body += struct.pack("<B", n.velocity)
                body += struct.pack("<B", n.panning)
                body += struct.pack("<h", 0)        # fine pitch offset
            body += struct.pack("<h", 0)            # end of layers for this tick
        body += struct.pack("<h", 0)                # end of song

        layers = b""
        for lay in (self.layers or [Layer()]):
            layers += _str(lay.name)
            layers += struct.pack("<b", 0)          # not locked
            layers += struct.pack("<b", lay.volume)
            layers += struct.pack("<B", lay.panning)

        custom_instruments = struct.pack("<b", 0)

        return header + body + layers + custom_instruments

    def write(self, path):
        with open(path, "wb") as fh:
            fh.write(self.to_bytes())
        return len(self.notes)


# ---------------------------------------------------------------- sequencing
class Seq:
    """A cursor that lays notes onto a Song, advancing in ticks.

    A .nbs layer holds at most one note per tick, so chords and overlapping
    notes need one layer per simultaneous voice. The cursor owns a growing
    stack of layers and spills onto the next free one as needed, which keeps
    callers free to just write music.
    """

    def __init__(self, song, instrument, name="", volume=100, panning=100, octave=0):
        self.song = song
        self.instrument = instrument
        self.name = name
        self.volume = volume
        self.panning = panning
        self.layers = [song.layer(name, volume, panning)]
        self.octave = octave
        self.t = 0

    @property
    def layer(self):
        return self.layers[0]

    def _free_layer(self, tick):
        """The first of this cursor's layers with nothing at `tick`."""
        for index in self.layers:
            if (tick, index) not in self.song._slots:
                return index
        extra = self.song.layer("%s %d" % (self.name or "voice", len(self.layers) + 1),
                                self.volume, self.panning)
        self.layers.append(extra)
        return extra

    def _put(self, tick, pitch, velocity):
        value = (pitch if isinstance(pitch, int) else midi(pitch)) + 12 * self.octave
        self.song.add(tick, self._free_layer(tick), self.instrument, value, velocity)

    # -- cursor control
    def at(self, tick):
        self.t = tick
        return self

    def rest(self, dur):
        self.t += dur
        return self

    # -- note entry
    def note(self, pitch, dur, velocity=100, sustain=False):
        """Play `pitch` for `dur` ticks. `pitch` may be a name, MIDI int, or None (rest)."""
        if pitch is not None:
            self._put(self.t, pitch, velocity)
            if sustain and dur >= 8:
                # Note blocks decay fast; re-strike long notes quietly so they ring.
                self._put(self.t + dur // 2, pitch, max(20, velocity - 40))
        self.t += dur
        return self

    def chord(self, pitches, dur, velocity=100):
        for pitch in pitches:
            if pitch is not None:
                self._put(self.t, pitch, velocity)
        self.t += dur
        return self

    def phrase(self, spec, velocity=100, sustain=False):
        """Lay a compact phrase: "E4:4 E4:4 E4:8 -:4" (pitch:ticks, '-' = rest)."""
        for token in spec.split():
            pitch, _, dur = token.partition(":")
            dur = int(dur) if dur else self.song.ticks_per_beat
            self.note(None if pitch == "-" else pitch, dur, velocity, sustain)
        return self
