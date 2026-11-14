"""
Generates the bundled .nbs soundtrack for UltimateChristmas.

Every carol here is a traditional/classical melody in the public domain (all
published before 1900); the note block arrangements are written from scratch by
this script, so the resulting files are safe to redistribute with the plugin.
Four original cues (workshop / battle / fanfare / sleigh bells) round out the set.

Run:  python tools/generate_music.py
Out:  src/main/resources/music/*.nbs  +  src/main/resources/music.yml
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import nbs
from nbs import (Song, Seq, midi, HARP, DOUBLE_BASS, GUITAR, BELL, CHIME,
                 XYLOPHONE, IRON_XYLOPHONE, FLUTE, PLING, BIT, BANJO,
                 COW_BELL, CLICK, DIDGERIDOO, SNARE, BASS_DRUM)

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "src", "main", "resources", "music")

PITCH_CLASS = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6,
               "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11,
               "DB": 1, "EB": 3, "GB": 6, "AB": 8, "BB": 10}


# --------------------------------------------------------------------------
# melody notation
# --------------------------------------------------------------------------
def parse(block, bar):
    """Parse a bar-per-line melody block into [(tick, pitch|None, dur)].

    Each non-empty line must add up to exactly `bar` ticks, which turns a
    miscounted rhythm into a loud failure instead of a subtly wrong song.
    """
    events, tick = [], 0
    for lineno, line in enumerate(block.strip().splitlines(), start=1):
        # No comment syntax here on purpose: '#' is part of note names (F#4).
        line = line.strip()
        if not line:
            continue
        start, total = tick, 0
        for token in line.split():
            pitch, _, dur = token.partition(":")
            dur = int(dur) if dur else 4
            events.append((tick, None if pitch == "-" else pitch, dur))
            tick += dur
            total += dur
        if total != bar:
            raise ValueError("bar %d is %d ticks, expected %d: %r"
                             % (lineno, total, bar, line))
        assert tick == start + bar
    return events


def play(seq, events, velocity=100, sustain=True):
    for tick, pitch, dur in events:
        seq.at(tick).note(pitch, dur, velocity, sustain=sustain)
    return max((t + d for t, _, d in events), default=0)


# --------------------------------------------------------------------------
# automatic harmonisation
# --------------------------------------------------------------------------
TRIADS = {"maj": (0, 4, 7), "min": (0, 3, 7), "dom7": (0, 4, 7, 10),
          "dim": (0, 3, 6)}

MAJOR_CHORDS = [(0, "maj", 1.00), (7, "maj", 0.95), (5, "maj", 0.90),
                (9, "min", 0.80), (2, "min", 0.75), (4, "min", 0.70),
                (7, "dom7", 0.85)]

MINOR_CHORDS = [(0, "min", 1.00), (5, "min", 0.88), (7, "min", 0.82),
                (8, "maj", 0.85), (3, "maj", 0.85), (10, "maj", 0.80),
                (7, "maj", 0.78)]


def _classes(root_pc, quality):
    return {(root_pc + iv) % 12 for iv in TRIADS[quality]}


def pick_chords(events, bar, key, minor=False):
    """Choose one chord per bar by how well it fits that bar's melody notes."""
    key_pc = PITCH_CLASS[key.upper()]
    palette = [((key_pc + deg) % 12, qual, pref)
               for deg, qual, pref in (MINOR_CHORDS if minor else MAJOR_CHORDS)]

    by_bar = {}
    for tick, pitch, dur in events:
        if pitch is not None:
            by_bar.setdefault(tick // bar, []).append((midi(pitch) % 12, dur, tick % bar))

    bars = (max((t for t, _, _ in events), default=0) // bar) + 1
    chords, previous = [], None
    for index in range(bars):
        notes = by_bar.get(index, [])
        if not notes:
            chords.append(previous or palette[0][:2])
            continue
        best, best_score = None, None
        for root_pc, quality, pref in palette:
            members = _classes(root_pc, quality)
            score = 0.0
            for pc, dur, offset in notes:
                weight = dur * (1.6 if offset == 0 else 1.0)
                score += weight * (1.0 if pc in members else -1.1)
            score *= pref
            if previous is not None and (root_pc, quality) == previous:
                score += 1.0          # mild inertia, fewer chord changes per bar
            if best_score is None or score > best_score:
                best, best_score = (root_pc, quality), score
        chords.append(best)
        previous = best
    return chords


def _voice(pc, low, high):
    note = pc + 12 * (low // 12)
    while note < low:
        note += 12
    while note > high:
        note -= 12
    return note


def harmonise(song, events, bar, key, minor=False, style="march",
              bass_instrument=DOUBLE_BASS, pad_instrument=GUITAR,
              bass_velocity=95, pad_velocity=55):
    """Add a bass line and chord pad underneath an already-written melody."""
    chords = pick_chords(events, bar, key, minor)
    bass = Seq(song, bass_instrument, "bass", volume=90)
    pad = Seq(song, pad_instrument, "chords", volume=70)

    if style == "waltz":          # 3/4: root, chord, chord
        bass_hits, pad_hits = [0], [bar // 3, 2 * bar // 3]
    elif style == "gentle":
        bass_hits, pad_hits = [0], [0, bar // 2]
    elif style == "driving":
        bass_hits, pad_hits = list(range(0, bar, bar // 8)), [0, bar // 2]
    elif style == "ballad":
        bass_hits, pad_hits = [0, bar // 2], [bar // 4, 3 * bar // 4]
    else:                         # march: 4/4 root on 1 & 3, chord on 2 & 4
        bass_hits, pad_hits = [0, bar // 2], [bar // 4, 3 * bar // 4]

    for index, (root_pc, quality) in enumerate(chords):
        origin = index * bar
        root = _voice(root_pc, 54, 65)
        fifth = _voice((root_pc + 7) % 12, 54, 65)
        # Root on the strong hit, fifth on the weaker ones: a plain oom-pah bass.
        for position, offset in enumerate(bass_hits):
            pitch = root if position % 2 == 0 else fifth
            bass.at(origin + offset).note(pitch, 1,
                                          bass_velocity if position % 2 == 0
                                          else bass_velocity - 12)
        voices = [_voice(root_pc + iv, 60, 74) for iv in TRIADS[quality]]
        for offset in pad_hits:
            pad.at(origin + offset).chord(voices, 1, pad_velocity)
    return chords


def sparkle(song, events, bar, every=None, instrument=BELL, velocity=55):
    """Double the melody's downbeat notes an octave up on a bright instrument."""
    seq = Seq(song, instrument, "sparkle", volume=55)
    for tick, pitch, dur in events:
        if pitch is None or tick % bar != 0:
            continue
        if every and (tick // bar) % every != 0:
            continue
        seq.at(tick).note(midi(pitch) + 12, 1, velocity)


def jingle(song, bar, bars, step=2, instrument=COW_BELL, velocity=45):
    """A sleigh-bell shimmer: light hits on every eighth."""
    seq = Seq(song, instrument, "sleigh bells", volume=50)
    for tick in range(0, bar * bars, step):
        strong = (tick % bar) == 0
        seq.at(tick).note("C5" if strong else "G4", 1,
                          velocity + (18 if strong else 0))


# --------------------------------------------------------------------------
# the carols
# --------------------------------------------------------------------------
CAROLS = []


def carol(key_name, title, original, bpm, bar, key, melody, *, minor=False,
          style="march", lead=HARP, pad=GUITAR, bells=None, sleigh=False,
          repeat=1, description=None):
    CAROLS.append(dict(key_name=key_name, title=title, original=original,
                       bpm=bpm, bar=bar, key=key, melody=melody, minor=minor,
                       style=style, lead=lead, pad=pad, bells=bells,
                       sleigh=sleigh, repeat=repeat, description=description))


carol("jingle_bells", "&e&lJingle Bells", "J. L. Pierpont, 1857",
      bpm=132, bar=16, key="C", style="march", lead=HARP, bells=2, sleigh=True,
      melody="""
      G4:4 E4:4 D4:4 C4:4
      G4:12 -:4
      A4:4 F4:4 E4:4 D4:4
      A4:12 -:4
      B4:4 G4:4 F4:4 E4:4
      C5:8 C5:4 C5:4
      C5:4 B4:4 A4:4 D5:4
      D5:8 -:8
      E4:4 E4:4 E4:8
      E4:4 E4:4 E4:8
      E4:4 G4:4 C4:4 D4:4
      E4:16
      F4:4 F4:4 F4:4 F4:4
      F4:4 E4:4 E4:4 E4:4
      E4:4 D4:4 D4:4 E4:4
      D4:8 G4:8
      E4:4 E4:4 E4:8
      E4:4 E4:4 E4:8
      E4:4 G4:4 C4:4 D4:4
      E4:16
      F4:4 F4:4 F4:4 F4:4
      F4:4 E4:4 E4:4 E4:4
      G4:4 G4:4 F4:4 D4:4
      C4:16
      """)

carol("deck_the_halls", "&a&lDeck the Halls", "Welsh traditional, 'Nos Galan'",
      bpm=138, bar=16, key="C", style="march", lead=PLING, bells=4,
      melody="""
      G4:4 F4:4 E4:4 D4:4
      C4:4 D4:4 E4:4 C4:4
      D4:4 E4:4 F4:4 E4:4
      D4:4 C4:4 B3:4 C4:4
      E4:4 F4:4 G4:4 A4:4
      G4:4 F4:4 E4:4 D4:4
      C4:4 D4:4 E4:4 C4:4
      D4:4 C4:4 B3:4 C4:4
      D4:4 E4:4 F4:4 D4:4
      E4:4 F4:4 G4:4 E4:4
      F4:4 G4:4 A4:4 B4:4
      C5:8 -:8
      E4:4 F4:4 G4:4 A4:4
      G4:4 F4:4 E4:4 D4:4
      C4:4 D4:4 E4:4 C4:4
      D4:4 C4:4 B3:4 C4:4
      C4:16
      """)

carol("we_wish_you_a_merry_christmas", "&c&lWe Wish You a Merry Christmas",
      "English traditional, West Country",
      bpm=150, bar=12, key="C", style="waltz", lead=BANJO, bells=4,
      melody="""
      -:8 C4:4
      F4:4 F4:4 G4:4
      F4:4 E4:4 D4:4
      D4:8 D4:4
      G4:4 G4:4 A4:4
      G4:4 F4:4 E4:4
      C4:8 C4:4
      A4:4 A4:4 B4:4
      A4:4 G4:4 F4:4
      D4:8 C4:4
      D4:4 G4:4 E4:4
      F4:8 -:4
      -:8 C4:4
      F4:4 F4:4 G4:4
      F4:4 E4:4 D4:4
      D4:8 D4:4
      G4:4 G4:4 A4:4
      G4:4 F4:4 E4:4
      C4:8 C4:4
      A4:4 A4:4 B4:4
      A4:4 G4:4 F4:4
      D4:8 C4:4
      D4:4 G4:4 E4:4
      F4:12
      """)

carol("silent_night", "&b&lSilent Night", "Franz Xaver Gruber, 1818",
      bpm=104, bar=12, key="C", style="gentle", lead=FLUTE, pad=HARP, bells=None,
      melody="""
      G4:6 A4:2 G4:4
      E4:12
      G4:6 A4:2 G4:4
      E4:12
      D5:8 D5:4
      B4:12
      C5:8 C5:4
      G4:12
      A4:8 A4:4
      C5:6 B4:2 A4:4
      G4:6 A4:2 G4:4
      E4:12
      A4:8 A4:4
      C5:6 B4:2 A4:4
      G4:6 A4:2 G4:4
      E4:12
      D5:8 D5:4
      F5:6 D5:2 B4:4
      C5:12
      E5:12
      C5:6 G4:2 E4:4
      G4:6 F4:2 D4:4
      C4:12
      -:12
      """)

carol("joy_to_the_world", "&6&lJoy to the World", "Lowell Mason / Handel, 1839",
      bpm=120, bar=16, key="C", style="march", lead=PLING, bells=2,
      melody="""
      C5:6 B4:2 A4:4 G4:4
      G4:6 F4:2 E4:4 D4:4
      C4:8 -:4 G4:4
      A4:6 A4:2 B4:4 B4:4
      C5:12 C5:4
      C5:4 C5:4 C5:4 B4:4
      A4:4 A4:4 A4:4 G4:4
      G4:6 F4:2 E4:8
      E4:4 E4:4 E4:4 F4:4
      E4:4 D4:4 C4:8
      C4:4 D4:4 D4:4 E4:4
      D4:4 C4:4 B3:4 C4:4
      C4:6 C4:2 C4:4 D4:4
      E4:6 D4:2 C4:4 B3:4
      C4:16
      -:16
      """)

carol("hark_the_herald_angels_sing", "&d&lHark! The Herald Angels Sing",
      "Felix Mendelssohn, 1840",
      bpm=116, bar=16, key="F", style="march", lead=HARP, bells=2,
      melody="""
      C4:4 C4:4 F4:6 F4:2
      A4:4 A4:4 G4:8
      C5:4 C5:4 Bb4:6 Bb4:2
      A4:4 G4:4 F4:8
      C4:4 C4:4 F4:6 F4:2
      A4:4 A4:4 G4:8
      C5:4 C5:4 Bb4:6 Bb4:2
      A4:4 G4:4 F4:8
      A4:4 A4:4 Bb4:4 Bb4:4
      C5:6 C5:2 Bb4:4 A4:4
      A4:4 A4:4 Bb4:4 Bb4:4
      C5:6 C5:2 Bb4:4 A4:4
      C5:4 C5:4 F5:6 F5:2
      D5:4 D5:4 C5:8
      C5:4 C5:4 Bb4:6 Bb4:2
      A4:4 G4:4 F4:8
      F4:16
      """)

carol("o_come_all_ye_faithful", "&e&lO Come, All Ye Faithful",
      "John Francis Wade, c. 1751",
      bpm=108, bar=16, key="G", style="ballad", lead=HARP, bells=4,
      melody="""
      G4:8 D4:4 G4:4
      A4:12 D4:4
      B4:8 A4:4 B4:4
      C5:8 B4:4 A4:4
      G4:8 D4:4 G4:4
      A4:8 B4:4 A4:4
      G4:8 F#4:4 E4:4
      D4:12 D4:4
      G4:8 A4:4 B4:4
      C5:8 B4:4 A4:4
      B4:8 A4:4 G4:4
      F#4:12 -:4
      D5:8 D5:4 C5:4
      B4:8 C5:4 B4:4
      A4:8 B4:4 A4:4
      G4:8 D4:4 G4:4
      A4:8 B4:4 A4:4
      G4:8 F#4:4 E4:4
      D4:4 E4:4 F#4:4 G4:4
      D5:8 C5:4 B4:4
      C5:8 B4:4 A4:4
      G4:16
      """)

carol("the_first_noel", "&a&lThe First Noel", "English traditional, Cornwall",
      bpm=112, bar=12, key="C", style="waltz", lead=FLUTE, pad=HARP, bells=4,
      melody="""
      -:4 E4:4 D4:4
      C4:4 D4:4 E4:4
      F4:4 G4:4 A4:4
      G4:8 F4:4
      E4:4 F4:4 G4:4
      A4:4 G4:4 F4:4
      E4:4 D4:4 C4:4
      D4:12
      C4:4 D4:4 E4:4
      F4:4 G4:4 A4:4
      G4:8 F4:4
      E4:4 F4:4 G4:4
      A4:4 G4:4 F4:4
      E4:4 D4:4 C4:4
      D4:12
      -:12
      C5:8 C5:4
      B4:4 A4:4 G4:4
      A4:4 B4:4 C5:4
      G4:8 F4:4
      E4:4 F4:4 G4:4
      A4:4 G4:4 F4:4
      E4:4 D4:4 C4:4
      D4:12
      C4:12
      """)

carol("god_rest_ye_merry_gentlemen", "&2&lGod Rest Ye Merry, Gentlemen",
      "English traditional, c. 1760",
      bpm=128, bar=16, key="E", minor=True, style="march", lead=BANJO,
      pad=HARP, bells=2,
      melody="""
      E4:4 E4:4 B4:4 B4:4
      C5:4 B4:4 A4:4 G4:4
      F#4:4 E4:4 D4:4 E4:4
      F#4:8 -:8
      G4:4 G4:4 D5:4 D5:4
      E5:4 D5:4 C5:4 B4:4
      A4:4 G4:4 F#4:4 G4:4
      A4:8 -:8
      B4:4 B4:4 C5:4 D5:4
      C5:4 B4:4 A4:4 B4:4
      C5:4 B4:4 A4:4 G4:4
      F#4:8 E4:8
      D5:8 D5:4 C5:4
      B4:4 A4:4 G4:4 F#4:4
      E4:4 G4:4 B4:4 E5:4
      D5:4 C5:4 B4:4 A4:4
      G4:4 F#4:4 E4:8
      -:16
      """)

carol("good_king_wenceslas", "&6&lGood King Wenceslas",
      "'Tempus Adest Floridum', 1582",
      bpm=124, bar=16, key="G", style="march", lead=PLING, bells=4,
      melody="""
      G4:4 G4:4 G4:4 A4:4
      G4:4 G4:4 D4:8
      E4:4 D4:4 E4:4 F#4:4
      G4:8 -:8
      G4:4 G4:4 G4:4 A4:4
      G4:4 G4:4 D4:8
      E4:4 D4:4 E4:4 F#4:4
      G4:8 -:8
      D5:4 C5:4 B4:4 A4:4
      B4:4 A4:4 G4:8
      E4:4 G4:4 G4:4 F#4:4
      G4:8 -:8
      G4:4 A4:4 B4:4 A4:4
      G4:4 F#4:4 E4:4 D4:4
      E4:4 D4:4 E4:4 F#4:4
      G4:16
      """)

carol("away_in_a_manger", "&b&lAway in a Manger",
      "William J. Kirkpatrick, 1895",
      bpm=100, bar=12, key="F", style="gentle", lead=FLUTE, pad=HARP, bells=4,
      melody="""
      -:8 C4:4
      F4:4 F4:4 G4:4
      A4:8 F4:4
      F4:4 A4:4 Bb4:4
      C5:8 A4:4
      A4:4 Bb4:4 C5:4
      D5:8 Bb4:4
      Bb4:4 A4:4 G4:4
      F4:8 C4:4
      F4:4 F4:4 G4:4
      A4:8 F4:4
      F4:4 A4:4 Bb4:4
      C5:8 A4:4
      A4:4 Bb4:4 C5:4
      D5:8 Bb4:4
      Bb4:4 A4:4 G4:4
      F4:12
      -:12
      """)

carol("up_on_the_housetop", "&c&lUp on the Housetop",
      "Benjamin Hanby, 1864",
      bpm=140, bar=16, key="C", style="march", lead=XYLOPHONE, bells=2, sleigh=True,
      melody="""
      C5:4 C5:4 C5:4 C5:4
      A4:4 A4:4 A4:8
      F4:4 F4:4 F4:4 F4:4
      C4:4 C4:4 C4:8
      C5:4 C5:4 C5:4 C5:4
      A4:4 A4:4 A4:8
      F4:4 F4:4 F4:4 F4:4
      G4:4 G4:4 C4:8
      G4:4 G4:4 G4:8
      E4:4 E4:4 E4:8
      G4:4 G4:4 G4:4 G4:4
      E4:4 E4:4 E4:8
      F4:4 F4:4 F4:4 F4:4
      C4:4 C4:4 C4:8
      G4:4 E4:4 G4:4 C5:4
      C4:16
      """)

carol("jolly_old_saint_nicholas", "&e&lJolly Old Saint Nicholas",
      "American traditional, 1865",
      bpm=126, bar=16, key="C", style="march", lead=BANJO, bells=4,
      melody="""
      E4:4 E4:4 E4:4 C4:4
      E4:4 E4:4 E4:4 C4:4
      G4:4 G4:4 G4:4 E4:4
      C5:4 C5:4 G4:8
      E4:4 E4:4 E4:4 C4:4
      E4:4 E4:4 E4:4 C4:4
      G4:4 G4:4 G4:4 E4:4
      C5:8 -:8
      C5:4 C5:4 C5:4 A4:4
      A4:4 A4:4 F4:4 F4:4
      G4:4 G4:4 G4:4 E4:4
      E4:4 C4:4 D4:8
      D4:4 D4:4 E4:4 F4:4
      F4:4 E4:4 D4:4 C4:4
      E4:4 G4:4 C5:4 G4:4
      C4:16
      """)

carol("angels_we_have_heard_on_high", "&d&lAngels We Have Heard on High",
      "French traditional, 'Gloria'",
      bpm=112, bar=16, key="F", style="ballad", lead=HARP, bells=2,
      melody="""
      F4:4 F4:4 F4:4 F4:4
      A4:4 G4:4 F4:8
      G4:4 G4:4 G4:4 G4:4
      Bb4:4 A4:4 G4:8
      A4:4 A4:4 A4:4 A4:4
      C5:4 Bb4:4 A4:8
      G4:4 G4:4 A4:4 Bb4:4
      A4:4 G4:4 F4:8
      C5:2 C5:2 Bb4:2 A4:2 G4:2 A4:2 Bb4:2 A4:2
      G4:2 A4:2 Bb4:2 A4:2 G4:4 F4:4
      F4:4 G4:4 A4:4 G4:4
      F4:8 -:8
      C5:2 C5:2 Bb4:2 A4:2 G4:2 A4:2 Bb4:2 A4:2
      G4:2 A4:2 Bb4:2 C5:2 Bb4:4 A4:4
      Bb4:4 A4:4 G4:4 A4:4
      F4:16
      """)

carol("o_christmas_tree", "&a&lO Christmas Tree",
      "German traditional, 'O Tannenbaum'",
      bpm=108, bar=12, key="G", style="waltz", lead=HARP, bells=4,
      melody="""
      -:8 D4:4
      G4:8 G4:4
      G4:8 A4:4
      B4:8 B4:4
      B4:8 -:4
      -:4 A4:4 B4:4
      C5:8 C5:4
      B4:8 A4:4
      G4:8 D5:4
      D5:4 B4:4 D5:4
      D5:8 C5:4
      C5:4 A4:4 C5:4
      C5:8 B4:4
      G4:8 A4:4
      B4:8 -:4
      -:4 A4:4 B4:4
      C5:8 C5:4
      B4:8 A4:4
      G4:12
      -:12
      """)

carol("ding_dong_merrily_on_high", "&6&lDing Dong Merrily on High",
      "'Branle de l'Official', 1588",
      bpm=134, bar=16, key="G", style="march", lead=BELL, pad=HARP, bells=None,
      melody="""
      D5:4 D5:4 C5:4 B4:4
      A4:4 B4:4 C5:8
      B4:4 B4:4 A4:4 G4:4
      A4:4 B4:4 G4:8
      D5:4 D5:4 C5:4 B4:4
      A4:4 B4:4 C5:8
      B4:4 B4:4 A4:4 G4:4
      F#4:4 E4:4 D4:8
      G4:2 A4:2 B4:2 A4:2 G4:2 A4:2 B4:2 A4:2
      G4:2 A4:2 B4:2 C5:2 B4:4 A4:4
      B4:2 C5:2 D5:2 C5:2 B4:2 A4:2 G4:2 F#4:2
      G4:8 -:8
      D5:4 C5:4 B4:4 A4:4
      B4:4 A4:4 G4:4 F#4:4
      G4:4 A4:4 B4:4 D5:4
      G4:16
      """)

carol("coventry_carol", "&5&lCoventry Carol",
      "English traditional, 1534",
      bpm=96, bar=12, key="A", minor=True, style="gentle", lead=FLUTE,
      pad=HARP, bells=None,
      melody="""
      A4:4 A4:4 G4:4
      A4:8 E4:4
      E4:4 E4:4 F4:4
      E4:12
      A4:4 A4:4 G4:4
      A4:8 C5:4
      C5:4 B4:4 A4:4
      A4:12
      C5:4 C5:4 C5:4
      D5:8 C5:4
      B4:4 A4:4 G#4:4
      A4:12
      A4:4 B4:4 C5:4
      B4:8 A4:4
      G#4:4 A4:4 B4:4
      A4:12
      -:12
      """)

carol("i_saw_three_ships", "&b&lI Saw Three Ships",
      "English traditional, 17th century",
      bpm=144, bar=12, key="C", style="waltz", lead=BANJO, bells=4, sleigh=True,
      melody="""
      -:8 C4:4
      F4:4 F4:4 F4:4
      F4:4 G4:4 A4:4
      A4:4 G4:4 F4:4
      G4:8 C4:4
      F4:4 F4:4 F4:4
      F4:4 G4:4 A4:4
      A4:4 G4:4 F4:4
      F4:8 C5:4
      C5:4 C5:4 C5:4
      C5:4 Bb4:4 A4:4
      Bb4:4 A4:4 G4:4
      A4:8 C4:4
      F4:4 F4:4 F4:4
      F4:4 G4:4 A4:4
      A4:4 G4:4 F4:4
      F4:12
      """)


# --------------------------------------------------------------------------
# hand-built pieces: bespoke arrangements the generic pipeline can't express
# --------------------------------------------------------------------------
def build_carol_of_the_bells():
    """Shchedryk (Leontovych, 1916) — one four-note cell over shifting harmony."""
    song = Song("Carol of the Bells", original_author="Mykola Leontovych, 1916",
                description="Shchedryk. Public domain melody, arrangement by UltimateChristmas.",
                bpm=150, ticks_per_beat=4)
    bar, bars = 12, 32

    # The famous ostinato: quarter, eighth, eighth, quarter, held all the way through.
    cell = Seq(song, BELL, "ostinato", volume=85)
    for index in range(bars):
        cell.at(index * bar)
        cell.note("C5", 4, 92).note("B4", 2, 70).note("C5", 2, 76).note("A4", 4, 86)

    progression = (["Am"] * 8 + ["Am", "Am", "G", "G"] + ["Am", "Am", "E", "E"] +
                   ["F", "F", "C", "C"] + ["Dm", "Dm", "E", "E"] + ["Am"] * 4)
    roots = {"Am": ("A", "min"), "G": ("G", "maj"), "E": ("E", "maj"),
             "F": ("F", "maj"), "C": ("C", "maj"), "Dm": ("D", "min")}

    bass = Seq(song, DOUBLE_BASS, "bass", volume=92)
    pad = Seq(song, HARP, "chords", volume=62)
    for index in range(bars):
        name = progression[index % len(progression)]
        root_name, quality = roots[name]
        root_pc = PITCH_CLASS[root_name]
        origin = index * bar
        bass.at(origin).note(_voice(root_pc, 54, 65), 1, 96)
        bass.at(origin + 8).note(_voice((root_pc + 7) % 12, 54, 65), 1, 72)
        voices = [_voice(root_pc + iv, 60, 74) for iv in TRIADS[quality]]
        pad.at(origin + 4).chord(voices, 1, 58)
        pad.at(origin + 8).chord(voices, 1, 48)

    # Counter-melody that climbs over the second half, as in the choral setting.
    counter = Seq(song, CHIME, "counter", volume=70)
    counter.at(16 * bar).phrase(
        "A4:4 B4:4 C5:4  D5:4 C5:4 B4:4  A4:8 -:4  E5:4 D5:4 C5:4", 72)
    counter.at(24 * bar).phrase(
        "C5:4 D5:4 E5:4  F5:4 E5:4 D5:4  C5:8 A4:4  A4:12", 76)

    song.notes.sort(key=lambda n: (n.tick, n.layer))
    return song, "&f&lCarol of the Bells", bars * bar


def build_workshop_theme():
    """Original: bouncy elf-workshop loop for the shop and menus."""
    song = Song("Santa's Workshop", original_author="UltimateChristmas",
                description="Original festive loop.", bpm=146, ticks_per_beat=4)
    bar = 16
    melody = parse("""
        C5:2 E5:2 G4:2 C5:2  E5:2 D5:2 C5:4
        D5:2 F5:2 A4:2 D5:2  F5:2 E5:2 D5:4
        E5:2 C5:2 G4:2 E5:2  D5:2 C5:2 B4:4
        A4:2 C5:2 E5:2 D5:2  C5:4 G4:4
        C5:2 E5:2 G4:2 C5:2  E5:2 G4:2 E5:4
        F5:2 E5:2 D5:2 C5:2  B4:2 C5:2 D5:4
        E5:2 D5:2 C5:2 B4:2  A4:2 G4:2 A4:4
        B4:2 C5:2 D5:2 E5:2  C5:8
        """, bar)
    lead = Seq(song, XYLOPHONE, "lead", volume=95)
    play(lead, melody, 92, sustain=False)
    harmonise(song, melody, bar, "C", style="driving",
              bass_instrument=DOUBLE_BASS, pad_instrument=BIT, pad_velocity=48)

    hats = Seq(song, CLICK, "hats", volume=38)
    for tick in range(0, bar * 8, 2):
        hats.at(tick).note("F#4", 1, 30 if tick % 4 else 44)
    song.notes.sort(key=lambda n: (n.tick, n.layer))
    return song, "&6&lSanta's Workshop", bar * 8


def build_battle_theme():
    """Original: driving minor cue for the Grinch / Krampus fights."""
    song = Song("Winter Siege", original_author="UltimateChristmas",
                description="Original boss battle cue.", bpm=168, ticks_per_beat=4)
    bar = 16
    melody = parse("""
        A4:2 A4:2 C5:2 A4:2  E5:4 D5:4
        C5:2 C5:2 E5:2 C5:2  A4:4 G4:4
        F4:2 F4:2 A4:2 F4:2  C5:4 Bb4:4
        A4:2 G4:2 F4:2 E4:2  A4:8
        A4:2 A4:2 C5:2 A4:2  E5:4 F5:4
        E5:2 D5:2 C5:2 B4:2  C5:4 A4:4
        D5:2 C5:2 B4:2 A4:2  G#4:4 B4:4
        A4:4 E5:4 A4:8
        """, bar)
    lead = Seq(song, BIT, "lead", volume=100)
    play(lead, melody, 100, sustain=False)
    harmonise(song, melody, bar, "A", minor=True, style="driving",
              bass_instrument=DIDGERIDOO, pad_instrument=IRON_XYLOPHONE,
              bass_velocity=100, pad_velocity=52)

    drums = Seq(song, BASS_DRUM, "kick", volume=90)
    snare = Seq(song, SNARE, "snare", volume=70)
    for index in range(8):
        origin = index * bar
        for offset in (0, 6, 8, 14):
            drums.at(origin + offset).note("F#4", 1, 95)
        for offset in (4, 12):
            snare.at(origin + offset).note("F#4", 1, 72)
    song.notes.sort(key=lambda n: (n.tick, n.layer))
    return song, "&2&lWinter Siege", bar * 8


def build_fanfare():
    """Original: four-bar bell fanfare for announcements and boss spawns."""
    song = Song("Festive Fanfare", original_author="UltimateChristmas",
                description="Original short fanfare.", bpm=120, ticks_per_beat=4,
                loop=False)
    bar = 16
    melody = parse("""
        C5:2 E5:2 G4:4 C5:4 E5:4
        D5:2 F5:2 A4:4 D5:8
        E5:2 D5:2 C5:2 B4:2 C5:8
        G4:4 C5:4 E5:8
        """, bar)
    lead = Seq(song, BELL, "fanfare", volume=100)
    play(lead, melody, 100)
    harmonise(song, melody, bar, "C", style="march", pad_instrument=CHIME,
              pad_velocity=60)
    song.notes.sort(key=lambda n: (n.tick, n.layer))
    return song, "&e&lFestive Fanfare", bar * 4


def build_sleigh_bells():
    """Original: sparse sleigh-bell ambience loop for background atmosphere."""
    song = Song("Sleigh Bells", original_author="UltimateChristmas",
                description="Original ambience loop.", bpm=96, ticks_per_beat=4)
    bar, bars = 16, 8
    jingle(song, bar, bars, step=2, instrument=COW_BELL, velocity=34)

    pad = Seq(song, CHIME, "chimes", volume=50)
    figure = ["C5", "E5", "G4", "E5", "A4", "C5", "G4", "C5"]
    for index in range(bars):
        pad.at(index * bar).note(figure[index % len(figure)], 1, 46)
        pad.at(index * bar + 10).note(figure[(index + 3) % len(figure)], 1, 34)

    bass = Seq(song, DOUBLE_BASS, "bass", volume=70)
    for index in range(bars):
        bass.at(index * bar).note("C4" if index % 2 == 0 else "A3", 1, 60)
    song.notes.sort(key=lambda n: (n.tick, n.layer))
    return song, "&f&lSleigh Bells (ambience)", bar * bars


HAND_BUILT = [
    ("carol_of_the_bells", build_carol_of_the_bells, "Mykola Leontovych, 1916"),
    ("uc_workshop_theme", build_workshop_theme, "UltimateChristmas original"),
    ("uc_winter_siege", build_battle_theme, "UltimateChristmas original"),
    ("uc_fanfare", build_fanfare, "UltimateChristmas original"),
    ("uc_sleigh_bells", build_sleigh_bells, "UltimateChristmas original"),
]

_voice = _voice  # re-export for the hand-built builders above


# --------------------------------------------------------------------------
# driver
# --------------------------------------------------------------------------
def render(spec):
    song = Song(spec["title"].replace("&", "").replace("l", "", 1).strip() or spec["key_name"],
                original_author=spec["original"],
                description=spec["description"] or
                "Public domain melody; note block arrangement by UltimateChristmas.",
                bpm=spec["bpm"], ticks_per_beat=4)
    song.name = spec["key_name"].replace("_", " ").title()

    bar = spec["bar"]
    melody = parse(spec["melody"], bar)
    lead = Seq(song, spec["lead"], "melody", volume=100)
    play(lead, melody, 100, sustain=True)

    harmonise(song, melody, bar, spec["key"], spec["minor"], spec["style"],
              pad_instrument=spec["pad"])

    if spec["bells"]:
        sparkle(song, melody, bar, every=spec["bells"])
    if spec["sleigh"]:
        bars = (max(t for t, _, _ in melody) // bar) + 1
        jingle(song, bar, bars)

    song.notes.sort(key=lambda n: (n.tick, n.layer))
    return song, (max(t + d for t, _, d in melody))


def emit(key_name, song, length, rows, title, original):
    if song.collisions:
        raise SystemExit("%s: %d notes landed on an occupied tick+layer and "
                         "would be dropped (first at %r)"
                         % (key_name, len(song.collisions), song.collisions[0]))
    path = os.path.join(OUT_DIR, key_name + ".nbs")
    count = song.write(path)
    rows.append((key_name, title, original, length / (song.tempo / 100.0),
                 count, len(song.layers)))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    rows = []

    for spec in CAROLS:
        song, length = render(spec)
        emit(spec["key_name"], song, length, rows, spec["title"], spec["original"])

    for key_name, builder, original in HAND_BUILT:
        song, title, length = builder()
        emit(key_name, song, length, rows, title, original)

    rows.sort(key=lambda r: r[0])
    write_music_yml(rows)

    print("Wrote %d tracks to %s" % (len(rows), os.path.normpath(OUT_DIR)))
    for key_name, title, original, seconds, count, layers in rows:
        print("  %-32s %5.1fs %4d notes %2d layers  %s"
              % (key_name, seconds, count, layers, original))


def write_music_yml(rows):
    path = os.path.join(OUT_DIR, "..", "modules", "music.yml")
    lines = [
        "# ============================================================",
        "#  UltimateChristmas - modules/music.yml",
        "# ============================================================",
        "#  Playback needs NoteBlockAPI installed (soft dependency).",
        "#  .nbs files live in plugins/UltimateChristmas/music/ and are",
        "#  unpacked from the jar on first start. Drop your own .nbs",
        "#  files in that folder and add them below to use them.",
        "#",
        "#  Every bundled track is an original note block arrangement of a",
        "#  public domain melody (or an original composition), generated by",
        "#  tools/generate_music.py - free to redistribute.",
        "# ============================================================",
        "",
        "# Playback volume, 0.0 to 1.0.",
        "volume: 0.7",
        "",
        "# A track key to start for players as they join. Leave blank for none.",
        "play-on-join: ''",
        "",
        "# The festive radio: a continuous playlist players opt into with",
        "# /christmas music radio. Their choice is remembered across reconnects.",
        "radio:",
        "  enabled: true",
        "  shuffle: true",
        "  # Leave empty to cycle every track below.",
        "  playlist:",
        "    - jingle_bells",
        "    - deck_the_halls",
        "    - we_wish_you_a_merry_christmas",
        "    - carol_of_the_bells",
        "    - silent_night",
        "",
        "# Short pieces the plugin plays by itself at specific moments.",
        "# Set a cue to '' to silence just that one.",
        "cues:",
        "  enabled: true",
        "  boss-spawn: uc_fanfare",
        "  boss-fight: uc_winter_siege",
        "  advent-claim: uc_fanfare",
        "  present-found: uc_fanfare",
        "  tree-complete: uc_fanfare",
        "",
        "tracks:",
    ]
    for key_name, title, original, seconds, count, layers in rows:
        lines += [
            "  %s:" % key_name,
            "    file: \"%s.nbs\"" % key_name,
            "    display_name: \"%s\"" % title,
            "    source: \"%s\"" % original,
            "    length_seconds: %d" % round(seconds),
            "",
        ]
    with open(os.path.normpath(path), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines).rstrip() + "\n")


if __name__ == "__main__":
    main()
