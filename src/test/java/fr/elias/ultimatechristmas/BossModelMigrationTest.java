package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.core.config.BossModelMigration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BossModelMigrationTest {
    @TempDir Path directory;

    @Test
    void upgradeKeepsEncounterCustomizationAndBacksUpTheOldConfig() throws Exception {
        Path file = directory.resolve("bosses.yml");
        Files.writeString(file, """
                bosses:
                  grinch:
                    health: 777
                    speed: 0.30
                    spawn: {world: winter, x: 12, y: 70, z: 24}
                    rewards: {custom: {weight: 1, shards: 321}}
                    model: {id: grinch, animations: {spawn: laugh}}
                  custom_boss: {health: 333, speed: 0.11}
                """);
        String before = Files.readString(file);
        assertTrue(BossModelMigration.migrate(file, getClass().getResourceAsStream("/modules/bosses.yml")));
        var config = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(777, config.getInt("bosses.grinch.health"));
        assertEquals(12, config.getInt("bosses.grinch.spawn.x"));
        assertEquals(321, config.getInt("bosses.grinch.rewards.custom.shards"));
        assertEquals(.18, config.getDouble("bosses.grinch.speed"));
        assertEquals(.11, config.getDouble("bosses.custom_boss.speed"));
        assertEquals("death", config.getString("bosses.grinch.model.animations.death"));
        assertEquals(3, config.getStringList("bosses.grinch.minions.types").size());
        assertEquals(before, Files.readString(directory.resolve("bosses.yml.before-" + BossModelMigration.REVISION)));
        String upgraded = Files.readString(file);
        assertFalse(BossModelMigration.migrate(file, getClass().getResourceAsStream("/modules/bosses.yml")));
        assertEquals(upgraded, Files.readString(file));
    }

    @Test
    void aSlowerGrinchStaysSlowAndInvalidYamlIsNotOverwritten() throws Exception {
        Path file = directory.resolve("bosses.yml");
        Files.writeString(file, "bosses:\n  grinch:\n    speed: 0.12\n");
        BossModelMigration.migrate(file, getClass().getResourceAsStream("/modules/bosses.yml"));
        assertEquals(.12, YamlConfiguration.loadConfiguration(file.toFile()).getDouble("bosses.grinch.speed"));
        Files.writeString(file, "bosses: [unterminated");
        assertThrows(Exception.class, () -> BossModelMigration.migrate(file,
                getClass().getResourceAsStream("/modules/bosses.yml")));
        assertEquals("bosses: [unterminated", Files.readString(file));
    }
}
