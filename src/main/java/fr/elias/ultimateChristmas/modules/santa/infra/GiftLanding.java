package fr.elias.ultimateChristmas.modules.santa.infra;

import fr.elias.ultimateChristmas.core.model.ModelRig;
import fr.elias.ultimateChristmas.core.model.RigPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where and when the present in Santa's drop clip comes to rest.
 *
 * <p>Read from the rig rather than hard-coded, so re-animating the drop in
 * Blockbench moves the real player head along with it.
 *
 * @param landTime  seconds into the clip when the present stops falling
 * @param position  its resting point in model space (blocks, before the root transform)
 */
public record GiftLanding(double landTime, Vector3f position) {

    private static final double STEP = 0.05;
    private static final float SETTLED = 0.03f;

    /** Returns {@code null} if the rig has no such clip or bone. */
    public static GiftLanding find(ModelRig rig, String clip, String bone) {
        ModelRig.Animation animation = rig == null ? null : rig.animation(clip);
        if (animation == null || rig.bones().stream().noneMatch(b -> b.name().equals(bone))) {
            return null;
        }
        int steps = (int) Math.floor(animation.length() / STEP);
        Vector3f[] samples = new Vector3f[steps + 1];
        for (int i = 0; i <= steps; i++) {
            Matrix4f matrix = RigPose.evaluate(rig, null, 0, animation, Math.min(i * STEP,
                    animation.length())).get(bone);
            samples[i] = matrix.getTranslation(new Vector3f());
        }
        Vector3f rest = samples[steps];
        int landed = steps;
        while (landed > 0 && Math.abs(samples[landed - 1].y - rest.y) <= SETTLED) {
            landed--;
        }
        return new GiftLanding(landed * STEP, rest);
    }
}
