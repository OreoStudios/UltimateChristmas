package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the optional plugins we integrate with, once, at startup.
 *
 * <p>Each integration is reached through a small wrapper that degrades to a
 * no-op when its plugin is absent, so calling code never has to guard. The 3.x
 * code scattered {@code try/catch (Throwable)} blocks and
 * {@code Class.forName} checks through the feature classes, which made it
 * impossible to tell what was actually required.
 */
public final class Hooks {

    private final UltimateChristmas plugin;

    private final WorldGuardHook worldGuard;
    private final LuckPermsHook luckPerms;
    private final VaultHook vault;
    private final NoteBlockHook noteBlock;
    private final DisguiseHook disguises;
    private final MythicMobsHook mythicMobs;
    private final ItemsAdderHook itemsAdder;
    private final ModelHook models;

    public Hooks(UltimateChristmas plugin) {
        this.plugin = plugin;
        this.worldGuard = new WorldGuardHook(plugin);
        this.luckPerms = new LuckPermsHook(plugin);
        this.vault = new VaultHook(plugin);
        this.noteBlock = new NoteBlockHook(plugin);
        this.disguises = new DisguiseHook(plugin);
        this.mythicMobs = new MythicMobsHook(plugin);
        this.itemsAdder = new ItemsAdderHook(plugin);
        this.models = new ModelHook(plugin);
    }

    /** Resolves every hook and logs a single summary line. */
    public void detectAll() {
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (PluginHook hook : List.of(worldGuard, luckPerms, vault, noteBlock,
                disguises, mythicMobs, itemsAdder)) {
            if (hook.detect()) {
                found.add(hook.pluginName());
            } else {
                missing.add(hook.pluginName());
            }
        }
        // ModelHook covers two plugins, so it is detected on its own.
        models.detect();
        plugin.getLogger().info("Integrations active: "
                + (found.isEmpty() ? "none" : String.join(", ", found)));
        if (!missing.isEmpty()) {
            plugin.getLogger().info("Integrations not installed (all optional): "
                    + String.join(", ", missing));
        }
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public LuckPermsHook luckPerms() {
        return luckPerms;
    }

    public VaultHook vault() {
        return vault;
    }

    public NoteBlockHook noteBlock() {
        return noteBlock;
    }

    public DisguiseHook disguises() {
        return disguises;
    }

    public MythicMobsHook mythicMobs() {
        return mythicMobs;
    }

    public ItemsAdderHook itemsAdder() {
        return itemsAdder;
    }

    /** Custom Blockbench model rendering via ModelEngine or BetterModel. */
    public ModelHook models() {
        return models;
    }

    /** Whether a plugin is installed and running. */
    public static boolean present(String name) {
        Plugin found = Bukkit.getPluginManager().getPlugin(name);
        return found != null && found.isEnabled();
    }
}
