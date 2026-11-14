package fr.elias.ultimateChristmas.core.effect;

import fr.elias.ultimateChristmas.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared particle, sound, firework and title helpers.
 *
 * <p>Festive visuals get reused constantly across modules, and doing them by
 * hand each time is how the 3.x code ended up with four slightly different
 * firework spawners.
 */
public final class Fx {

    private static final List<Color> FESTIVE = List.of(
            Color.fromRGB(0xC4, 0x1E, 0x3A),   // holly red
            Color.fromRGB(0x16, 0x5B, 0x33),   // spruce green
            Color.WHITE,
            Color.fromRGB(0xFF, 0xD7, 0x00));  // gold

    private Fx() {
    }

    // ------------------------------------------------------------- particles

    public static void burst(Location at, Particle particle, int count, double spread) {
        World world = at.getWorld();
        if (world != null) {
            world.spawnParticle(particle, at, count, spread, spread, spread, 0.02);
        }
    }

    /** A ring of particles, for summoning circles and area markers. */
    public static void ring(Location centre, Particle particle, double radius, int points) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        for (int index = 0; index < points; index++) {
            double angle = 2 * Math.PI * index / points;
            world.spawnParticle(particle,
                    centre.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0);
        }
    }

    /** An upward spiral, used for level-ups and gift openings. */
    public static void helix(Location base, Particle particle, double height, double radius) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        int steps = (int) (height * 10);
        for (int step = 0; step < steps; step++) {
            double progress = step / (double) steps;
            double angle = progress * Math.PI * 6;
            world.spawnParticle(particle, base.clone().add(
                    Math.cos(angle) * radius, progress * height, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0);
        }
    }

    /** Falling snow above a location, used by the ambience and snow modules. */
    public static void snowfall(Location centre, double radius, int count) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < count; index++) {
            double offsetX = (random.nextDouble() - 0.5) * radius * 2;
            double offsetZ = (random.nextDouble() - 0.5) * radius * 2;
            double offsetY = 3 + random.nextDouble() * 6;
            world.spawnParticle(Particle.SNOWFLAKE,
                    centre.clone().add(offsetX, offsetY, offsetZ), 1, 0, 0, 0, 0.0);
        }
    }

    // ------------------------------------------------------------- fireworks

    /** A red/green/white/gold firework that detonates immediately. */
    public static void festiveFirework(Location at) {
        firework(at, randomFestiveEffect(), 0);
    }

    /** A firework with a flight time in rocket "power" units. */
    public static void firework(Location at, FireworkEffect effect, int power) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Firework firework = world.spawn(at, Firework.class, spawned -> {
            FireworkMeta meta = spawned.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(Math.max(0, power));
            spawned.setFireworkMeta(meta);
        });
        if (power <= 0) {
            // Power 0 still waits a tick before detonating; do it now so the
            // burst lands where the reward happened.
            firework.detonate();
        }
    }

    public static FireworkEffect randomFestiveEffect() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        FireworkEffect.Type[] types = {
                FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.STAR, FireworkEffect.Type.BURST
        };
        return FireworkEffect.builder()
                .with(types[random.nextInt(types.length)])
                .withColor(FESTIVE.get(random.nextInt(FESTIVE.size())),
                        FESTIVE.get(random.nextInt(FESTIVE.size())))
                .withFade(Color.WHITE)
                .flicker(random.nextBoolean())
                .trail(true)
                .build();
    }

    /** A timed display of several rockets, for the midnight countdown. */
    public static void fireworkShow(org.bukkit.plugin.Plugin plugin, Location at,
                                    int rockets, long periodTicks) {
        for (int index = 0; index < rockets; index++) {
            final int offset = index;
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Location spot = at.clone().add(
                        (random.nextDouble() - 0.5) * 12, 0, (random.nextDouble() - 0.5) * 12);
                firework(spot, randomFestiveEffect(), 1 + random.nextInt(2));
            }, offset * Math.max(1, periodTicks));
        }
    }

    // ----------------------------------------------------------- titles/sound

    public static void title(Player player, String title, String subtitle,
                             Map<String, String> placeholders) {
        Component main = title == null ? Component.empty() : Text.parse(title, placeholders);
        Component sub = subtitle == null ? Component.empty() : Text.parse(subtitle, placeholders);
        player.showTitle(Title.title(main, sub, Title.Times.times(
                Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }

    public static void sound(Location at, Sound sound, float volume, float pitch) {
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, sound, volume, pitch);
        }
    }

    /** A short sleigh-bell flourish, used for gift and present events. */
    public static void jingle(Location at) {
        sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.6f);
        sound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.9f);
    }
}
