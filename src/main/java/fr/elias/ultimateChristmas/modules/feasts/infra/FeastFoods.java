package fr.elias.ultimateChristmas.modules.feasts.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.ItemCodec;
import fr.elias.ultimateChristmas.core.item.Keys;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The festive food a server has configured.
 *
 * <p>Each food is a tagged edible item that applies potion effects and restores
 * hunger when eaten. The item is consumed on use like any food, so a stack of
 * hot cocoa behaves exactly as a player expects — the plugin only adds the
 * buffs on top.
 */
public final class FeastFoods {

    /**
     * One festive food.
     *
     * @param id          config key, also the persistent tag value
     * @param display     the edible item
     * @param effects     buffs applied on eating
     * @param restoreFood hunger points restored, on top of the base item's
     * @param healHearts  health restored in half-hearts
     * @param cooldown    seconds before this food can be eaten again, 0 for none
     * @param message     a line shown on eating, or {@code null}
     */
    public record Food(String id, ItemStack display, List<PotionEffect> effects,
                       int restoreFood, double healHearts, int cooldown, String message) {
    }

    private final Logger logger;
    private final Map<String, Food> foods = new LinkedHashMap<>();

    public FeastFoods(Logger logger) {
        this.logger = logger;
    }

    public void load(ConfigurationSection section) {
        foods.clear();
        for (String id : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(id);
            if (child == null) {
                continue;
            }
            // Default to a cookie so an unconfigured material is still edible.
            ItemStack display = ItemBuilder
                    .wrap(ItemCodec.read(child, Material.COOKIE, logger))
                    .tag(Keys.feastId(), id)
                    .build();
            if (!display.getType().isEdible()) {
                logger.warning("feasts.yml: '" + id + "' uses " + display.getType()
                        + ", which cannot be eaten; players will not be able to consume it.");
            }

            foods.put(id, new Food(
                    id,
                    display,
                    readEffects(child.getStringList("effects"), id),
                    child.getInt("restore-food", 0),
                    child.getDouble("heal-hearts", 0),
                    Math.max(0, child.getInt("cooldown", 0)),
                    child.getString("message")));
        }
        logger.info("Feasts: " + foods.size() + " food(s).");
    }

    private List<PotionEffect> readEffects(List<String> raw, String id) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":");
            PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                    org.bukkit.NamespacedKey.minecraft(
                            parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                logger.warning("feasts.yml: unknown effect '" + parts[0] + "' on food '"
                        + id + "'.");
                continue;
            }
            int duration = 600;
            int amplifier = 0;
            try {
                if (parts.length > 1) {
                    duration = Integer.parseInt(parts[1].trim());
                }
                if (parts.length > 2) {
                    amplifier = Integer.parseInt(parts[2].trim());
                }
            } catch (NumberFormatException ex) {
                logger.warning("feasts.yml: unreadable numbers in effect '" + entry
                        + "' on food '" + id + "'.");
            }
            effects.add(new PotionEffect(type, duration, amplifier, true, true, true));
        }
        return effects;
    }

    public Food get(String id) {
        return id == null ? null : foods.get(id);
    }

    public java.util.Set<String> ids() {
        return foods.keySet();
    }

    public ItemStack create(String id, int amount) {
        Food food = get(id);
        if (food == null) {
            return null;
        }
        ItemStack copy = food.display().clone();
        copy.setAmount(Math.max(1, Math.min(copy.getMaxStackSize(), amount)));
        return copy;
    }

    /** Reads the feast tag off an item, if it has one. */
    public static String foodOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Keys.getString(item.getItemMeta().getPersistentDataContainer(), Keys.feastId());
    }
}
