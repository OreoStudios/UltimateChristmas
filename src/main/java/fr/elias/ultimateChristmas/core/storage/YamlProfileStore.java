package fr.elias.ultimateChristmas.core.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One YAML file per player under {@code playerdata/}.
 *
 * <p>A file per player keeps a single load cheap and means a corrupted record
 * costs one player's progress rather than the whole server's. Writes go to a
 * temporary file and are then moved into place, so a crash mid-save cannot
 * leave a half-written profile behind.
 */
public final class YamlProfileStore implements ProfileStore {

    private final Plugin plugin;
    private final File directory;

    public YamlProfileStore(Plugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "playerdata");
    }

    @Override
    public void open() throws IOException {
        Files.createDirectories(directory.toPath());
    }

    @Override
    public void close() {
        // Nothing held open.
    }

    private File fileFor(UUID id) {
        return new File(directory, id + ".yml");
    }

    @Override
    public PlayerProfile load(UUID id) {
        File file = fileFor(id);
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return ProfileCodec.decode(id, ProfileCodec.fromSection(yaml));
    }

    @Override
    public void save(PlayerProfile profile) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ProfileCodec.flattenInto(yaml, ProfileCodec.encode(profile));

        File target = fileFor(profile.id());
        File temporary = new File(directory, profile.id() + ".yml.tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void saveAll(Collection<PlayerProfile> profiles) {
        for (PlayerProfile profile : profiles) {
            try {
                save(profile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not save profile for " + profile.name(), ex);
            }
        }
    }

    @Override
    public List<Entry> top(String stat, int limit) {
        List<Entry> rows = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return rows;
        }
        for (File file : files) {
            UUID id = parseId(file.getName());
            if (id == null) {
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Map<String, Long> stats = ProfileCodec.longMap(yaml.get("stats"));
            long value = stats.getOrDefault(stat, 0L);
            if (value > 0) {
                rows.add(new Entry(id, yaml.getString("name", "?"), value));
            }
        }
        rows.sort(Comparator.comparingLong(Entry::value).reversed());
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    @Override
    public Collection<UUID> knownPlayers() {
        List<UUID> ids = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return ids;
        }
        for (File file : files) {
            UUID id = parseId(file.getName());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private UUID parseId(String fileName) {
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 4));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
