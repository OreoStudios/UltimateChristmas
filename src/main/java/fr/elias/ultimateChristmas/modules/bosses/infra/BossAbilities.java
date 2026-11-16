package fr.elias.ultimateChristmas.modules.bosses.infra;

import fr.elias.ultimateChristmas.core.effect.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The boss abilities, kept deliberately simple and safe.
 *
 * <p>Every ability only affects players within a bounded radius and never
 * modifies the world, so an ability can misfire at worst by doing nothing. Each
 * is a static method taking the boss entity, so abilities hold no state and the
 * fight loop just picks one by name.
 */
public final class BossAbilities {

    private static final double RANGE = 14.0;

    private BossAbilities() {
    }

    /** Keeps legacy ability names on the same animation mapping as modern names. */
    public static String canonicalName(String ability) {
        return switch (ability.toLowerCase(java.util.Locale.ROOT)) {
            case "roar_knockback" -> "roar";
            case "snowstorm_slow" -> "frost";
            case "shadow_dash" -> "leap";
            case "summon_minions" -> "summon";
            default -> ability.toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Runs the named ability, ignoring an unknown name. */
    public static void run(String ability, Mob boss, int summonCount,
                           java.util.function.Consumer<org.bukkit.entity.Zombie> configureMinion) {
        switch (canonicalName(ability)) {
            case "roar" -> roar(boss);
            case "frost" -> frost(boss);
            case "leap" -> leap(boss);
            case "summon" -> summon(boss, summonCount, configureMinion);
            default -> {
                // Unknown ability name: do nothing rather than fail the loop.
            }
        }
    }

    /** Knocks nearby players back and deals a little damage. */
    private static void roar(Mob boss) {
        Location centre = boss.getLocation();
        Fx.ring(centre.clone().add(0, 1, 0), Particle.SWEEP_ATTACK, 3.0, 20);
        Fx.sound(centre, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
        for (Player player : nearbyPlayers(boss)) {
            Vector push = player.getLocation().toVector()
                    .subtract(centre.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 1, 0);
            }
            player.setVelocity(player.getVelocity()
                    .add(push.normalize().multiply(1.2).setY(0.5)));
            player.damage(4.0, boss);
        }
    }

    /** Chills nearby players with slowness and mining fatigue. */
    private static void frost(Mob boss) {
        Fx.snowfall(boss.getLocation().add(0, 3, 0), RANGE, 40);
        Fx.sound(boss.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.7f);
        for (Player player : nearbyPlayers(boss)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 120, 0));
            Fx.burst(player.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 15, 0.4);
        }
    }

    /** Leaps toward a random nearby player. */
    private static void leap(Mob boss) {
        List<Player> players = nearbyPlayers(boss);
        if (players.isEmpty()) {
            return;
        }
        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        Vector toTarget = target.getLocation().toVector()
                .subtract(boss.getLocation().toVector());
        if (toTarget.lengthSquared() < 0.01) {
            return;
        }
        boss.setVelocity(toTarget.normalize().multiply(1.4).setY(0.5));
        Fx.sound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        Fx.burst(boss.getLocation(), Particle.LARGE_SMOKE, 20, 0.5);
    }

    /** Summons a couple of minions to back the boss up. */
    private static void summon(Mob boss, int requested,
                               java.util.function.Consumer<org.bukkit.entity.Zombie> configureMinion) {
        if (boss.getWorld() == null) {
            return;
        }
        // Cap total nearby minions so repeated casts cannot flood the area.
        long existing = boss.getWorld().getNearbyEntities(boss.getLocation(), RANGE, RANGE, RANGE)
                .stream().filter(BossAbilities::isMinion).count();
        if (existing >= 8) {
            return;
        }
        // Stamp minions with the owning boss's id, so the module can give them
        // that boss's configured minion model.
        String bossId = fr.elias.ultimateChristmas.core.item.Keys.getString(
                boss.getPersistentDataContainer(),
                fr.elias.ultimateChristmas.core.item.Keys.bossId());
        var plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("UltimateChristmas");

        int count = Math.min(Math.max(1, requested), (int) (8 - existing));
        for (int index = 0; index < count; index++) {
            Location spot = boss.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-3, 3), 0,
                    ThreadLocalRandom.current().nextDouble(-3, 3));
            var spawned = boss.getWorld().spawn(spot, org.bukkit.entity.Zombie.class, minion -> {
                minion.setMetadata("uc_boss_minion",
                        new org.bukkit.metadata.FixedMetadataValue(plugin,
                                bossId == null ? "" : bossId));
                minion.setAdult();
                minion.setShouldBurnInDay(false);
                minion.setCanPickupItems(false);
            });
            // The entity now exists, so its display rig can be attached safely.
            configureMinion.accept(spawned);
        }
        Fx.ring(boss.getLocation(), Particle.WITCH, 2.0, 16);
    }

    private static boolean isMinion(Entity entity) {
        return entity.hasMetadata("uc_boss_minion");
    }

    private static List<Player> nearbyPlayers(Mob boss) {
        List<Player> players = new java.util.ArrayList<>();
        for (Entity entity : boss.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (entity instanceof Player player && !isInvulnerable(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private static boolean isInvulnerable(LivingEntity entity) {
        return entity instanceof Player player
                && (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR);
    }
}
