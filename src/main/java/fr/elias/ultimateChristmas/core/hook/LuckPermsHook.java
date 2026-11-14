package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/**
 * Resolves a player's permission group, used to pick group-specific reward sets.
 */
public final class LuckPermsHook extends PluginHook {

    private LuckPerms api;

    public LuckPermsHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "LuckPerms";
    }

    @Override
    protected boolean setUp() {
        api = LuckPermsProvider.get();
        return api != null;
    }

    /**
     * The player's primary group, or {@code fallback} when LuckPerms is absent.
     *
     * <p>Reads the cached user only — never loads from storage — so this is safe
     * to call on the main thread.
     */
    public String primaryGroup(Player player, String fallback) {
        if (!active()) {
            return fallback;
        }
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return fallback;
        }
        String group = user.getPrimaryGroup();
        return group == null || group.isBlank() ? fallback : group;
    }
}
