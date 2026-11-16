package fr.elias.ultimateChristmas;

import com.google.gson.*;
import fr.elias.ultimateChristmas.core.model.*;
import org.bukkit.NamespacedKey;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RigPoseTest {
    private JsonObject rigJson() {
        return JsonParser.parseReader(new InputStreamReader(
                getClass().getResourceAsStream("/rigs/grinch.json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private ModelRig rig() { return ModelRig.fromJson(rigJson(), NamespacedKey::fromString); }

    @Test
    void matchesPosesCapturedInBlockbenchIncludingBlinkAndMultiAxisCubes() {
        JsonArray fixtures = JsonParser.parseReader(new InputStreamReader(
                getClass().getResourceAsStream("/models/grinch-blockbench-poses.json"),
                StandardCharsets.UTF_8)).getAsJsonArray();
        ModelRig rig = rig();
        for (JsonElement entry : fixtures) {
            JsonObject fixture = entry.getAsJsonObject();
            String clip = fixture.get("clip").getAsString();
            double time = fixture.get("time").getAsDouble();
            var pose = RigPose.evaluate(rig, rig.animation(clip), time, null, 0);
            for (var expected : fixture.getAsJsonObject("bones").entrySet()) {
                assertMatrix(expected.getValue().getAsJsonArray(), pose.get(expected.getKey()),
                        clip + " " + time + " bone " + expected.getKey());
            }
            for (JsonElement boneEntry : rigJson().getAsJsonArray("bones")) {
                JsonObject bone = boneEntry.getAsJsonObject();
                if (!bone.has("source_element")) continue;
                String source = bone.get("source_element").getAsString();
                assertMatrix(fixture.getAsJsonObject("cubes").getAsJsonArray(source),
                        pose.get(bone.get("name").getAsString()), clip + " cube " + source);
            }
        }
    }

    private void assertMatrix(JsonArray expected, Matrix4f actual, String label) {
        assertNotNull(actual, label);
        float[] values = actual.get(new float[16]);
        for (int i = 0; i < 16; i++) assertEquals(expected.get(i).getAsDouble(), values[i], .00015, label + " [" + i + "]");
    }

    @Test
    void overlayKeepsUnrelatedBlinkChannel() {
        ModelRig rig = rig();
        var pose = RigPose.evaluate(rig, rig.animation("idle"), 2.43, rig.animation("attack"), .24);
        Matrix4f relative = new Matrix4f(pose.get("head")).invert().mul(pose.get("eyelid_l"));
        assertEquals(235, relative.m11(), .001);
    }

    @Test
    void missingScaleIsIdentityAndStepKeysHoldUntilTheNextKey() {
        assertArrayEquals(new float[]{1, 1, 1}, RigPose.sample(List.of(), .2, 1));
        var keys = List.of(new ModelRig.Keyframe(0, 2, 3, 4, "step"),
                new ModelRig.Keyframe(1, 8, 9, 10));
        assertArrayEquals(new float[]{2, 3, 4}, RigPose.sample(keys, .99, 0));
        assertArrayEquals(new float[]{8, 9, 10}, RigPose.sample(keys, 1, 0));
    }

    @Test
    void deathHoldsAndCannotBeInterruptedWhileOrdinaryClipsReturnToWalking() {
        ModelRig rig = rig();
        AnimationPlayback clock = new AnimationPlayback(rig);
        clock.loop("walk");
        assertTrue(clock.play("laugh", 30)); // Authored loop, requested as a one-shot.
        clock.advance(rig.animation("laugh").length(), 1);
        assertNotNull(clock.shot());
        clock.advance(.05, 1);
        assertNull(clock.shot());
        assertEquals("walk", clock.loop().name());
        assertTrue(clock.finish("death"));
        clock.advance(20, 1);
        assertNull(clock.loop());
        assertEquals(rig.animation("death").length(), clock.shotTime());
        assertTrue(rig.animation("death").hold());
        assertFalse(rig.animation("death").loop());
        assertFalse(clock.play("attack", 1000));
    }

    @Test
    void hurtCannotInterruptSummoningAndSlowMovementUsesWalk() {
        ModelRig rig = rig();
        AnimationPlayback clock = new AnimationPlayback(rig);
        assertTrue(clock.play("summon", 40));
        clock.advance(.6, 1);
        assertFalse(clock.play("hurt", 20));
        assertEquals(.6, clock.shotTime());
        var states = new DisplayModelRenderer.StateClips("idle", "walk", "run", null, .32, .85);
        assertEquals("walk", DisplayModelRenderer.movementClip(rig, states, .18, false));
        assertEquals("run", DisplayModelRenderer.movementClip(rig, states, .4, false));
        assertEquals("idle", DisplayModelRenderer.movementClip(rig, states, 0, false));
    }
}
