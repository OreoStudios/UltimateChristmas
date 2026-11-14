package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Applies a player skin to an NPC via LibsDisguises, when it is installed.
 *
 * <p>Reached by reflection on purpose: LibsDisguises publishes only to a
 * third-party Maven repository, and the surface used here is two calls. A
 * compile-time dependency would tie builds of this plugin to the availability
 * of that repo for no real benefit. Without it, NPCs fall back to the native
 * appearance configured in santa.yml / bosses.yml, which needs no extra plugin.
 */
public final class DisguiseHook extends PluginHook {

    private Constructor<?> playerDisguiseConstructor;
    private Method disguiseToAll;
    private Method undisguiseToAll;
    private Method setEntity;

    public DisguiseHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "LibsDisguises";
    }

    @Override
    protected boolean setUp() throws Exception {
        Class<?> playerDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
        Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
        playerDisguiseConstructor = playerDisguise.getConstructor(String.class);
        setEntity = disguise.getMethod("setEntity", Entity.class);
        disguiseToAll = disguise.getMethod("startDisguise");
        Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
        undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
        return true;
    }

    /**
     * Disguises {@code entity} as the player {@code skinName}.
     *
     * @return true if the disguise was applied
     */
    public boolean disguiseAsPlayer(Entity entity, String skinName) {
        if (!active() || entity == null || skinName == null || skinName.isBlank()) {
            return false;
        }
        try {
            Object disguise = playerDisguiseConstructor.newInstance(skinName);
            setEntity.invoke(disguise, entity);
            disguiseToAll.invoke(disguise);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not disguise entity as '" + skinName
                    + "': " + ex.getMessage());
            return false;
        }
    }

    public void undisguise(Entity entity) {
        if (!active() || entity == null) {
            return;
        }
        try {
            undisguiseToAll.invoke(null, entity);
        } catch (Exception ignored) {
            // The entity is going away regardless; nothing useful to do here.
        }
    }
}
