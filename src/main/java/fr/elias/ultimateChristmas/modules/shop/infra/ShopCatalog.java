package fr.elias.ultimateChristmas.modules.shop.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.ItemCodec;
import fr.elias.ultimateChristmas.core.reward.Reward;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The store's categories and entries, parsed from the shop module's config.
 *
 * <p>An entry's payload is an ordinary {@link Reward}, which is why a shop item
 * can hand over items, run commands, grant shards or apply potion effects
 * without the shop knowing anything about those mechanisms. In 3.x the shop had
 * its own bespoke item-building and command-dispatch code, separate from the
 * gift and loot systems and subtly different from both.
 */
public final class ShopCatalog {

    /**
     * One purchasable entry.
     *
     * @param id            config key, also used for the purchase permission
     * @param category      which tab it belongs to
     * @param icon          what the menu shows
     * @param price         cost in shards, or 0 if priced in server currency
     * @param money         cost in server currency, or 0 if priced in shards
     * @param reward        what the buyer receives
     * @param permission    extra permission required, or {@code null}
     * @param stockPerDay   purchases allowed per player per day, 0 for unlimited
     * @param requiresNice  minimum naughty/nice standing, or {@link Integer#MIN_VALUE}
     */
    public record Entry(
            String id,
            String category,
            org.bukkit.inventory.ItemStack icon,
            long price,
            double money,
            Reward reward,
            String permission,
            int stockPerDay,
            int requiresNice
    ) {
        public boolean paidInShards() {
            return price > 0 || money <= 0;
        }
    }

    /** One tab in the store. */
    public record Category(String id, String name, Material icon, int slot,
                           List<Entry> entries) {
    }

    private final Logger logger;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public ShopCatalog(Logger logger) {
        this.logger = logger;
    }

    /**
     * Rebuilds the catalog.
     *
     * @param categoriesSection the {@code categories} section
     */
    public void load(ConfigurationSection categoriesSection) {
        categories.clear();
        for (String id : categoriesSection.getKeys(false)) {
            ConfigurationSection section = categoriesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Entry> entries = readEntries(id, section.getConfigurationSection("items"));
            categories.put(id, new Category(
                    id,
                    section.getString("name", "&f" + id),
                    ItemBuilder.material(section.getString("icon"), Material.CHEST),
                    section.getInt("slot", -1),
                    entries));
        }
        logger.info("Shop: " + categories.size() + " category/categories, "
                + entryCount() + " entries.");
    }

    private List<Entry> readEntries(String category, ConfigurationSection itemsSection) {
        List<Entry> entries = new ArrayList<>();
        if (itemsSection == null) {
            return entries;
        }
        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            // The icon doubles as the display; the reward is what is delivered.
            var icon = ItemCodec.read(section, Material.PAPER, logger);
            Reward reward = Reward.read(section, logger);
            if (reward.isEmpty()) {
                logger.warning("Shop entry '" + section.getCurrentPath()
                        + "' delivers nothing; add items, commands or shards to it.");
                continue;
            }
            long price = section.getLong("price", 0);
            double money = section.getDouble("money", 0);
            if (price <= 0 && money <= 0) {
                logger.warning("Shop entry '" + section.getCurrentPath()
                        + "' has no price; skipping it so it cannot be taken for free.");
                continue;
            }
            entries.add(new Entry(
                    id,
                    category,
                    icon,
                    price,
                    money,
                    reward,
                    section.getString("permission"),
                    section.getInt("stock-per-day", 0),
                    section.contains("requires-nice")
                            ? section.getInt("requires-nice")
                            : Integer.MIN_VALUE));
        }
        return entries;
    }

    public java.util.Collection<Category> categories() {
        return categories.values();
    }

    public java.util.Set<String> categoryIds() {
        return categories.keySet();
    }

    public Category category(String id) {
        return categories.get(id);
    }

    public int entryCount() {
        return categories.values().stream().mapToInt(category -> category.entries().size()).sum();
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }

    /** The single category when there is only one, else {@code null}. */
    public Category soleCategory() {
        return categories.size() == 1 ? categories.values().iterator().next() : null;
    }
}
