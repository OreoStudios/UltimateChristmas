# UltimateChristmas

A full festive season for Paper 1.21.x servers — an advent calendar, a wandering
Santa, festive bosses, a hidden-present hunt, snowball fights, a community tree,
Secret Santa, quests, a festive pass, cosmetics, leaderboards, a 23-track carol
jukebox, and more.

Version 4.0 is a ground-up rewrite on top of
[MinecraftModulith](https://github.com/OreoStudios/MinecraftModulith): every
feature is an independent module with its own API boundary and lifecycle, so any
of them can be turned on, off, restarted or reloaded on its own, live, without
touching the rest.

- **Java:** 21
- **Platform:** Paper / Folia-aware 1.21.x
- **Dependencies:** none required — every integration is optional and detected
  at startup.

---

## Features

Each feature is a module. Manage them in game with `/christmas module …`.

| Module | What it does |
| --- | --- |
| `economy` | The shard currency, earned from blocks, mobs and events |
| `shop` | Santa's Workshop store, with categories and confirmation |
| `advent` | A 25-door advent calendar with streaks and paid catch-up |
| `santa` | A wandering Santa NPC who hands out gifts |
| `bosses` | The Grinch, Krampus and Frost Monarch, with shared loot |
| `snow` | Personal snowfall that follows players, plus blizzards |
| `snowball` | Snowball fights, trick snowballs and hit rewards |
| `presents` | Presents hidden around the world to find and open |
| `music` | A note-block carol jukebox, radio and event cues |
| `quests` | Daily, weekly and season objectives |
| `pass` | A festive pass with free and premium tiers |
| `cosmetics` | Hats and particle trails |
| `feasts` | Festive food and drink that grant buffs |
| `tree` | One community tree the whole server decorates together |
| `secretsanta` | A server-wide Secret Santa gift exchange |
| `countdown` | A countdown boss bar and a midnight firework show |
| `leaderboards` | Festive leaderboards for every tracked stat |
| `ambience` | Join greetings and incidental festive atmosphere |
| `admin` | Reload, diagnostics and the module control commands |
| `platform` | Shared infrastructure the other modules build on |

---

## Commands

Everything is under `/christmas` (aliases: `/uc`, `/xmas`). Run it with no
arguments for paged, clickable help.

**Players**

| Command | Does |
| --- | --- |
| `/christmas shards [player]` | Check a shard balance |
| `/christmas pay <player> <amount>` | Send shards to someone |
| `/christmas shop` | Open the store |
| `/christmas advent` | Open the advent calendar |
| `/christmas snow [on\|off]` | Toggle the snow that follows you |
| `/christmas music [list\|play <t>\|radio\|stop]` | Play a carol |
| `/christmas presents` | How many presents are hidden |
| `/christmas quests` | See your objectives |
| `/christmas pass` | Open your festive pass |
| `/christmas cosmetics` | Open the wardrobe |
| `/christmas feast` | List or eat festive food |
| `/christmas tree [hang\|status]` | Decorate the community tree |
| `/christmas secretsanta [join\|deliver]` | The gift exchange |
| `/christmas countdown` | Time until Christmas |
| `/christmas top [board]` | Leaderboards |
| `/christmas season` | Where the season clock stands |
| `/christmas profile` | Your festive stats |

**Admins** (`ultimatechristmas.admin.*`)

| Command | Does |
| --- | --- |
| `/christmas reload` | Reload every config (nothing restarts) |
| `/christmas module <action> [id]` | Per-module lifecycle (below) |
| `/christmas eco <give\|take\|set> <player> <amt>` | Shard admin |
| `/christmas santa <spawn\|despawn>` | Control the Santa NPC |
| `/christmas boss <spawn\|despawn> [id]` | Control festive bosses |
| `/christmas snow blizzard [seconds]` | Start a blizzard |
| `/christmas presents <spawn\|clear>` | Manage the present hunt |
| `/christmas architecture [format]` | Export the module graph |

---

## The module system

Each feature lives in its own package with a strict internal layering:

```
modules/<feature>/
├── package-info.java   @ApplicationModule(id = "<feature>")  — the boundary
├── api/                @ModuleApi contract + domain events    — visible to others
├── infra/              implementation, listeners, menus       — private
└── lifecycle/          @PluginModule class + Parameters record
```

Only the `api` package of a module is visible to other modules; `infra` is
private to it. Modules talk to each other through those published APIs or through
domain events on the Modulith event bus — never by reaching into each other's
internals. An architecture test enforces this.

**Typed functors.** Each module is constructed by a typed factory from an
immutable parameter record built from its config, so a module never parses raw
config in its constructor and is trivial to test in isolation. See
`ModuleWiring`.

**Per-module lifecycle.** Because every module registers its listeners, tasks
and commands against its own Modulith lifecycle scope, each can be controlled
independently and live:

```
/christmas module list                 what exists and what is running
/christmas module disable <id>          stop one feature now (and keep it off)
/christmas module enable <id>           start it again
/christmas module restart <id>          fresh listeners and tasks
/christmas module reload <id>           re-read just that module's config
```

A module disabled in game is recorded in `config.yml` and stays off across
restarts. Disabling a module that another running module requires is refused
with a message naming what is in the way.

---

## Configuration

```
plugins/UltimateChristmas/
├── config.yml          global: the season window, currency name, storage
├── lang.yml            every player-facing string (MiniMessage + legacy codes)
├── modules/            one file per module, named after it
│   ├── economy.yml
│   ├── advent.yml
│   └── …
├── music/              the bundled .nbs carols (unpacked on first run)
└── playerdata/         per-player data (YAML mode)
```

The **season window** — start date, length, timezone, daily rollover hour — is
configured once in `config.yml`, and the advent calendar, the countdown and the
daily reset all read from it, so they can never disagree.

**Rewards** everywhere use one shared format, so a prize defined for an advent
door also works in the shop, a boss drop, a present, a quest or a pass tier:

```yaml
my_reward:
  weight: 25               # relative chance within its table
  shards: 50-120           # a number or an inclusive range
  xp: 100                  # vanilla experience
  pass-xp: 50              # festive pass progress
  items:
    - { material: DIAMOND, amount: 3 }
  commands: ["say %player% won!"]
  messages: ["&aYou won something nice!"]
  sound: ENTITY_PLAYER_LEVELUP
  firework: true
  effects: ["SPEED:600:1"]
```

**Storage** is YAML (a file per player) by default, or SQLite (`storage.type:
sqlite`) for larger servers — which also makes leaderboards an indexed query.
Either way, writes are batched and happen off the main thread.

---

## Integrations (all optional)

| Plugin | Adds |
| --- | --- |
| NoteBlockAPI | plays the bundled carols |
| WorldGuard | region limits for Santa, presents and the tree |
| LuckPerms | group-specific reward tables |
| Vault | shop entries priced in server currency |
| LibsDisguises | a player skin for the Santa NPC |
| MythicMobs | shard rewards for custom mobs |
| ItemsAdder | shard rewards for custom blocks |

The plugin detects what is installed at startup and logs which integrations are
active. Nothing is required.

---

## The soundtrack

The 23 bundled tracks are generated, not downloaded: `tools/generate_music.py`
writes note-block arrangements of 18 public-domain carols (Jingle Bells, Silent
Night, Carol of the Bells, O Come All Ye Faithful…) plus four original cues, all
free to redistribute. `tools/verify_music.py` reads every file back and checks it
against the `.nbs` spec.

```
python tools/generate_music.py     # regenerate the soundtrack + music.yml
python tools/verify_music.py        # validate the .nbs files
python tools/check_configs.py       # validate every YAML resource
```

---

## Building

Requires JDK 21+ and Python 3.10+. Maven compiles the four bundled Blockbench
projects into runtime rigs and a resource-pack zip during `generate-resources`.
Use `-Dpython.executable=python3` when needed.

```
mvn clean package
```

Produces `target/UltimateChristmas-4.1.1.jar` with MinecraftModulith shaded in.
Requires JDK 21.

## Remastered Grinch encounter

The Grinch and all three minions now use the remastered 256px source projects,
with all 35 authored animations. The built-in renderer supports bone and cube
rest rotations, blink scaling, animation priorities, and death poses after the
base entity disappears. The Grinch moves at 0.18 and switches to the run clip
only above 0.32 blocks/tick; rage does not speed him up.

Summoning cycles through Sneaky, Strong and Crazy. Hurt, rage, victory and
carrying states have event mappings. Preview any clip with
`/christmas boss animate grinch <clip>` (tab completion lists all clips).

Existing installations receive a backed-up model/config upgrade automatically.
Rewards, health and spawn locations remain customized. Re-host or merge the new
`plugins/UltimateChristmas/pack/UltimateChristmas-Models.zip` so clients receive
the new models. The pack targets Java 1.21.8; no external hosting URL is supplied.
See [the model guide](src/main/resources/models/README.txt) for migration details.

The item-display centering and affine transforms follow
[Paper's display entity documentation](https://docs.papermc.io/paper/dev/display-entities/).
Pose regression fixtures were captured from the running Blockbench project.

This build is Java-only: it ships no Bedrock pack or Geyser mappings. Bedrock
players connected through Geyser do not see the animated models.

---

## Credits

Built by Elias. Carol arrangements and original cues generated by the tools in
`tools/`. Architecture on
[MinecraftModulith](https://github.com/OreoStudios/MinecraftModulith) by Oreo
Studios.
