UltimateChristmas 4.1 - remastered boss models
=============================================

The built-in display provider renders all five models without a model plugin.
The client must load the generated resource pack. ModelEngine and BetterModel
remain optional providers, configured separately.

Bundled Bedrock cube projects, each with an embedded 256 x 256 pixel atlas:
  grinch.bbmodel: idle, walk, run, attack, laugh, summon, steal, hurt, rage,
                  death, celebrate
  grinch_minion_sneaky.bbmodel
  grinch_minion_strong.bbmodel
  grinch_minion_crazy.bbmodel
    Each minion: idle, walk, run, attack, laugh, carry_present, hurt, death.
  santa_claus.bbmodel: idle, walk, wave, laugh, drop_gift, give_gift,
                       interact, celebrate

SANTA (modules/santa.yml, model section)
  idle/walk: while resting / strolling around his spawn point
  wave: when he appears, and at each player who walks up to him
  drop_gift: at random intervals while he is spawned (gifts.drops); the real
             present item lands where the clip sets its "gift" bone down
  interact: a player right-clicks him (he gives nothing on click)
  give_gift: not used by default; assign it to any slot under animations
  celebrate: a player opens one of his presents nearby
  laugh: farewell, played before he leaves
  /christmas santa spawn   (at your feet if no spawn-locations are set)
  /christmas santa drop    (play the drop now)

The Grinch uses movement speed 0.18 (previously 0.30), a 0.32 blocks/tick run
animation threshold and 0.85 locomotion playback rate. Rage does not increase
his movement speed. Change these deliberately in modules/bosses.yml if desired.

ANIMATION EVENTS
  idle/walk/run: chosen from actual horizontal movement
  attack: melee hit; hurt: nonfatal incoming damage
  spawn: laugh; summon ability: summon; roar: laugh; frost: steal gesture
  rage: once below 35% health; victory: celebrate after killing a player
  death: the display finishes its authored defeat after the base mob dies
  minions: carry_present while resting without a combat target

Summon cycles through Sneaky, Strong and Crazy, using their model settings and
stats. Summoned health is 35% of the corresponding standalone mini-boss health.
At most eight nearby summoned minions are allowed. Death/despawn cleanup is
restricted to minions belonging to that particular boss encounter.

TRYING THE MODELS
  /christmas boss spawn grinch
  /christmas boss spawn grinch_sneaky
  /christmas boss spawn grinch_strong
  /christmas boss spawn grinch_crazy
  /christmas boss animate grinch summon
  /christmas boss animate grinch celebrate

The animate command plays any authored clip once; tab completion lists clips.
A held death preview stays down until the boss is despawned. It does not kill
the entity or grant rewards. Normally death is driven by the actual kill.

RESOURCE PACK
Maven generates src/main/resources/pack/UltimateChristmas-Models.zip and embeds
it in the jar. It is unpacked to plugins/UltimateChristmas/pack/ on startup.
Host the new zip at your existing resource-pack.url, or merge its assets into
your server's pack. Players must download the updated pack. No hosting URL is
invented or changed automatically. The pack targets Minecraft Java 1.21.8.

UPGRADING
Bundled assets are versioned by their content, including animation rigs.
Changed bundles automatically replace matching shipped models, rigs and pack;
previous copies are kept in plugins/UltimateChristmas/model-backups/.
Files installed by a previous manifest but no longer shipped are backed up
and removed. Additional server-owned models/rigs are left alone. With an
unchanged bundle, local edits survive a restart; missing files are restored.

The one-time bosses.yml migration backs up the original file beside it as
bosses.yml.before-grinch-remastered-20261116. It updates the four Grinch model
sections, the summon variants and hitbox scales, and caps the Grinch's existing
speed at 0.18. It preserves customized health, loot, spawn coordinates, messages
and unrelated bosses. Later manual changes survive subsequent restarts.

BUILDING / EDITING
Requires JDK 21+ and Python 3.10+ (standard library for model compilation).
  python tools/generate_resourcepack.py
  python -m unittest discover -s tools -p "test_*.py"
  mvn -B -ntp clean verify

Maven regenerates models at generate-resources. Set -Dpython.executable=python3
if that is the Python command on your machine. The .bbmodel files remain the
source of truth; do not edit generated rigs or item models in the repository.

The compiler supports cube geometry, per-face UVs, arbitrary ZYX cube/bone
rest rotations, numeric rotation/position/scale tracks and loop/once/hold.
It rejects unbaked expressions and unsupported interpolation instead of
silently dropping them. Preserve Bedrock format for these source projects.

The built-in runtime is tested against poses captured from Blockbench, including
blink scaling and rotated cube leaves. Those tests do not replace a client
check of resource-pack loading, hitboxes and combat on the deployment server.
Fabled is not a dependency of UltimateChristmas's built-in renderer. External
Fabled/ModelEngine/BetterModel workflows need their own integration check.
