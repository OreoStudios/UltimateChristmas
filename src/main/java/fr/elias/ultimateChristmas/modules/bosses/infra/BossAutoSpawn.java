package fr.elias.ultimateChristmas.modules.bosses.infra;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Opt-in, per-boss automatic spawn policy. The old single "spawn" point remains
 * the fallback so existing setups can enable automation without moving it.
 */
public record BossAutoSpawn(boolean enabled, int intervalSeconds, int respawnSeconds,
                            int maxAlive, boolean announce, List<Location> locations) {

    public BossAutoSpawn {
        locations = List.copyOf(locations);
    }

    public static BossAutoSpawn read(ConfigurationSection config, Location legacySpawn, Logger logger) {
        if (config == null) {
            return new BossAutoSpawn(false, 3600, 1800, 1, true, List.of());
        }
        List<Location> locations = new ArrayList<>();
        List<?> raw = config.getList("locations");
        if (raw != null) {
            for (Object entry : raw) {
                Location location = readLocation(entry);
                if (location == null) {
                    logger.warning("Invalid boss auto-spawn location (world must be loaded): " + entry);
                } else {
                    locations.add(location);
                }
            }
        }
        if (locations.isEmpty() && legacySpawn != null) {
            locations.add(legacySpawn.clone());
        }

        int interval = Math.max(10, config.getInt("interval-seconds",
                config.getInt("interval-minutes", 60) * 60));
        int respawn = Math.max(10, config.getInt("respawn-after-death-seconds",
                config.getInt("respawn-after-death-minutes", 30) * 60));
        boolean enabled = config.getBoolean("enabled", false);
        if (enabled && locations.isEmpty()) {
            logger.warning("Boss auto-spawn is enabled, but no valid spawn locations are configured.");
        }
        return new BossAutoSpawn(enabled, interval, respawn,
                Math.max(1, config.getInt("max-alive", 1)),
                config.getBoolean("announce", true), locations);
    }

    private static Location readLocation(Object raw) {
        try {
            if (raw instanceof String csv) {
                String[] fields = csv.split(",");
                if (fields.length < 4) return null;
                World world = Bukkit.getWorld(fields[0].trim());
                return world == null ? null : new Location(world,
                        Double.parseDouble(fields[1].trim()),
                        Double.parseDouble(fields[2].trim()),
                        Double.parseDouble(fields[3].trim()));
            }
            if (raw instanceof Map<?, ?> map) {
                Object name = map.get("world");
                World world = name == null ? null : Bukkit.getWorld(String.valueOf(name));
                return world == null ? null : new Location(world,
                        Double.parseDouble(String.valueOf(map.get("x"))),
                        Double.parseDouble(String.valueOf(map.get("y"))),
                        Double.parseDouble(String.valueOf(map.get("z"))));
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }

    public Location pickLocation() {
        if (locations.isEmpty()) return null;
        return locations.get(ThreadLocalRandom.current().nextInt(locations.size())).clone();
    }
}
