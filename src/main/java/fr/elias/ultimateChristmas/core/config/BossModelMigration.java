package fr.elias.ultimateChristmas.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** One-time remaster upgrade; keep rewards, health, spawn locations and other bosses. */
public final class BossModelMigration {
    public static final String REVISION = "grinch-remastered-20261116";
    private static final List<String> IDS = List.of("grinch", "grinch_sneaky", "grinch_strong", "grinch_crazy");
    private BossModelMigration() { }

    public static boolean migrate(Path file, InputStream bundled) throws Exception {
        try (InputStream source = bundled) {
            if (source == null) throw new IOException("Missing bundled bosses.yml");
            if (!Files.exists(file)) return false; // Modulith installs fresh defaults.
            YamlConfiguration current = new YamlConfiguration();
            current.load(file.toFile()); // Invalid YAML must not be overwritten.
            if (REVISION.equals(current.getString("model-assets-revision"))) return false;
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(source, StandardCharsets.UTF_8));
            Path backup = file.resolveSibling("bosses.yml.before-" + REVISION);
            if (!Files.exists(backup)) Files.copy(file, backup);
            for (String id : IDS) {
                String path = "bosses." + id;
                if (!current.isConfigurationSection(path)) {
                    copySection(current, defaults.getConfigurationSection(path), path);
                } else {
                    copySection(current, defaults.getConfigurationSection(path + ".model"), path + ".model");
                    current.set(path + ".hitbox-scale", defaults.getDouble(path + ".hitbox-scale", 1));
                }
            }
            copySection(current, defaults.getConfigurationSection("bosses.grinch.minions"), "bosses.grinch.minions");
            current.set("bosses.grinch.speed", Math.min(.18, current.getDouble("bosses.grinch.speed", .18)));
            current.set("model-assets-revision", REVISION);
            current.save(file.toFile());
            return true;
        }
    }

    private static void copySection(YamlConfiguration target, ConfigurationSection source, String path) {
        if (source == null) return;
        target.set(path, null);
        source.getValues(true).forEach((key, value) -> {
            if (!(value instanceof ConfigurationSection)) target.set(path + "." + key, value);
        });
    }
}
