package fr.elias.ultimateChristmas.modules.tree.infra;

import dev.oreo.modulith.core.ModuleConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * The community tree's shared state: where it stands and how decorated it is.
 *
 * <p>This is server state, not per-player state, so it lives in the module's
 * own config file rather than on a player profile. The ornament count and the
 * tree location are written back through {@link ModuleConfiguration#set} and
 * persisted, so a restart mid-season does not lose the tree.
 */
public final class TreeState {

    private final ModuleConfiguration config;

    private Location location;
    private int ornaments;
    private boolean announcedComplete;

    public TreeState(ModuleConfiguration config) {
        this.config = config;
        read();
    }

    /** Reloads the persisted state from config. */
    public void read() {
        this.ornaments = config.getInt("state.ornaments", 0);
        this.announcedComplete = config.getBoolean("state.completed", false);
        this.location = readLocation();
    }

    private Location readLocation() {
        String worldName = config.getString("state.location.world", "");
        if (worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                config.getDouble("state.location.x", 0),
                config.getDouble("state.location.y", 0),
                config.getDouble("state.location.z", 0));
    }

    public Location location() {
        return location == null ? null : location.clone();
    }

    public int ornaments() {
        return ornaments;
    }

    public boolean completed() {
        return announcedComplete;
    }

    public void setLocation(Location where) {
        this.location = where.clone();
        config.set("state.location.world", where.getWorld().getName());
        config.set("state.location.x", where.getX());
        config.set("state.location.y", where.getY());
        config.set("state.location.z", where.getZ());
        config.save();
    }

    /** Records another ornament and persists. Returns the new total. */
    public int addOrnament() {
        ornaments++;
        config.set("state.ornaments", ornaments);
        config.save();
        return ornaments;
    }

    public void markCompleted() {
        announcedComplete = true;
        config.set("state.completed", true);
        config.save();
    }

    /** Clears the tree back to bare, keeping its location. */
    public void reset() {
        ornaments = 0;
        announcedComplete = false;
        config.set("state.ornaments", 0);
        config.set("state.completed", false);
        config.save();
    }
}
