package fr.elias.ultimateChristmas.modules.snowball.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.ItemCodec;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.reward.Reward;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The trick snowballs a server has configured.
 *
 * <p>A kind is a tagged snowball item plus what happens on impact. Because the
 * on-hit payload is an ordinary {@link Reward}, a snowball can grant shards,
 * run commands or hand over items using the same config shape as every other
 * prize in the plugin.
 */
public final class SnowballKinds {

    /**
     * One trick snowball.
     *
     * @param id            config key, also the persistent tag value
     * @param display       the item players carry and throw
     * @param victimEffects potion effects applied to whoever is hit
     * @param throwerReward granted to the thrower on a hit, or {@code null}
     * @param knockback     extra knockback applied to the victim
     * @param naughtyPoints standing change for the thrower on a player hit
     */
    public record Kind(
            String id,
            ItemStack display,
            List<PotionEffect> victimEffects,
            Reward throwerReward,
            double knockback,
            int naughtyPoints
    ) {
    }

    private final Logger logger;
    private final Map<String, Kind> kinds = new LinkedHashMap<>();

    public SnowballKinds(Logger logger) {
        this.logger = logger;
    }

    public void load(ConfigurationSection section) {
        kinds.clear();
        for (String id : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(id);
            if (child == null) {
                continue;
            }
            ItemStack display = ItemBuilder
                    .wrap(ItemCodec.read(child, Material.SNOWBALL, logger))
                    .tag(Keys.snowballKind(), id)
                    .build();

            // The reward lives under 'on-hit' so it cannot be confused with the
            // item's own display keys sitting in the same section.
            ConfigurationSection onHit = child.getConfigurationSection("on-hit");
            Reward reward = onHit == null ? null : Reward.read(onHit, logger);
            if (reward != null && reward.isEmpty()) {
                reward = null;
            }

            List<PotionEffect> effects = new ArrayList<>();
            if (onHit != null) {
                Reward effectHolder = Reward.read(onHit, logger);
                effects.addAll(effectHolder.effects());
            }

            kinds.put(id, new Kind(
                    id,
                    display,
                    effects,
                    reward,
                    child.getDouble("knockback", 0),
                    child.getInt("naughty-points", 0)));
        }
        logger.info("Snowballs: " + kinds.size() + " trick kind(s).");
    }

    public Kind get(String id) {
        return id == null ? null : kinds.get(id);
    }

    public java.util.Set<String> ids() {
        return kinds.keySet();
    }

    /** Reads the kind tag off a thrown snowball's item, if it has one. */
    public static String kindOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Keys.getString(item.getItemMeta().getPersistentDataContainer(),
                Keys.snowballKind());
    }

    public ItemStack create(String id, int amount) {
        Kind kind = get(id);
        if (kind == null) {
            return null;
        }
        ItemStack copy = kind.display().clone();
        copy.setAmount(Math.max(1, Math.min(copy.getMaxStackSize(), amount)));
        return copy;
    }
}
