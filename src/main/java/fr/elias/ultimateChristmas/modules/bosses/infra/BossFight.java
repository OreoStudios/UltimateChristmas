package fr.elias.ultimateChristmas.modules.bosses.infra;

import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One live boss encounter: the entity, its health bar, and who has hurt it.
 *
 * <p>Damage is tracked per player so loot can go to the real contributors
 * rather than only the last hit. The health bar is a real boss bar shown to
 * everyone fighting, added and removed as players come into and out of range.
 *
 * <p>While a fight is live the boss's chunk is kept loaded with a plugin chunk
 * ticket (moved with the boss), so the entity never unloads mid-fight. Without
 * that, wandering out of a player's render distance silently dropped the fight —
 * the bar, the abilities and the on-death loot all vanished.
 */
public final class BossFight {

    private final ChristmasPlatform platform;
    private final BossType type;
    private final Mob entity;
    private final BossBar bar;
    private final Map<UUID, Double> damage = new LinkedHashMap<>();

    /** The real maximum health of the spawned entity, which Minecraft caps. */
    private final double maxHealth;

    /** The chunk currently held loaded for this fight, or {@code null}. */
    private Chunk ticketedChunk;

    public BossFight(ChristmasPlatform platform, BossType type, Mob entity) {
        this.platform = platform;
        this.type = type;
        this.entity = entity;
        // Read the actual max health back from the entity: Minecraft clamps the
        // MAX_HEALTH attribute (to 1024 by default), so a boss configured with
        // more would otherwise show a bar that starts part-empty.
        AttributeInstance maxAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        this.maxHealth = maxAttribute != null ? maxAttribute.getValue() : type.health();
        this.bar = BossBar.bossBar(Text.parse(type.displayName()), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        keepChunkLoaded();
    }

    /** Applies the configured stats and tags the entity as this boss. */
    public static Mob spawn(ChristmasPlatform platform, BossType type, Location where) {
        Mob mob = (Mob) where.getWorld().spawnEntity(where, type.entityType());

        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(type.health());
            // setBaseValue clamps to the attribute's own ceiling rather than
            // throwing, so read the applied value back and warn if it was cut.
            double applied = maxHealth.getValue();
            if (applied + 0.5 < type.health()) {
                platform.plugin().getLogger().warning("Boss '" + type.id() + "' was configured "
                        + "with " + (long) type.health() + " health, but Minecraft caps a "
                        + mob.getType() + " at " + (long) applied + ". Using " + (long) applied
                        + ". (Use ModelEngine/BetterModel scaling or a custom mob for more.)");
            }
            mob.setHealth(applied);
        }
        if (mob.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(type.speed());
        }
        if (mob.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(type.attackDamage());
        }
        mob.setGlowing(type.glowing());
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        mob.customName(Text.parse(type.displayName()));
        mob.setCustomNameVisible(true);
        mob.getPersistentDataContainer()
                .set(Keys.bossId(), PersistentDataType.STRING, type.id());
        return mob;
    }

    /** Whether an entity is a festive boss. */
    public static boolean isBoss(org.bukkit.entity.Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(Keys.bossId());
    }

    public static String bossIdOf(org.bukkit.entity.Entity entity) {
        return entity == null ? null
                : Keys.getString(entity.getPersistentDataContainer(), Keys.bossId());
    }

    public BossType type() {
        return type;
    }

    public Mob entity() {
        return entity;
    }

    public boolean alive() {
        return !entity.isDead() && entity.isValid() && entity.getHealth() > 0;
    }

    public double maxHealth() {
        return maxHealth;
    }

    /** Records damage and refreshes the health bar. */
    public void recordDamage(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        damage.merge(player.getUniqueId(), amount, Double::sum);
    }

    /** The fraction of health remaining, 0 to 1. */
    public double healthFraction() {
        return Math.max(0, Math.min(1.0, entity.getHealth() / maxHealth));
    }

    /** Shows the bar to everyone near the boss and updates its progress. */
    public void updateBar() {
        keepChunkLoaded();
        bar.progress((float) healthFraction());

        var shouldSee = entity.getWorld().getNearbyEntities(
                entity.getLocation(), 48, 48, 48);
        java.util.Set<Player> near = new java.util.HashSet<>();
        for (var nearby : shouldSee) {
            if (nearby instanceof Player player) {
                near.add(player);
                player.showBossBar(bar);
            }
        }
        // Hide from anyone who wandered off, so the bar does not linger.
        for (Player online : platform.plugin().getServer().getOnlinePlayers()) {
            if (!near.contains(online)) {
                online.hideBossBar(bar);
            }
        }
    }

    public void hideBar() {
        for (Player online : platform.plugin().getServer().getOnlinePlayers()) {
            online.hideBossBar(bar);
        }
    }

    /** Keeps the boss's current chunk loaded, releasing the previous one. */
    private void keepChunkLoaded() {
        if (!entity.isValid()) {
            return;
        }
        Chunk current = entity.getLocation().getChunk();
        if (current.equals(ticketedChunk)) {
            return;
        }
        releaseChunk();
        current.addPluginChunkTicket(platform.plugin());
        ticketedChunk = current;
    }

    /** Releases the chunk ticket. Called on death and despawn. */
    public void releaseChunk() {
        if (ticketedChunk != null) {
            ticketedChunk.removePluginChunkTicket(platform.plugin());
            ticketedChunk = null;
        }
    }

    /** The top damage dealers, highest first, capped at {@code limit}. */
    public List<Map.Entry<UUID, Double>> topContributors(int limit) {
        return damage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    public boolean hasContributors() {
        return !damage.isEmpty();
    }

    /** Total damage dealt to the boss so far, across all players. */
    public double totalDamage() {
        return damage.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public int contributorCount() {
        return damage.size();
    }

    public Location location() {
        return entity.getLocation();
    }
}
