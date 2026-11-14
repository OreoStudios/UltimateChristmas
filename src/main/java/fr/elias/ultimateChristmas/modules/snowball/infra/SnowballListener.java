package fr.elias.ultimateChristmas.modules.snowball.infra;

import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.snowball.api.SnowballHitEvent;
import fr.elias.ultimateChristmas.modules.snowball.lifecycle.SnowballParameters;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Snowball fights: trick effects, rewards and standing changes.
 *
 * <p>The thrown projectile carries the trick-snowball id, copied from the item
 * at launch. Reading it off the entity at impact is what makes the effect
 * reliable — checking the thrower's held item on hit, as 3.x did for its
 * villager rewards, breaks the moment a player swaps hotbar slots mid-flight.
 */
public final class SnowballListener implements Listener {

    private final ChristmasPlatform platform;
    private final SnowballKinds kinds;
    private final Supplier<SnowballParameters> parameters;
    private final Supplier<RewardTable> hitRewards;
    private final Consumer<Object> emit;

    public SnowballListener(ChristmasPlatform platform, SnowballKinds kinds,
                            Supplier<SnowballParameters> parameters,
                            Supplier<RewardTable> hitRewards, Consumer<Object> emit) {
        this.platform = platform;
        this.kinds = kinds;
        this.parameters = parameters;
        this.hitRewards = hitRewards;
        this.emit = emit;
    }

    /** Stamps the projectile with the kind of snowball it came from. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !(snowball.getShooter() instanceof Player thrower)) {
            return;
        }
        ItemStack source = snowball.getItem();
        String kind = SnowballKinds.kindOf(source);
        if (kind == null) {
            // Fall back to the held item, for servers whose plugins replace the
            // projectile's item stack.
            kind = SnowballKinds.kindOf(thrower.getInventory().getItemInMainHand());
        }
        if (kind != null && kinds.get(kind) != null) {
            snowball.getPersistentDataContainer()
                    .set(Keys.snowballKind(), PersistentDataType.STRING, kind);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !(snowball.getShooter() instanceof Player thrower)) {
            return;
        }
        SnowballParameters settings = parameters.get();
        Entity struck = event.getHitEntity();

        String kindId = Keys.getString(snowball.getPersistentDataContainer(),
                Keys.snowballKind());
        SnowballKinds.Kind kind = kinds.get(kindId);

        if (kind != null) {
            applyTrick(thrower, struck, kind);
        }

        if (!(struck instanceof Player victim)) {
            if (struck instanceof LivingEntity mob && settings.rewardMobHits()) {
                rewardHit(thrower, null, kindId, settings, mob.getLocation());
            }
            return;
        }
        if (victim.equals(thrower)) {
            return;
        }
        rewardHit(thrower, victim, kindId, settings, victim.getLocation());
    }

    /** Applies a trick snowball's effects to whoever was hit. */
    private void applyTrick(Player thrower, Entity struck, SnowballKinds.Kind kind) {
        if (struck instanceof LivingEntity living) {
            for (PotionEffect effect : kind.victimEffects()) {
                living.addPotionEffect(effect);
            }
            if (kind.knockback() > 0) {
                Vector push = living.getLocation().toVector()
                        .subtract(thrower.getLocation().toVector());
                if (push.lengthSquared() > 0.001) {
                    living.setVelocity(living.getVelocity()
                            .add(push.normalize().multiply(kind.knockback()).setY(0.35)));
                }
            }
            Fx.burst(living.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 20, 0.4);
            Fx.sound(living.getLocation(), Sound.BLOCK_SNOW_BREAK, 1.0f, 1.4f);
        }

        if (kind.throwerReward() != null) {
            platform.rewards().grant(thrower, kind.throwerReward(),
                    Messages.of("kind", kind.id()));
        }
        if (kind.naughtyPoints() != 0 && struck instanceof Player) {
            platform.profiles().get(thrower).addNaughtyNice(kind.naughtyPoints());
        }
    }

    /**
     * Rewards a hit, respecting the per-player cooldown.
     *
     * <p>The cooldown lives on the profile, so it survives a reconnect and
     * cannot be reset by logging out — which the 3.x in-memory map allowed.
     */
    private void rewardHit(Player thrower, Player victim, String kindId,
                           SnowballParameters settings, org.bukkit.Location where) {
        platform.progress().record(thrower, PlayerProfile.STAT_SNOWBALLS_HIT, 1);
        emit.accept(new SnowballHitEvent(thrower.getUniqueId(),
                victim == null ? null : victim.getUniqueId(),
                kindId == null ? "plain" : kindId));

        if (!thrower.hasPermission("ultimatechristmas.snowball.reward")) {
            return;
        }
        PlayerProfile profile = platform.profiles().get(thrower);
        if (profile.onCooldown("snowball-reward")) {
            return;
        }
        RewardTable table = hitRewards.get();
        if (table.isEmpty()) {
            return;
        }
        profile.setCooldown("snowball-reward", settings.rewardCooldownSeconds() * 1000L);
        platform.rewards().rollAndGrant(thrower, table, Messages.of(
                "victim", victim == null ? "a mob" : victim.getName()));
        Fx.jingle(where);
    }
}
