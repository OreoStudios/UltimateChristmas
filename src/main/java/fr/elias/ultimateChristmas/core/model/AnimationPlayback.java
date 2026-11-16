package fr.elias.ultimateChristmas.core.model;

/** Animation clock independent of Bukkit, including one-shot priorities and death holds. */
public final class AnimationPlayback {
    private final ModelRig rig;
    private String loop;
    private String shot;
    private double loopTime;
    private double shotTime;
    private int priority;
    private boolean terminal;

    public AnimationPlayback(ModelRig rig) { this.rig = rig; }

    public void loop(String clip) {
        if (!terminal && rig.has(clip) && !clip.equals(loop)) {
            loop = clip;
            loopTime = 0;
        }
    }

    public boolean play(String clip, int requestedPriority) {
        if (terminal || !rig.has(clip)
                || (shot != null && (priority > requestedPriority || shot.equals(clip)))) return false;
        shot = clip;
        shotTime = 0;
        priority = requestedPriority;
        return true;
    }

    public boolean finish(String clip) {
        if (terminal || !rig.has(clip)) return false;
        terminal = true;
        loop = null;
        shot = clip;
        shotTime = 0;
        priority = Integer.MAX_VALUE;
        return true;
    }

    public void advance(double seconds, double movementRate) {
        ModelRig.Animation base = loop();
        if (base != null) {
            loopTime += seconds * movementRate;
            loopTime = base.loop() ? loopTime % base.length() : Math.min(loopTime, base.length());
        }
        ModelRig.Animation overlay = shot();
        if (overlay != null) {
            // Render the final key once before returning to locomotion.
            if (shotTime >= overlay.length() && !terminal && !overlay.hold()) {
                shot = null;
                priority = 0;
            } else {
                shotTime = Math.min(overlay.length(), shotTime + seconds);
            }
        }
    }

    public ModelRig.Animation loop() { return rig.animation(loop); }
    public ModelRig.Animation shot() { return rig.animation(shot); }
    public double loopTime() { return loopTime; }
    public double shotTime() { return shotTime; }
    public boolean playingOnce() { return shot != null; }
    public boolean terminal() { return terminal; }
}
