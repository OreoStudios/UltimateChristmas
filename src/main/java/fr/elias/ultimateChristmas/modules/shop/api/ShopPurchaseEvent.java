package fr.elias.ultimateChristmas.modules.shop.api;

import java.util.UUID;

/**
 * Published after a shop purchase completes and has been paid for.
 *
 * @param player   the buyer
 * @param entryId  the shop entry's config key
 * @param price    shards spent, or 0 if paid in server currency
 * @param money    server currency spent, or 0 if paid in shards
 */
public record ShopPurchaseEvent(UUID player, String entryId, long price, double money) {
}
