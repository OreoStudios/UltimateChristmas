package fr.elias.ultimateChristmas.core.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed animation rig: a bone hierarchy plus named animations.
 *
 * <p>Produced by {@code tools/generate_resourcepack.py} from a {@code .bbmodel}
 * and loaded at runtime as {@code rigs/<id>.json}. The plugin's own display
 * renderer reads this to pose and animate a boss without ModelEngine or
 * BetterModel, playing whatever animations the source model defined.
 *
 * <p>Deliberately a plain data holder: all the maths lives in
 * {@link RigPose}, and the format is whatever the compiler emits, so a new
 * model is a config-and-asset change with no code.
 */
public final class ModelRig {

    /**
     * One bone.
     *
     * @param name   unique within the rig
     * @param parent parent bone name, or {@code null} for the root
     * @param pivot  rotation pivot in Blockbench pixels (16 px = 1 block)
     * @param model  the item-model key this bone renders, or {@code null} for a
     *               pure joint that only moves its children
     */
    public record Bone(String name, String parent, float[] pivot, float[] rest,
                       float[] modelOffset, NamespacedKey model) {

        public boolean hasModel() {
            return model != null;
        }
    }

    /** One keyframe: a time in seconds and an (x, y, z) value. */
    public record Keyframe(double time, float x, float y, float z, String interpolation) {
        public Keyframe(double time, float x, float y, float z) {
            this(time, x, y, z, "linear");
        }
    }

    /** The rotation, position and scale tracks for one bone in one animation. */
    public record Channels(List<Keyframe> rotation, List<Keyframe> position,
                           List<Keyframe> scale) {
    }

    /**
     * One animation.
     *
     * @param name    e.g. {@code walk}, {@code attack}, {@code laugh}
     * @param length  loop length in seconds
     * @param loop    whether it repeats (otherwise it plays once and ends)
     * @param bones   per-bone channel tracks, keyed by bone name
     */
    public record Animation(String name, double length, boolean loop, boolean hold,
                            Map<String, Channels> bones) {
    }

    private final String name;
    private final List<Bone> bones;
    private final Map<String, Animation> animations;

    private ModelRig(String name, List<Bone> bones, Map<String, Animation> animations) {
        this.name = name;
        this.bones = List.copyOf(bones);
        this.animations = Map.copyOf(animations);
    }

    public String name() {
        return name;
    }

    /** Bones in hierarchy order (every parent before its children). */
    public List<Bone> bones() {
        return bones;
    }

    public Animation animation(String animationName) {
        return animationName == null ? null : animations.get(
                animationName.startsWith("animation.") ? animationName.substring(10) : animationName);
    }

    public boolean has(String animationName) {
        return animation(animationName) != null;
    }

    public java.util.Set<String> animationNames() {
        return animations.keySet();
    }

    // ----------------------------------------------------------------- parse

    /**
     * Parses a rig from its JSON form.
     *
     * @throws IllegalArgumentException if the document is not a usable rig
     */
    public static ModelRig fromJson(JsonObject root, NamespaceResolver keys) {
        String name = root.has("name") ? root.get("name").getAsString() : "model";

        List<Bone> bones = new ArrayList<>();
        for (JsonElement element : array(root, "bones")) {
            JsonObject boneJson = element.getAsJsonObject();
            String boneName = boneJson.get("name").getAsString();
            String parent = boneJson.has("parent") && !boneJson.get("parent").isJsonNull()
                    ? boneJson.get("parent").getAsString() : null;
            float[] pivot = readVector(boneJson.getAsJsonArray("pivot"));
            // A bone's own (rest) rotation in Blockbench euler degrees, and the
            // shift the compiler applied to the bone's geometry to fit it inside
            // the item-model coordinate box (undone at render time). Both are
            // optional, so a rig compiled by an older tool still loads.
            float[] rest = boneJson.has("rotation")
                    ? readVector(boneJson.getAsJsonArray("rotation")) : new float[]{0, 0, 0};
            float[] modelOffset = boneJson.has("model_offset")
                    ? readVector(boneJson.getAsJsonArray("model_offset")) : new float[]{0, 0, 0};
            NamespacedKey model = null;
            if (boneJson.has("model") && !boneJson.get("model").isJsonNull()) {
                model = keys.resolve(boneJson.get("model").getAsString());
            }
            bones.add(new Bone(boneName, parent, pivot, rest, modelOffset, model));
        }
        if (bones.isEmpty()) {
            throw new IllegalArgumentException("rig '" + name + "' has no bones");
        }

        Map<String, Animation> animations = new LinkedHashMap<>();
        if (root.has("animations") && root.get("animations").isJsonObject()) {
            JsonObject animsJson = root.getAsJsonObject("animations");
            for (String animName : animsJson.keySet()) {
                animations.put(animName, parseAnimation(animName,
                        animsJson.getAsJsonObject(animName)));
            }
        }
        return new ModelRig(name, bones, animations);
    }

    private static Animation parseAnimation(String name, JsonObject json) {
        double length = json.has("length") ? json.get("length").getAsDouble() : 1.0;
        boolean loop = !json.has("loop") || json.get("loop").getAsBoolean();

        Map<String, Channels> bones = new LinkedHashMap<>();
        if (json.has("bones") && json.get("bones").isJsonObject()) {
            JsonObject bonesJson = json.getAsJsonObject("bones");
            for (String boneName : bonesJson.keySet()) {
                JsonObject channels = bonesJson.getAsJsonObject(boneName);
                bones.put(boneName, new Channels(
                        readKeyframes(channels, "rotation"),
                        readKeyframes(channels, "position"),
                        readKeyframes(channels, "scale")));
            }
        }
        boolean hold = json.has("hold") && json.get("hold").getAsBoolean();
        return new Animation(name, Math.max(0.001, length), loop && !hold, hold, bones);
    }

    private static List<Keyframe> readKeyframes(JsonObject channels, String channel) {
        List<Keyframe> frames = new ArrayList<>();
        if (channels.has(channel) && channels.get(channel).isJsonArray()) {
            for (JsonElement element : channels.getAsJsonArray(channel)) {
                JsonArray frame = element.getAsJsonArray();
                frames.add(new Keyframe(frame.get(0).getAsDouble(),
                        frame.get(1).getAsFloat(), frame.get(2).getAsFloat(),
                        frame.get(3).getAsFloat(), frame.size() > 4
                                ? frame.get(4).getAsString() : "linear"));
            }
        }
        frames.sort(java.util.Comparator.comparingDouble(Keyframe::time));
        return List.copyOf(frames);
    }

    private static float[] readVector(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(),
                array.get(2).getAsFloat()};
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : new JsonArray();
    }

    /** Turns a {@code namespace:path} string into a key owned by the plugin. */
    @FunctionalInterface
    public interface NamespaceResolver {
        NamespacedKey resolve(String namespacedKey);
    }
}
