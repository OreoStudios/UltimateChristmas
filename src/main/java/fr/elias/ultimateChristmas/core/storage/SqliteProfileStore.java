package fr.elias.ultimateChristmas.core.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite-backed storage, for servers large enough that a file per player hurts.
 *
 * <p>The full profile lives in one JSON column, which keeps the schema stable as
 * features are added. Stats are additionally denormalised into {@code uc_stats}
 * purely so leaderboards are an indexed query instead of a full scan.
 *
 * <p>The driver ships with the server, so nothing is shaded into the jar.
 */
public final class SqliteProfileStore implements ProfileStore {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final Plugin plugin;
    private final Gson gson = new Gson();
    private final File file;

    private Connection connection;

    public SqliteProfileStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.db");
    }

    @Override
    public void open() throws SQLException, ClassNotFoundException {
        // Fail with a clear message rather than a NoClassDefFoundError deep in a task.
        Class.forName("org.sqlite.JDBC");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create the plugin data folder for players.db");
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS uc_players (
                        uuid    TEXT PRIMARY KEY NOT NULL,
                        name    TEXT,
                        updated INTEGER NOT NULL,
                        data    TEXT NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS uc_stats (
                        uuid  TEXT NOT NULL,
                        stat  TEXT NOT NULL,
                        value INTEGER NOT NULL,
                        PRIMARY KEY (uuid, stat)
                    )""");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS uc_stats_lookup ON uc_stats (stat, value DESC)");
        }
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not close players.db: " + ex.getMessage());
        } finally {
            connection = null;
        }
    }

    @Override
    public PlayerProfile load(UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT data FROM uc_players WHERE uuid = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                Map<String, Object> data = gson.fromJson(results.getString("data"), MAP_TYPE);
                return ProfileCodec.decode(id, data == null ? new LinkedHashMap<>() : data);
            }
        }
    }

    @Override
    public void save(PlayerProfile profile) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            writeOne(profile);
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @Override
    public void saveAll(Collection<PlayerProfile> profiles) throws SQLException {
        if (profiles.isEmpty()) {
            return;
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (PlayerProfile profile : profiles) {
                writeOne(profile);
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void writeOne(PlayerProfile profile) throws SQLException {
        Map<String, Object> encoded = ProfileCodec.encode(profile);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO uc_players (uuid, name, updated, data) VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name,
                    updated = excluded.updated, data = excluded.data""")) {
            statement.setString(1, profile.id().toString());
            statement.setString(2, profile.name());
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, gson.toJson(encoded));
            statement.executeUpdate();
        }

        Map<String, Long> stats = profile.stats();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO uc_stats (uuid, stat, value) VALUES (?, ?, ?)
                ON CONFLICT(uuid, stat) DO UPDATE SET value = excluded.value""")) {
            for (Map.Entry<String, Long> entry : stats.entrySet()) {
                statement.setString(1, profile.id().toString());
                statement.setString(2, entry.getKey());
                statement.setLong(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public List<Entry> top(String stat, int limit) throws SQLException {
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.uuid AS uuid, p.name AS name, s.value AS value
                FROM uc_stats s LEFT JOIN uc_players p ON p.uuid = s.uuid
                WHERE s.stat = ? AND s.value > 0
                ORDER BY s.value DESC LIMIT ?""")) {
            statement.setString(1, stat);
            statement.setInt(2, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    try {
                        rows.add(new Entry(UUID.fromString(results.getString("uuid")),
                                results.getString("name"), results.getLong("value")));
                    } catch (IllegalArgumentException ignored) {
                        // A malformed uuid row should not break the whole board.
                    }
                }
            }
        }
        return rows;
    }

    @Override
    public Collection<UUID> knownPlayers() throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid FROM uc_players");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                try {
                    ids.add(UUID.fromString(results.getString(1)));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
        return ids;
    }
}
