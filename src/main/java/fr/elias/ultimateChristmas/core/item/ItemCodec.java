package fr.elias.ultimateChristmas.core.item;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads an {@link ItemStack} out of a configuration section.
 *
 * <p>One parser for every item in every config. The 3.x code had this logic
 * copied into the shop GUI, the gift factory, the present factory and the loot
 * chest builder, each supporting a slightly different subset of keys; moving it
 * here means a new option shows up everywhere at once.
 *
 * <pre>
 * material: DIAMOND_SWORD      # or 'icon', for compatibility with 3.x shop.yml
 * amount: 1
 * name: "&amp;bFrostbite"
 * lore: ["&amp;7Chilly."]
 * model-data: 101
 * glow: true
 * unbreakable: false
 * hide-attributes: true
 * head: "MHF_Snowman"          # name, texture hash, URL or base64
 * colour: "#c42b2b"            # leather armour only
 * uses: 42                     # custom limited-use counter
 * enchants: { sharpness: 3 }
 * attributes: { attack_damage: 10, attack_speed: -2 }
 * </pre>
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static ItemStack read(ConfigurationSection section, Material fallback, Logger logger) {
        return read(section, fallback, Map.of(), logger);
    }

    /**
     * Builds the item described by {@code section}.
     *
     * @param fallback     material used when none is configured or the name is unknown
     * @param placeholders substituted into the name and lore
     */
    public static ItemStack read(ConfigurationSection section, Material fallback,
                                 Map<String, String> placeholders, Logger logger) {
        if (section == null) {
            return new ItemStack(fallback);
        }

        // 'icon' is the 3.x spelling; 'material' is the documented one.
        String materialName = firstNonBlank(section.getString("material"), section.getString("icon"));
        Material material = ItemBuilder.material(materialName, fallback);
        if (materialName != null && !materialName.isBlank()
                && Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT)) == null
                && logger != null) {
            logger.warning("Unknown material '" + materialName + "' at "
                    + section.getCurrentPath() + "; using " + fallback + ".");
        }

        ItemBuilder builder = ItemBuilder.of(material, section.getInt("amount", 1))
                .name(firstNonBlank(section.getString("name"), section.getString("display_name")),
                        placeholders)
                .lore(section.getStringList("lore").isEmpty()
                        ? section.getStringList("lines") : section.getStringList("lore"),
                        placeholders)
                .modelData(section.getInt("model-data", section.getInt("custom_model_data", 0)))
                .glint(section.getBoolean("glow", section.getBoolean("glint", false)))
                .unbreakable(section.getBoolean("unbreakable", false));

        String head = firstNonBlank(section.getString("head"), section.getString("head_texture"),
                section.getString("skull"));
        if (head != null) {
            builder.skull(head);
        }

        String colour = firstNonBlank(section.getString("colour"), section.getString("color"));
        if (colour != null) {
            Color parsed = parseColour(colour);
            if (parsed != null) {
                builder.colour(parsed);
            } else if (logger != null) {
                logger.warning("Unreadable colour '" + colour + "' at " + section.getCurrentPath());
            }
        }

        readEnchants(section, builder, logger);
        readAttributes(section, builder, logger);

        int uses = section.getInt("uses", section.getInt("max_durability", 0));
        if (uses > 0) {
            builder.uses(uses);
        }

        if (section.getBoolean("hide-attributes", true)) {
            builder.flags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        }
        return builder.build();
    }

    private static void readEnchants(ConfigurationSection section, ItemBuilder builder,
                                     Logger logger) {
        ConfigurationSection enchants = section.getConfigurationSection("enchants");
        if (enchants == null) {
            return;
        }
        for (String key : enchants.getKeys(false)) {
            Enchantment enchantment = resolveEnchantment(key);
            if (enchantment == null) {
                if (logger != null) {
                    logger.warning("Unknown enchantment '" + key + "' at "
                            + enchants.getCurrentPath());
                }
                continue;
            }
            builder.enchant(enchantment, Math.max(1, enchants.getInt(key, 1)));
        }
    }

    private static void readAttributes(ConfigurationSection section, ItemBuilder builder,
                                       Logger logger) {
        // Flat 3.x keys stay supported so existing shop.yml entries keep working.
        double damage = section.getDouble("attackdamage", 0);
        double speed = section.getDouble("attackspeed", 0);
        if (damage != 0) {
            builder.attribute(Attribute.ATTACK_DAMAGE, damage);
        }
        if (speed != 0) {
            builder.attribute(Attribute.ATTACK_SPEED, speed);
        }

        ConfigurationSection attributes = section.getConfigurationSection("attributes");
        if (attributes == null) {
            return;
        }
        for (String key : attributes.getKeys(false)) {
            Attribute attribute = resolveAttribute(key);
            if (attribute == null) {
                if (logger != null) {
                    logger.warning("Unknown attribute '" + key + "' at "
                            + attributes.getCurrentPath());
                }
                continue;
            }
            builder.attribute(attribute, attributes.getDouble(key, 0));
        }
    }

    @SuppressWarnings("deprecation")
    private static Enchantment resolveEnchantment(String key) {
        String normalised = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Enchantment byKey = org.bukkit.Registry.ENCHANTMENT.get(
                org.bukkit.NamespacedKey.minecraft(normalised));
        return byKey != null ? byKey : Enchantment.getByName(normalised.toUpperCase(Locale.ROOT));
    }

    private static Attribute resolveAttribute(String key) {
        String normalised = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        // Registry lookup first, so both 'attack_damage' and the older
        // 'generic.attack_damage' spelling resolve.
        Attribute byKey = org.bukkit.Registry.ATTRIBUTE.get(
                org.bukkit.NamespacedKey.minecraft(normalised.replace("generic.", "")));
        if (byKey != null) {
            return byKey;
        }
        try {
            return Attribute.valueOf(normalised.toUpperCase(Locale.ROOT).replace("GENERIC.", ""));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Parses {@code #rrggbb}, {@code r,g,b} or a named Bukkit colour. */
    public static Color parseColour(String raw) {
        String value = raw.trim();
        try {
            if (value.startsWith("#")) {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            }
            if (value.contains(",")) {
                String[] parts = value.split(",");
                if (parts.length == 3) {
                    return Color.fromRGB(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()));
                }
                return null;
            }
            return switch (value.toUpperCase(Locale.ROOT)) {
                case "RED" -> Color.RED;
                case "GREEN" -> Color.GREEN;
                case "BLUE" -> Color.BLUE;
                case "WHITE" -> Color.WHITE;
                case "BLACK" -> Color.BLACK;
                case "YELLOW" -> Color.YELLOW;
                case "ORANGE" -> Color.ORANGE;
                case "PURPLE" -> Color.PURPLE;
                case "AQUA" -> Color.AQUA;
                case "SILVER" -> Color.SILVER;
                case "GRAY", "GREY" -> Color.GRAY;
                case "MAROON" -> Color.MAROON;
                case "OLIVE" -> Color.OLIVE;
                case "NAVY" -> Color.NAVY;
                case "TEAL" -> Color.TEAL;
                case "FUCHSIA" -> Color.FUCHSIA;
                case "LIME" -> Color.LIME;
                default -> null;
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
