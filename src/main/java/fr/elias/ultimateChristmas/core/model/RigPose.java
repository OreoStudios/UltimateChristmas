package fr.elias.ultimateChristmas.core.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure forward kinematics, shared by the renderer and Blockbench pose checks. */
public final class RigPose {
    private RigPose() { }

    public static Map<String, Matrix4f> evaluate(ModelRig rig, ModelRig.Animation loop,
                                                double loopTime, ModelRig.Animation shot,
                                                double shotTime) {
        Map<String, Matrix4f> matrices = new LinkedHashMap<>();
        Map<String, ModelRig.Bone> bones = new LinkedHashMap<>();
        for (ModelRig.Bone bone : rig.bones()) {
            ModelRig.Channels base = loop == null ? null : loop.bones().get(bone.name());
            ModelRig.Channels overlay = shot == null ? null : shot.bones().get(bone.name());
            float[] rotation = channel(base, overlay, "rotation", loopTime, shotTime, 0);
            float[] position = channel(base, overlay, "position", loopTime, shotTime, 0);
            float[] scale = channel(base, overlay, "scale", loopTime, shotTime, 1);
            float[] pivot = bone.pivot();
            ModelRig.Bone parent = bones.get(bone.parent());
            float[] parentPivot = parent == null ? new float[3] : parent.pivot();
            Matrix4f world = parent == null ? new Matrix4f()
                    : new Matrix4f(matrices.get(parent.name()));
            world.translate((pivot[0] - parentPivot[0] + position[0]) / 16f,
                    (pivot[1] - parentPivot[1] + position[1]) / 16f,
                    (pivot[2] - parentPivot[2] + position[2]) / 16f);
            float[] rest = bone.rest();
            world.rotate(new Quaternionf().rotationZYX(radians(rest[2] + rotation[2]),
                    radians(rest[1] + rotation[1]), radians(rest[0] + rotation[0])));
            // Matrices retain inherited nonuniform scale, including eyelid blink.
            world.scale(nonzero(scale[0]), nonzero(scale[1]), nonzero(scale[2]));
            matrices.put(bone.name(), world);
            bones.put(bone.name(), bone);
        }
        return matrices;
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    private static float nonzero(float value) { return value == 0 ? 0.00001f : value; }

    private static float[] channel(ModelRig.Channels base, ModelRig.Channels overlay,
                                    String channel, double baseTime, double shotTime, float fallback) {
        List<ModelRig.Keyframe> shot = track(overlay, channel);
        return shot.isEmpty() ? sample(track(base, channel), baseTime, fallback)
                : sample(shot, shotTime, fallback);
    }

    private static List<ModelRig.Keyframe> track(ModelRig.Channels channels, String name) {
        if (channels == null) return List.of();
        return switch (name) {
            case "rotation" -> channels.rotation();
            case "position" -> channels.position();
            default -> channels.scale();
        };
    }

    public static float[] sample(List<ModelRig.Keyframe> frames, double time, float fallback) {
        if (frames.isEmpty()) return new float[]{fallback, fallback, fallback};
        if (time <= frames.getFirst().time()) return value(frames.getFirst());
        if (time >= frames.getLast().time()) return value(frames.getLast());
        for (int i = 0; i < frames.size() - 1; i++) {
            ModelRig.Keyframe a = frames.get(i), b = frames.get(i + 1);
            if (time < a.time() || time >= b.time()) continue;
            if ("step".equals(a.interpolation())) return value(a);
            float t = (float) ((time - a.time()) / (b.time() - a.time()));
            float[] av = value(a), bv = value(b), result = new float[3];
            if ("catmullrom".equals(a.interpolation()) || "catmullrom".equals(b.interpolation())) {
                float[] before = value(frames.get(Math.max(0, i - 1)));
                float[] after = value(frames.get(Math.min(frames.size() - 1, i + 2)));
                for (int axis = 0; axis < 3; axis++) {
                    float p0 = before[axis], p1 = av[axis], p2 = bv[axis], p3 = after[axis];
                    result[axis] = .5f * ((2 * p1) + (-p0 + p2) * t
                            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                            + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
                }
            } else {
                for (int axis = 0; axis < 3; axis++) result[axis] = av[axis] + (bv[axis] - av[axis]) * t;
            }
            return result;
        }
        return value(frames.getLast());
    }

    private static float[] value(ModelRig.Keyframe frame) {
        return new float[]{frame.x(), frame.y(), frame.z()};
    }
}
