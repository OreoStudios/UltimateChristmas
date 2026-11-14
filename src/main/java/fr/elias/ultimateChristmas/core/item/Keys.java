package fr.elias.ultimateChristmas.core.item;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Persistent-data keys the plugin stamps onto its items and entities.
 *
 * <p>Tagging beats the 3.x approach of recognising an item by its display name:
 * a renamed item stopped working, and anyone could forge one with an anvil.
 */
public final class Keys {

    private static NamespacedKey giftType;
    private static NamespacedKey presentId;
    private static NamespacedKey usesLeft;
    private static NamespacedKey usesMax;
    private static NamespacedKey cosmeticId;
    private static NamespacedKey shopItem;
    private static NamespacedKey npcRole;
    private static NamespacedKey bossId;
    private static NamespacedKey ornamentId;
    private static NamespacedKey feastId;
    private static NamespacedKey menuMarker;
    private static NamespacedKey adventDay;
    private static NamespacedKey wrappedGift;
    private static NamespacedKey owner;
    private static NamespacedKey snowballKind;

    private Keys() {
    }

    public static void init(UltimateChristmas plugin) {
        giftType = new NamespacedKey(plugin, "gift_type");
        presentId = new NamespacedKey(plugin, "present_id");
        usesLeft = new NamespacedKey(plugin, "uses_left");
        usesMax = new NamespacedKey(plugin, "uses_max");
        cosmeticId = new NamespacedKey(plugin, "cosmetic_id");
        shopItem = new NamespacedKey(plugin, "shop_item");
        npcRole = new NamespacedKey(plugin, "npc_role");
        bossId = new NamespacedKey(plugin, "boss_id");
        ornamentId = new NamespacedKey(plugin, "ornament_id");
        feastId = new NamespacedKey(plugin, "feast_id");
        menuMarker = new NamespacedKey(plugin, "menu_marker");
        adventDay = new NamespacedKey(plugin, "advent_day");
        wrappedGift = new NamespacedKey(plugin, "wrapped_gift");
        owner = new NamespacedKey(plugin, "owner");
        snowballKind = new NamespacedKey(plugin, "snowball_kind");
    }

    public static NamespacedKey giftType() {
        return giftType;
    }

    public static NamespacedKey presentId() {
        return presentId;
    }

    public static NamespacedKey usesLeft() {
        return usesLeft;
    }

    public static NamespacedKey usesMax() {
        return usesMax;
    }

    public static NamespacedKey cosmeticId() {
        return cosmeticId;
    }

    public static NamespacedKey shopItem() {
        return shopItem;
    }

    public static NamespacedKey npcRole() {
        return npcRole;
    }

    public static NamespacedKey bossId() {
        return bossId;
    }

    public static NamespacedKey ornamentId() {
        return ornamentId;
    }

    public static NamespacedKey feastId() {
        return feastId;
    }

    public static NamespacedKey menuMarker() {
        return menuMarker;
    }

    public static NamespacedKey adventDay() {
        return adventDay;
    }

    public static NamespacedKey wrappedGift() {
        return wrappedGift;
    }

    public static NamespacedKey owner() {
        return owner;
    }

    public static NamespacedKey snowballKind() {
        return snowballKind;
    }

    // ----------------------------------------------------------- convenience

    public static String getString(PersistentDataContainer container, NamespacedKey key) {
        return container == null ? null : container.get(key, PersistentDataType.STRING);
    }

    public static boolean has(PersistentDataContainer container, NamespacedKey key) {
        return container != null && container.has(key);
    }

    public static int getInt(PersistentDataContainer container, NamespacedKey key, int fallback) {
        if (container == null) {
            return fallback;
        }
        Integer value = container.get(key, PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }
}
