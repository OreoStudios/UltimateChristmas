package fr.elias.ultimateChristmas.core.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Applies textures to player-head items.
 *
 * <p>Accepts three shapes so configs can use whichever is convenient:
 * a plain player name, a raw texture URL, or the base64 texture blob that
 * head-database sites hand out. Everything is resolved locally — no blocking
 * call to Mojang on the main thread, which is what made the 3.x head code
 * occasionally hitch the server.
 */
public final class Heads {

    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/]{40,}={0,2}$");
    private static final String TEXTURE_HOST = "http://textures.minecraft.net/texture/";

    private Heads() {
    }

    public static void apply(SkullMeta meta, String value) {
        String trimmed = value.trim();

        if (BASE64.matcher(trimmed).matches()) {
            applyTexture(meta, trimmed);
            return;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            applyTexture(meta, encode(trimmed));
            return;
        }
        if (trimmed.length() <= 16 && trimmed.matches("[A-Za-z0-9_]+")) {
            // A bare name: let the server resolve it from its own profile cache.
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(trimmed));
            return;
        }
        // A bare texture hash.
        applyTexture(meta, encode(TEXTURE_HOST + trimmed));
    }

    private static void applyTexture(SkullMeta meta, String base64) {
        // The profile UUID only has to be stable and unique; deriving it from the
        // texture means identical heads share one profile and stack together.
        PlayerProfile profile = Bukkit.createProfile(
                UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8)), null);
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
    }

    private static String encode(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
