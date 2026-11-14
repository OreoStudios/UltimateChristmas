package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.entity.Entity;

/**
 * Identifies MythicMobs mobs, so the shard economy can reward killing them.
 */
public final class MythicMobsHook extends PluginHook {

    public MythicMobsHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "MythicMobs";
    }

    @Override
    protected boolean setUp() {
        return MythicBukkit.inst() != null;
    }

    /**
     * The internal mob type of {@code entity}, or {@code null} if it is not a
     * MythicMobs mob. This is the name used in mobs config files, not the
     * display name.
     */
    public String mobTypeOf(Entity entity) {
        if (!active() || entity == null) {
            return null;
        }
        try {
            var api = MythicBukkit.inst().getAPIHelper();
            if (!api.isMythicMob(entity)) {
                return null;
            }
            var mob = api.getMythicMobInstance(entity);
            return mob == null ? null : mob.getType().getInternalName();
        } catch (Exception | NoClassDefFoundError error) {
            return null;
        }
    }

    public boolean isMythicMob(Entity entity) {
        return mobTypeOf(entity) != null;
    }
}
