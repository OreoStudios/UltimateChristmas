// File: src/main/java/fr/elias/ultimateChristmas/util/Lang.java
package fr.elias.ultimateChristmas.util;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class Lang {

    private static UltimateChristmas plugin;
    private static FileConfiguration cfg;

    /**
     * Called once in onEnable() and again on reload.
     */
    public static void init(UltimateChristmas pl) {
        plugin = pl;
        reload();
    }

    /**
     * Reloads lang.yml from disk using ConfigUtil.
     */
    public static void reload() {
        if (plugin == null) return;
        cfg = plugin.getConfig("lang.yml");
    }

    /**
     * Get a raw string from lang.yml (color codes still with '&').
     */
    public static String raw(String path, String def) {
        if (cfg == null) return def;
        return cfg.getString(path, def);
    }

    /**
     * Get a colored string from lang.yml (using & -> §).
     */
    public static String get(String path, String def) {
        String s = raw(path, def);
        return Msg.color(s);
    }

    /**
     * Send a message from lang.yml to a CommandSender.
     */
    public static void send(CommandSender to, String path, String def) {
        to.sendMessage(get(path, def));
    }
}
