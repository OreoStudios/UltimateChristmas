package fr.elias.ultimateChristmas;

import com.google.gson.*;
import fr.elias.ultimateChristmas.core.model.*;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RemasteredAssetsTest {
    @Test
    void allFourRigsRetainTheirClipsAndConfigReferencesResolve() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/modules/bosses.yml"), StandardCharsets.UTF_8));
        Map<String, String> ids = Map.of("grinch", "grinch", "grinch_sneaky", "grinch_minion_sneaky",
                "grinch_strong", "grinch_minion_strong", "grinch_crazy", "grinch_minion_crazy");
        for (var entry : ids.entrySet()) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(
                    getClass().getResourceAsStream("/rigs/" + entry.getValue() + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            ModelRig rig = ModelRig.fromJson(json, NamespacedKey::fromString);
            Set<String> expected = entry.getKey().equals("grinch")
                    ? Set.of("idle", "walk", "run", "attack", "laugh", "summon", "steal", "hurt", "rage", "death", "celebrate")
                    : Set.of("idle", "walk", "run", "attack", "laugh", "carry_present", "hurt", "death");
            assertEquals(expected, rig.animationNames(), entry.getValue());
            Set<String> bones = new HashSet<>();
            for (var bone : rig.bones()) {
                assertTrue(bone.parent() == null || bones.contains(bone.parent()), bone.name());
                assertTrue(bones.add(bone.name()), "duplicate bone");
            }
            for (String clip : expected) {
                assertTrue(bones.containsAll(rig.animation(clip).bones().keySet()));
            }
            var model = config.getConfigurationSection("bosses." + entry.getKey() + ".model");
            for (String key : List.of("animations", "ability-animations")) {
                var clips = model.getConfigurationSection(key);
                if (clips != null) for (String name : clips.getKeys(false)) {
                    assertTrue(rig.has(clips.getString(name)), entry.getKey() + ":" + name);
                }
            }
            assertTrue(rig.has("animation.death"));
        }
        assertEquals(.18, config.getDouble("bosses.grinch.speed"));
        assertEquals(List.of("grinch_sneaky", "grinch_strong", "grinch_crazy"),
                config.getStringList("bosses.grinch.minions.types"));
        assertEquals("summon", config.getString("bosses.grinch.model.ability-animations.summon"));
    }
}
