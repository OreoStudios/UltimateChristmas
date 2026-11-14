package fr.elias.ultimateChristmas.modules.santa.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;
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
 * @param skin               LibsDisguises player skin, or blank for a plain villager
 * @param autoSpawn          spawn Santa on a timer
 * @param staySeconds        how long he stays before leaving
 * @param cooldownSeconds    gap between automatic appearances
 * @param giftCooldownHours  how often one player may receive a gift from him
 * @param openRegions        WorldGuard regions where his presents may be opened; empty means anywhere
 * @param spawnLocations     where he can appear
 */
public record SantaParameters(
        String displayName,
        String skin,
        boolean autoSpawn,
        int staySeconds,
        int cooldownSeconds,
        int giftCooldownHours,
        List<String> openRegions,
        List<Location> spawnLocations
) {

    public SantaParameters {
        openRegions = List.copyOf(openRegions);
        spawnLocations = List.copyOf(spawnLocations);
    }

    public static final SantaParameters DEFAULTS = new SantaParameters(
            "&c&lSanta Claus", "", true, 180, 600, 20,
            List.of(), List.of());

    public static SantaParameters from(ModuleConfiguration config, SantaParameters defaults) {
        return new SantaParameters(
                config.getString("display-name", defaults.displayName()),
                config.getString("skin", defaults.skin()),
                config.getBoolean("auto-spawn", defaults.autoSpawn()),
                Math.max(10, config.getInt("stay-seconds", defaults.staySeconds())),
                Math.max(30, config.getInt("cooldown-seconds", defaults.cooldownSeconds())),
                Math.max(0, config.getInt("gift-cooldown-hours", defaults.giftCooldownHours())),
                stringList(config.getStringList("gifts.open-regions"), defaults.openRegions()),
                readLocations(config.get("spawn-locations")));
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
