package fr.elias.ultimateChristmas.core.model;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Sends the bundled model resource pack to players, when a URL is configured.
 *
 * <p>The {@code display} model provider only shows up for players who have the
 * pack. The plugin cannot host a file, but it can push a hosted one with the
 * right hash: the admin hosts the unpacked
 * {@code pack/UltimateChristmas-Models.zip}, sets {@code resource-pack.url}, and
 * this computes that zip's SHA-1 so the client caches it correctly.
 *
 * <p>With no URL set it does nothing, so servers that distribute the pack
 * another way (server.properties, a pack-merging plugin) are unaffected.
 */
public final class ResourcePackSender implements Listener {

    private final Plugin plugin;
    private final String url;
    private final boolean required;
    private final byte[] hash;

    private ResourcePackSender(Plugin plugin, String url, boolean required, byte[] hash) {
        this.plugin = plugin;
        this.url = url;
        this.required = required;
        this.hash = hash;
    }

    /**
     * Creates a sender if a pack URL is configured, else {@code null}.
     *
     * @param packZip the unpacked bundled pack, used only for its hash
     */
    public static ResourcePackSender createIfConfigured(Plugin plugin, String url,
                                                        boolean required, File packZip) {
        if (url == null || url.isBlank()) {
            return null;
        }
        byte[] hash = sha1(packZip, plugin);
        return new ResourcePackSender(plugin, url.trim(), required, hash);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            // Hash may be null if the local zip was missing; sending without it
            // still works, the client just cannot cache by content.
            player.setResourcePack(url, hash, required);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not send the model resource pack to "
                    + player.getName() + ": " + ex.getMessage());
        }
    }

    private static byte[] sha1(File file, Plugin plugin) {
        if (file == null || !file.isFile()) {
            plugin.getLogger().warning("resource-pack.url is set but "
                    + "pack/UltimateChristmas-Models.zip is missing; sending without a hash.");
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(Files.readAllBytes(file.toPath()));
        } catch (NoSuchAlgorithmException | IOException ex) {
            plugin.getLogger().warning("Could not hash the resource pack: " + ex.getMessage());
            return null;
        }
    }
}
