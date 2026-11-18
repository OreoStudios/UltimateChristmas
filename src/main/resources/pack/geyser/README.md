# UltimateChristmas Bedrock resource pack

This package contains the four remastered characters as **157 mapped item-display
parts**: Grinch 80, Sneaky 25, Strong 25 and Crazy 27. It preserves all 246 cubes,
the original four 256 x 256 PNG atlases, face UVs and signed UV spans. It targets
UltimateChristmas **4.1.1 or later**, including its item-display rotation fix.

## Required server components

Use a current Geyser release supporting **custom items v2** (API 2.9.3/build 1062
or newer). A compatible **GeyserDisplayEntity extension and its accompanying
GeyserDisplayEntityPack.mcpack** are also required to render these boss parts.
Choose an extension release built for your installed Geyser version. The source
examined for the companion configuration is commit
`3d22bff3265f85812beaeb5e3b7e898ee4a58105` (extension 1.0.14, Geyser 2.12 API).
The extension and its pack are third-party dependencies, not included here.

**The .mcpack plus JSON mappings alone cannot display the animated bosses on
plain Geyser.** Bedrock has no Java item-display entity. Floodgate does not add
display-entity rendering either.

## Installation

Find the config directory of the Geyser instance that accepts Bedrock players.
For Geyser-Spigot this is usually `plugins/Geyser-Spigot`; on a proxy, install on
the proxy's Geyser instance, not just on the Paper backend.

1. Put `packs/UltimateChristmas-Bedrock.mcpack` in Geyser's `packs/` folder.
2. Put `custom_mappings/ultimatechristmas.json` in Geyser's `custom_mappings/`.
3. Ensure `gameplay.enable-custom-content: true` in Geyser's `config.yml`.
4. Install a compatible GeyserDisplayEntity extension in Geyser's `extensions/`
   and its companion resource pack in `packs/`. Start it once to create its
   configuration. Stop the server before installing the mapping file below.
5. Put the supplied `ultimatechristmas.yml` in that extension's generated
   `Mappings/` directory (normally
   `extensions/GeyserDisplayEntity/Mappings/`). Keep unrelated mappings.
6. Fully restart Geyser/server and reconnect from Bedrock, accepting both packs.
   Mappings and custom item registrations are established during startup.

The bundle's two mapping formats have different destinations: Geyser reads the
JSON; the display extension reads the YAML. Neither mapping belongs inside the
`.mcpack`. Do not use the Bedrock `.mcpack` as the plugin's Java resource-pack URL.
Java players continue using `UltimateChristmas-Models.zip`.

## Mapping details

Each entry matches `minecraft:paper` plus the exact `minecraft:item_model` value
that UltimateChristmas puts on its ItemDisplay. For example:

```json
{
  "type": "definition",
  "model": "ultimatechristmas:boss/grinch_head",
  "bedrock_identifier": "ultimatechristmas:boss_grinch_head",
  "bedrock_options": {
    "icon": "ultimatechristmas.grinch",
    "creative_category": "none",
    "allow_offhand": false,
    "display_handheld": false
  }
}
```

The root JSON uses `format_version: 2` and `items.minecraft:paper`. These are
not legacy CustomModelData mappings. Unmodified vanilla paper is not matched.
Identifiers match the corresponding attachables; icon keys exist in
`textures/item_texture.json`. No extra Java behavior components are invented.
The inventory icon uses the character's atlas; these internal bone items are
not intended as player inventory cosmetics.

The 3D attachables target the `geyser:item_display` owner and copy the reference
extension's `geyser`, `geyser_x`, `geyser_y`, `geyser_z` bone hierarchy. A local
Y half-turn matches Minecraft's FIXED item-rendering convention. The supplied
per-item extension configuration equips them on the chest with its documented
`y-offset: -0.5`, property-based scale and no extra mapping rotation.

## Animation and verification limits

All **35 animations remain on the Java server**. UltimateChristmas animates the
157 displays; the extension must translate their current transforms. Playing
separate looping Bedrock clips would double-animate the parts and lose combat
timing, so this pack deliberately does not invent independent animation loops.

The current referenced extension does **not** preserve the complete affine
transform pipeline used by these rigs. Its quaternion combination, roll,
nonuniform scale and interpolation handling require Bedrock runtime review.
Consequently this is a validated asset/mapping conversion, **not a certification
that all animated boss poses match Java**. Full parity needs a display bridge
that faithfully handles both rotation quaternions and per-axis scaling, or a
dedicated native-entity integration. Installing a pack cannot fix that protocol
limitation. No Bedrock client visual test has been performed for this output.

After installation, check Geyser's startup log for mapping errors. Spawn each
character (`/christmas boss spawn grinch`, `grinch_sneaky`, `grinch_strong`,
`grinch_crazy`). Compare front, back and sides, then use
`/christmas boss animate grinch <clip>` for all 11 boss clips. Check blinking,
walk, attack, summon and death closely; verify all eight minion clips too.

## Rebuilding

```text
python tools/generate_resourcepack.py
python -B -m unittest discover -s tools -p "test_*.py"
```

Maven's existing generate-resources phase also builds both packs. The build puts
`UltimateChristmas-Bedrock.mcpack` and `UltimateChristmas-Geyser-bundle.zip`
beside the Java pack in `src/main/resources/pack/`. These files are included in
the plugin JAR and its asset manifest, so the existing installer also extracts
them into `plugins/UltimateChristmas/pack/` on startup. Geyser still requires
the manual installation steps above.

The mappings are also available without extracting the ZIP:

- `src/main/resources/pack/geyser/custom_mappings/ultimatechristmas.json`
- `src/main/resources/pack/geyser/extensions/GeyserDisplayEntity/Mappings/ultimatechristmas.yml`

These loose files, their README and the checksum report are included in the
asset manifest too, and are extracted into `plugins/UltimateChristmas/pack/geyser/`.

Another copy of the mappings and deployment bundle is written under
`target/geyser/`. Pack UUIDs remain stable and builds are deterministic. Raise
the pack version when publishing changed Bedrock assets to avoid client cache
ambiguity.

## Documentation checked

- [Geyser custom items v2 and Bedrock options](https://geysermc.org/wiki/geyser/custom-items/#bedrock-options)
- [Geyser custom mapping installation](https://geysermc.org/wiki/geyser/custom-content/)
- [Sending Bedrock resource packs](https://geysermc.org/wiki/geyser/packs/)
- [Geyser display entity limitation](https://geysermc.org/wiki/other/rainbow/)
- [GeyserDisplayEntity source and setup](https://github.com/GeyserExtensionists/GeyserDisplayEntity)
- [Microsoft attachable specification](https://learn.microsoft.com/en-us/minecraft/creator/documents/attachables)

Geometry/UV conversion was cross-checked against Blockbench's Bedrock export.
The source `.bbmodel` projects and existing Java pack are preserved.
