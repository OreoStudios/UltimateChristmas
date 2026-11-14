package fr.elias.ultimateChristmas;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.elias.ultimateChristmas.core.model.ModelRig;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the contract between the model compiler and the runtime: the
 * {@code rigs/*.json} the Python tool emits must parse into a usable rig, with
 * bones in parent-before-child order (the animation engine relies on that) and
 * the animations the model was authored with.
 */
class ModelRigTest {

    private static final Path RIG = Path.of("src", "main", "resources", "rigs", "grinch.json");

    private ModelRig loadGrinch() throws Exception {
        try (Reader reader = Files.newBufferedReader(RIG, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            // Same resolver the runtime uses; NamespacedKey.fromString needs no server.
            return ModelRig.fromJson(json, NamespacedKey::fromString);
        }
    }

    @Test
    void theCompiledGrinchRigParses() throws Exception {
        ModelRig rig = loadGrinch();
        assertNotNull(rig);
        assertFalse(rig.bones().isEmpty(), "rig has no bones");
    }

    @Test
    void everyBoneAppearsAfterItsParent() throws Exception {
        ModelRig rig = loadGrinch();
        Set<String> seen = new HashSet<>();
        for (ModelRig.Bone bone : rig.bones()) {
            if (bone.parent() != null) {
                assertTrue(seen.contains(bone.parent()),
                        "bone '" + bone.name() + "' comes before its parent '"
                                + bone.parent() + "'; the FK walk would break");
            }
            seen.add(bone.name());
        }
    }

    @Test
    void rigKeepsItsAuthoredAnimations() throws Exception {
        ModelRig rig = loadGrinch();
        assertTrue(rig.has("walk"), "the walk animation was lost in compilation");
        ModelRig.Animation walk = rig.animation("walk");
        assertTrue(walk.length() > 0);
        assertFalse(walk.bones().isEmpty(), "walk animates no bones");
        // At least one bone must carry rotation keyframes, or nothing moves.
        boolean anyRotation = walk.bones().values().stream()
                .anyMatch(channels -> !channels.rotation().isEmpty());
        assertTrue(anyRotation, "walk has no rotation keyframes on any bone");
    }

    @Test
    void bonesWithGeometryCarryAnItemModel() throws Exception {
        ModelRig rig = loadGrinch();
        // The leg/body/head bones render; only pure joints (the root) may not.
        long rendered = rig.bones().stream().filter(ModelRig.Bone::hasModel).count();
        assertTrue(rendered >= 6, "expected most bones to have a model, got " + rendered);
    }
}
