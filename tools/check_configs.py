"""
Validates every YAML resource the plugin ships.

Checks the failure modes that are silent at runtime:

* a syntax error, which makes Bukkit load an empty config and fall back to
  code defaults with no obvious sign that anything is wrong;
* a duplicate key, which YAML resolves by silently keeping the last one (a key
  used once as a number and once as a section is the classic case);
* a tab character used for indentation, which YAML rejects outright;
* a reward entry that grants nothing, which usually means a typo in its keys.

Run:  python tools/check_configs.py
"""

import io
import os
import sys

import yaml

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources")

# Keys that make a reward actually do something.
REWARD_PAYLOAD_KEYS = {
    "shards", "xp", "pass-xp", "naughty-nice", "items", "commands",
    "player-commands", "messages", "broadcast", "effects", "title", "subtitle",
}


class DuplicateKeyLoader(yaml.SafeLoader):
    """A loader that reports duplicate mapping keys instead of ignoring them."""


def _no_duplicates(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise yaml.constructor.ConstructorError(
                "while building a mapping", node.start_mark,
                "found duplicate key %r (YAML keeps only the last one)" % (key,),
                key_node.start_mark)
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


DuplicateKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _no_duplicates)


def yaml_files():
    for folder, _, names in os.walk(ROOT):
        for name in sorted(names):
            if name.endswith((".yml", ".yaml")):
                yield os.path.join(folder, name)


def check_tabs(text, problems):
    for number, line in enumerate(text.splitlines(), start=1):
        stripped = line.lstrip(" ")
        if stripped.startswith("\t") or (line and line[0] == "\t"):
            problems.append("line %d: indented with a tab; YAML needs spaces" % number)


def check_rewards(node, path, problems):
    """Warns about a reward-shaped entry that would grant nothing.

    A reward is identified by a `weight` key (the plugin's reward format). A
    bare `chance` is not enough on its own, because unrelated settings use that
    word too; it only counts when a reward payload or display sits beside it.
    """
    if not isinstance(node, dict):
        return
    for key, value in node.items():
        here = "%s.%s" % (path, key) if path else str(key)
        if isinstance(value, dict):
            siblings = set(value)
            display = "display" in siblings or "icon" in siblings
            looks_like_reward = "weight" in siblings or (
                "chance" in siblings and (display or (REWARD_PAYLOAD_KEYS & siblings)))
            if looks_like_reward and not (REWARD_PAYLOAD_KEYS & siblings):
                problems.append("%s has a weight but grants nothing" % here)
            check_rewards(value, here, problems)


def main():
    root = os.path.normpath(ROOT)
    files = list(yaml_files())
    if not files:
        print("no YAML files found under %s" % root)
        return 1

    failures = 0
    for path in files:
        relative = os.path.relpath(path, root).replace("\\", "/")
        problems = []
        text = io.open(path, encoding="utf-8").read()
        check_tabs(text, problems)

        data = None
        try:
            data = yaml.load(text, Loader=DuplicateKeyLoader)
        except yaml.YAMLError as error:
            problems.append(str(error).replace("\n", " "))

        if data is not None:
            check_rewards(data, "", problems)

        keys = len(data) if isinstance(data, dict) else 0
        status = "ok  " if not problems else "FAIL"
        print("%s %-28s %3d top-level key(s)" % (status, relative, keys))
        for problem in problems:
            print("       - %s" % problem)
        if problems:
            failures += 1

    print("\n%d/%d config files valid" % (len(files) - failures, len(files)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
