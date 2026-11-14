package fr.elias.ultimateChristmas.modules.cosmetics.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.ItemCodec;
import fr.elias.ultimateChristmas.core.item.Keys;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The cosmetics a server offers.
 *
 * <p>Two kinds, because they are the two that work reliably without extra
 * plugins: a hat worn in the helmet slot, and a particle trail that follows the
 * player. Both are purely visual and neither grants stats, so equipping one can
 * never affect gameplay balance.
 */
public final class CosmeticCatalog {

    /** What a cosmetic does. */
    public enum Kind {
        HAT,
        TRAIL;

        static Kind parse(String raw, Logger logger, String path) {
            if (raw == null) {
                return HAT;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                logger.warning("cosmetics.yml: unknown type '" + raw + "' at " + path
                        + "; using hat.");
                return HAT;
            }
        }

        /** Equipment slot key, so only one of each kind is worn at a time. */
        public String slot() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One cosmetic.
     *
     * @param id         config key
     * @param name       shown to players
     * @param kind       hat or trail
     * @param icon       menu icon, and the worn item for a hat
     * @param particle   the trail's particle, or {@code null} for a hat
     * @param permission extra permission required, or {@code null}
     * @param freeToAll  owned by everyone without being granted
     */
    public record Cosmetic(String id, String name, Kind kind, ItemStack icon,
                           Particle particle, String permission, boolean freeToAll) {
    }

    private final Logger logger;
    private final Map<String, Cosmetic> cosmetics = new LinkedHashMap<>();

    public CosmeticCatalog(Logger logger) {
        this.logger = logger;
    }

    public void load(ConfigurationSection section) {
        cosmetics.clear();
        for (String id : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(id);
            if (child == null) {
                continue;
            }
            Kind kind = Kind.parse(child.getString("type"), logger, id);

            Particle particle = null;
            if (kind == Kind.TRAIL) {
                String name = child.getString("particle", "SNOWFLAKE");
                try {
                    particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    logger.warning("cosmetics.yml: unknown particle '" + name + "' at "
                            + id + "; using SNOWFLAKE.");
                    particle = Particle.SNOWFLAKE;
                }
            }

            ItemStack icon = ItemBuilder
                    .wrap(ItemCodec.read(child, defaultIcon(kind), logger))
                    .tag(Keys.cosmeticId(), id)
                    .build();

            cosmetics.put(id, new Cosmetic(
                    id,
                    child.getString("name", id),
                    kind,
                    icon,
                    particle,
                    child.getString("permission"),
                    child.getBoolean("free", false)));
        }
        logger.info("Cosmetics: " + cosmetics.size() + " item(s).");
    }

    private static Material defaultIcon(Kind kind) {
        return kind == Kind.HAT ? Material.LEATHER_HELMET : Material.FIREWORK_STAR;
    }

    public Cosmetic get(String id) {
        return id == null ? null : cosmetics.get(id);
    }

    public java.util.Collection<Cosmetic> all() {
        return cosmetics.values();
    }

    public java.util.Set<String> ids() {
        return cosmetics.keySet();
    }

    public List<Cosmetic> ofKind(Kind kind) {
        List<Cosmetic> matches = new ArrayList<>();
        for (Cosmetic cosmetic : cosmetics.values()) {
            if (cosmetic.kind() == kind) {
                matches.add(cosmetic);
            }
        }
        return matches;
    }

    public boolean isEmpty() {
        return cosmetics.isEmpty();
    }
}
