package fr.elias.ultimateChristmas.modules.economy.infra;

import fr.elias.ultimateChristmas.core.reward.Amount;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Where shards come from, parsed from the economy module's config.
 *
 * <p>Parsed once on load into maps keyed by material and entity type rather
 * than being read out of the config on every block break: the 3.x listener did
 * a config section lookup plus string comparison for every single block any
 * player broke.
 */
public final class ShardSources {

    /** One earning rule: how much, and how often it pays out. */
    public record Source(Amount amount, double chance) {

        /** Rolls this source, returning 0 when the chance check fails. */
        public long roll() {
            if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) {
                return 0;
            }
            return amount.roll();
        }
    }

    private final Logger logger;

    private final Map<Material, Source> blocks = new HashMap<>();
    private final Map<EntityType, Source> mobs = new HashMap<>();
    private final Map<String, Source> customBlocks = new HashMap<>();
    private final Map<String, Source> mythicMobs = new HashMap<>();

    public ShardSources(Logger logger) {
        this.logger = logger;
    }

    /**
     * Replaces the rules from config sections.
     *
     * @param earning the {@code earning} section of modules/economy.yml
     */
    public void load(ConfigurationSection earning) {
        blocks.clear();
        mobs.clear();
        customBlocks.clear();
        mythicMobs.clear();

        readBlocks(earning.getConfigurationSection("blocks"));
        readMobs(earning.getConfigurationSection("mobs"));
        readNamed(earning.getConfigurationSection("itemsadder-blocks"), customBlocks);
        readNamed(earning.getConfigurationSection("mythicmobs"), mythicMobs);

        logger.info("Shard sources: " + blocks.size() + " block(s), " + mobs.size()
                + " mob(s), " + customBlocks.size() + " custom block(s), "
                + mythicMobs.size() + " mythic mob(s).");
    }

    private void readBlocks(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null) {
                logger.warning("economy.yml: unknown block '" + key + "'.");
                continue;
            }
            Source source = readSource(section, key);
            if (source != null) {
                blocks.put(material, source);
            }
        }
    }

    private void readMobs(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Source source = readSource(section, key);
            if (source == null) {
                continue;
            }
            try {
                mobs.put(EntityType.valueOf(key.toUpperCase(Locale.ROOT)), source);
            } catch (IllegalArgumentException ex) {
                logger.warning("economy.yml: unknown mob '" + key + "'.");
            }
        }
    }

    private void readNamed(ConfigurationSection section, Map<String, Source> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Source source = readSource(section, key);
            if (source != null) {
                target.put(key.toLowerCase(Locale.ROOT), source);
            }
        }
    }

    /**
     * Reads one rule.
     *
     * <p>{@code chance} is accepted as a fraction (0.05) or a percentage (5),
     * because both appeared in 3.x configs: anything above 1 is read as a
     * percentage.
     */
    private Source readSource(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            logger.warning("economy.yml: '" + parent.getCurrentPath() + "." + key
                    + "' should be a section with 'shards' and 'chance'.");
            return null;
        }
        Amount amount = Amount.parse(section.get("shards"));
        if (amount.isZero()) {
            return null;
        }
        double chance = section.getDouble("chance", 1.0);
        if (chance > 1.0) {
            chance = chance / 100.0;
        }
        return new Source(amount, Math.max(0, Math.min(1.0, chance)));
    }

    // ---------------------------------------------------------------- lookups

    public Source forBlock(Material material) {
        return blocks.get(material);
    }

    public Source forMob(EntityType type) {
        return mobs.get(type);
    }

    public Source forCustomBlock(String namespacedId) {
        return namespacedId == null ? null : customBlocks.get(namespacedId.toLowerCase(Locale.ROOT));
    }

    public Source forMythicMob(String internalName) {
        return internalName == null ? null : mythicMobs.get(internalName.toLowerCase(Locale.ROOT));
    }

    public boolean hasCustomBlocks() {
        return !customBlocks.isEmpty();
    }

    public boolean hasMythicMobs() {
        return !mythicMobs.isEmpty();
    }

    public boolean paysForBlock(Material material) {
        return blocks.containsKey(material);
    }

    public int total() {
        return blocks.size() + mobs.size() + customBlocks.size() + mythicMobs.size();
    }
}
