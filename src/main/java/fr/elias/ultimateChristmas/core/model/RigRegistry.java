package fr.elias.ultimateChristmas.core.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads the compiled animation rigs the display renderer can play.
 *
 * <p>Rigs are {@code rigs/<id>.json} files produced by the resource-pack
 * compiler. They are read from the plugin's data folder, which the plugin fills
 * with the bundled ones on startup; an admin can drop a freshly compiled rig in
 * the same folder and reload, so adding a model needs no new jar.
 */
public final class RigRegistry {

    private final Plugin plugin;
    private final Map<String, ModelRig> rigs = new LinkedHashMap<>();

    public RigRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Reloads every {@code *.json} rig from {@code <dataFolder>/rigs/}. */
    public void reload() {
        rigs.clear();
        File dir = new File(plugin.getDataFolder(), "rigs");
        File[] files = dir.listFiles((folder, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ModelRig rig = ModelRig.fromJson(json, RigRegistry::resolveKey);
                rigs.put(rig.name().toLowerCase(java.util.Locale.ROOT), rig);
            } catch (IOException | RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not load model rig " + file.getName(), ex);
            }
        }
        if (!rigs.isEmpty()) {
            plugin.getLogger().info("Loaded " + rigs.size() + " model rig(s): " + rigs.keySet());
        }
    }

    public ModelRig get(String id) {
        return id == null ? null : rigs.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    public java.util.Set<String> ids() {
        return rigs.keySet();
    }

    public boolean isEmpty() {
        return rigs.isEmpty();
    }

    private static NamespacedKey resolveKey(String namespacedKey) {
        NamespacedKey key = NamespacedKey.fromString(namespacedKey);
        if (key == null) {
            throw new IllegalArgumentException("bad item-model key: " + namespacedKey);
        }
        return key;
    }
}
