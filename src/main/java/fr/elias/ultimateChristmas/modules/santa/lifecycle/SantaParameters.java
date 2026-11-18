package fr.elias.ultimateChristmas.modules.santa.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;
import fr.elias.ultimateChristmas.core.hook.ModelHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed settings for the Santa NPC, built by its functor.
 *
 * @param displayName        Santa's name tag
 * @param skin               LibsDisguises player skin, used only when no model is shown
 * @param autoSpawn          spawn Santa on a timer
 * @param staySeconds        how long he stays before leaving
 * @param cooldownSeconds    gap between automatic appearances
 * @param openRegions       WorldGuard regions where his presents may be opened; empty means anywhere
 * @param spawnLocations     where he can appear
 * @param model              his Blockbench model and the clip used for each action
 * @param wander             how he strolls around his spawn point
 * @param drops              the player-head presents he leaves on the ground
 */
public record SantaParameters(
        String displayName,
        String skin,
        boolean autoSpawn,
        int staySeconds,
        int cooldownSeconds,
        List<String> openRegions,
        List<Location> spawnLocations,
        Model model,
        Wander wander,
        Present present,
        Drops drops
) {

    /**
     * @param id         rig / model id ({@code rigs/<id>.json} for the built-in renderer)
     * @param provider   display, modelengine, bettermodel, auto or none
     * @param scale      model scale
     * @param offsetY    lift in blocks so the boots meet the ground
     * @param yawOffset  flip if the model faces backwards
     * @param idle       standing loop
     * @param walk       walking loop
     * @param arrive     played when he appears
     * @param greet      played at a player who walks up to him
     * @param dropGift   played each time he leaves a present on the ground
     * @param clicked    played when a player right-clicks him
     * @param celebrate  played when someone opens one of his presents nearby
     * @param farewell   played before he leaves
     */
    public record Model(String id, ModelHook.Provider provider, float scale, double offsetY,
                        float yawOffset, String idle, String walk, String arrive, String greet,
                        String dropGift, String clicked, String celebrate, String farewell) {
        public static final Model DEFAULTS = new Model("santa_claus", ModelHook.Provider.AUTO,
                1, 0, 180, "idle", "walk", "wave", "wave", "drop_gift", "interact",
                "celebrate", "laugh");

        static Model from(ConfigurationSection section, Model d) {
            if (section == null) {
                return d;
            }
            ConfigurationSection clips = section.getConfigurationSection("animations");
            return new Model(
                    section.getString("id", d.id()),
                    section.isSet("provider") ? ModelHook.Provider.parse(section.getString("provider"))
                            : d.provider(),
                    (float) Math.max(0.05, section.getDouble("scale", d.scale())),
                    section.getDouble("offset-y", d.offsetY()),
                    (float) section.getDouble("yaw-offset", d.yawOffset()),
                    clip(clips, "idle", d.idle()),
                    clip(clips, "walk", d.walk()),
                    clip(clips, "arrive", d.arrive()),
                    clip(clips, "greet", d.greet()),
                    clip(clips, "drop-gift", d.dropGift()),
                    clip(clips, "clicked", clip(clips, "refuse", d.clicked())),
                    clip(clips, "celebrate", d.celebrate()),
                    clip(clips, "farewell", d.farewell()));
        }

        private static String clip(ConfigurationSection clips, String key, String fallback) {
            return clips == null ? fallback : clips.getString(key, fallback);
        }
    }

    /**
     * @param enabled        stroll around instead of standing still
     * @param radius         furthest he wanders from where he appeared, in blocks
     * @param speed          walking speed in blocks per tick
     * @param pauseMinTicks  shortest rest between strolls
     * @param pauseMaxTicks  longest rest between strolls
     * @param greetRadius    a player this close is waved at, once per visit
     */
    public record Wander(boolean enabled, double radius, double speed,
                         int pauseMinTicks, int pauseMaxTicks, double greetRadius) {
        public static final Wander DEFAULTS = new Wander(true, 10, 0.06, 60, 160, 6);

        static Wander from(ConfigurationSection section, Wander d) {
            if (section == null) {
                return d;
            }
            int min = (int) Math.round(Math.max(0, section.getDouble("pause-min-seconds",
                    d.pauseMinTicks() / 20.0)) * 20);
            int max = (int) Math.round(Math.max(0, section.getDouble("pause-max-seconds",
                    d.pauseMaxTicks() / 20.0)) * 20);
            return new Wander(
                    section.getBoolean("enabled", d.enabled()),
                    Math.max(1, section.getDouble("radius", d.radius())),
                    Math.min(0.3, Math.max(0.02, section.getDouble("speed", d.speed()))),
                    Math.min(min, max), Math.max(min, max),
                    Math.max(0, section.getDouble("greet-radius", d.greetRadius())));
        }
    }

    /**
     * The present item. Players pick it up and right-click it to open it.
     *
     * @param type             head, itemsadder or material
     * @param headTexture      head texture: base64 value, texture URL/hash or a player name
     * @param itemsAdderId     {@code namespace:id} of an ItemsAdder item (type itemsadder)
     * @param material         vanilla material (type material, or the ItemsAdder fallback)
     * @param customModelData  optional custom model data for a resource-pack texture
     * @param name             item name, also floating above it on the ground
     * @param lore             item lore
     */
    public record Present(String type, String headTexture, String itemsAdderId, String material,
                          int customModelData, String name, List<String> lore) {
        public static final Present DEFAULTS = new Present("head",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTE3YjMwNDExZTUwYWUxNWJiZTg0Yzk0NGY1MWRmNWUxYjRmNTZiM2RiMDg5MjgwYjZkMTVkNDAzNjU5MDYxIn19fQ==",
                "", "PLAYER_HEAD", 0, "&c&lSanta's Present",
                List.of("&7Right-click to open!", "&fContains a random reward",
                        "&fstraight from &cSanta &f🎁"));

        public Present {
            lore = List.copyOf(lore);
        }

        static Present from(ConfigurationSection section, ConfigurationSection legacyDrops, Present d) {
            if (section == null) {
                // Builds from before the present section kept these under drops.
                if (legacyDrops == null) {
                    return d;
                }
                return new Present(d.type(), legacyDrops.getString("head-texture", d.headTexture()),
                        d.itemsAdderId(), d.material(), d.customModelData(),
                        legacyDrops.getString("name", d.name()), d.lore());
            }
            List<String> lore = section.getStringList("lore");
            return new Present(
                    section.getString("type", d.type()).trim().toLowerCase(java.util.Locale.ROOT),
                    section.getString("head-texture", d.headTexture()),
                    section.getString("itemsadder-id", d.itemsAdderId()),
                    section.getString("material", d.material()),
                    Math.max(0, section.getInt("custom-model-data", d.customModelData())),
                    section.getString("name", d.name()),
                    section.isSet("lore") ? lore : d.lore());
        }
    }

    /**
     * @param enabled          drop presents while he is around
     * @param minSeconds       shortest random gap between two drops
     * @param maxSeconds       longest random gap between two drops
     * @param maxOnGround      most uncollected presents at once
     * @param lifetimeSeconds  a present nobody picks up disappears after this long
     */
    public record Drops(boolean enabled, int minSeconds, int maxSeconds, int maxOnGround,
                        int lifetimeSeconds) {
        public static final Drops DEFAULTS = new Drops(true, 15, 40, 8, 300);

        static Drops from(ConfigurationSection section, Drops d) {
            if (section == null) {
                return d;
            }
            int min = d.minSeconds();
            int max = d.maxSeconds();
            if (section.isSet("interval-seconds")) {
                // Older files had one fixed interval: drop anywhere from half of it up to it.
                max = section.getInt("interval-seconds");
                min = max / 2;
            }
            min = Math.max(5, section.getInt("interval-min-seconds", min));
            max = Math.max(5, section.getInt("interval-max-seconds", max));
            return new Drops(
                    section.getBoolean("enabled", d.enabled()),
                    Math.min(min, max), Math.max(min, max),
                    Math.max(1, section.getInt("max-on-ground", d.maxOnGround())),
                    Math.max(10, section.getInt("lifetime-seconds", d.lifetimeSeconds())));
        }

        /** A random gap until the next drop, in ticks. */
        public int nextIntervalTicks() {
            return java.util.concurrent.ThreadLocalRandom.current()
                    .nextInt(minSeconds * 20, maxSeconds * 20 + 1);
        }
    }

    public SantaParameters {
        openRegions = List.copyOf(openRegions);
        spawnLocations = List.copyOf(spawnLocations);
    }

    public static final SantaParameters DEFAULTS = new SantaParameters(
            "&c&lSanta Claus", "", true, 180, 600,
            List.of(), List.of(), Model.DEFAULTS, Wander.DEFAULTS,
            Present.DEFAULTS, Drops.DEFAULTS);

    public static SantaParameters from(ModuleConfiguration config, SantaParameters defaults) {
        return new SantaParameters(
                config.getString("display-name", defaults.displayName()),
                config.getString("skin", defaults.skin()),
                config.getBoolean("auto-spawn", defaults.autoSpawn()),
                Math.max(10, config.getInt("stay-seconds", defaults.staySeconds())),
                Math.max(30, config.getInt("cooldown-seconds", defaults.cooldownSeconds())),
                stringList(config.getStringList("gifts.open-regions"), defaults.openRegions()),
                readLocations(config.get("spawn-locations")),
                Model.from(section(config.get("model")), defaults.model()),
                Wander.from(section(config.get("wander")), defaults.wander()),
                Present.from(section(config.get("gifts.present")),
                        section(config.get("gifts.drops")), defaults.present()),
                Drops.from(section(config.get("gifts.drops")), defaults.drops()));
    }

    private static ConfigurationSection section(Object node) {
        return node instanceof ConfigurationSection section ? section : null;
    }

    private static List<String> stringList(List<String> values, List<String> fallback) {
        return values == null || values.isEmpty() ? fallback : List.copyOf(values);
    }

    /**
     * Reads spawn points from a list of {@code "world,x,y,z"} strings or a
     * keyed section. Entries whose world is not loaded are skipped with no
     * failure, so a spawn point in an unloaded world does not break the rest.
     */
    private static List<Location> readLocations(Object node) {
        List<Location> locations = new ArrayList<>();
        if (node instanceof List<?> list) {
            for (Object entry : list) {
                Location parsed = parseString(String.valueOf(entry));
                if (parsed != null) {
                    locations.add(parsed);
                }
            }
        } else if (node instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child == null) {
                    continue;
                }
                World world = Bukkit.getWorld(child.getString("world", ""));
                if (world != null) {
                    locations.add(new Location(world,
                            child.getDouble("x"), child.getDouble("y"), child.getDouble("z"),
                            (float) child.getDouble("yaw", 0), 0));
                }
            }
        }
        return locations;
    }

    private static Location parseString(String raw) {
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            return new Location(world,
                    Double.parseDouble(parts[1].trim()) + 0.5,
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()) + 0.5);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
