package fr.elias.ultimateChristmas.core.item;

import fr.elias.ultimateChristmas.core.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent builder for the plugin's items.
 *
 * <p>Centralises the fiddly parts the 3.x code repeated in five places: turning
 * configured strings into non-italic components, hiding attribute tooltips,
 * attaching a glint without a real enchantment, and writing persistent data.
 */
public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
        this.meta = stack.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(new ItemStack(material, Math.max(1, amount)));
    }

    /** Wraps an existing stack so it can be edited with the same API. */
    public static ItemBuilder wrap(ItemStack existing) {
        return new ItemBuilder(existing.clone());
    }

    /**
     * Resolves a material name from config, falling back with a clear log line
     * rather than throwing and taking the whole menu down.
     */
    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material resolved = Material.matchMaterial(name.trim().toUpperCase(java.util.Locale.ROOT));
        return resolved == null ? fallback : resolved;
    }

    // ------------------------------------------------------------------ text

    public ItemBuilder name(String raw) {
        return name(raw, Map.of());
    }

    public ItemBuilder name(String raw, Map<String, String> placeholders) {
        if (raw != null && !raw.isEmpty()) {
            meta.displayName(Text.parseItem(raw, placeholders));
        }
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(component);
        return this;
    }

    public ItemBuilder lore(List<String> raw) {
        return lore(raw, Map.of());
    }

    public ItemBuilder lore(List<String> raw, Map<String, String> placeholders) {
        if (raw != null && !raw.isEmpty()) {
            meta.lore(Text.parseLore(raw, placeholders));
        }
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        meta.lore(new ArrayList<>(lines));
        return this;
    }

    /** Appends a line to whatever lore is already set. */
    public ItemBuilder addLore(String raw, Map<String, String> placeholders) {
        List<Component> lines = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lines.add(Text.parseItem(raw, placeholders));
        meta.lore(lines);
        return this;
    }

    public ItemBuilder addLore(String raw) {
        return addLore(raw, Map.of());
    }

    public ItemBuilder addBlankLore() {
        return addLore("");
    }

    // ---------------------------------------------------------------- visuals

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), amount)));
        return this;
    }

    public ItemBuilder modelData(int data) {
        if (data > 0) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    /** Adds a harmless enchantment plus the hide flag, for a glint only. */
    public ItemBuilder glint(boolean on) {
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder hideAll() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return this;
    }

    public ItemBuilder unbreakable(boolean value) {
        meta.setUnbreakable(value);
        return this;
    }

    /** Dyes leather armour, for the Santa-hat style cosmetics. */
    public ItemBuilder colour(Color colour) {
        if (meta instanceof LeatherArmorMeta leather && colour != null) {
            leather.setColor(colour);
        }
        return this;
    }

    public ItemBuilder potion(PotionEffect effect) {
        if (meta instanceof PotionMeta potion && effect != null) {
            potion.addCustomEffect(effect, true);
        }
        return this;
    }

    /** Applies a head texture, by player name or by base64 texture value. */
    public ItemBuilder skull(String nameOrTexture) {
        if (meta instanceof SkullMeta skull && nameOrTexture != null && !nameOrTexture.isBlank()) {
            Heads.apply(skull, nameOrTexture);
        }
        return this;
    }

    // ------------------------------------------------------------- mechanics

    /**
     * Shows a durability bar reflecting {@code uses} remaining swings.
     *
     * <p>Vanilla durability is reused as the display, and the authoritative
     * counter lives in persistent data so the number survives repairs and
     * cannot be desynced by another plugin editing the damage value.
     */
    public ItemBuilder uses(int uses) {
        if (uses <= 0 || !(meta instanceof Damageable damageable)) {
            return this;
        }
        int maxDurability = stack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return this;
        }
        meta.getPersistentDataContainer().set(Keys.usesLeft(), PersistentDataType.INTEGER, uses);
        meta.getPersistentDataContainer().set(Keys.usesMax(), PersistentDataType.INTEGER, uses);
        // One "use" maps onto a slice of the vanilla bar so it empties as uses run out.
        damageable.setDamage(0);
        return this;
    }

    public ItemBuilder attribute(Attribute attribute, double amount) {
        if (amount == 0) {
            return this;
        }
        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey("ultimatechristmas", attribute.name().toLowerCase(java.util.Locale.ROOT)
                        + "_" + UUID.randomUUID().toString().substring(0, 8)),
                amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(attribute, modifier);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, int value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
