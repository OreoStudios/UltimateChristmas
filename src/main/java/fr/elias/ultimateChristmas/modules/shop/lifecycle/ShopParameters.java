package fr.elias.ultimateChristmas.modules.shop.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the store, built by its functor.
 *
 * @param title            menu title, with {@code %category%} and {@code %balance%}
 * @param confirmPurchases show a yes/no step before taking payment
 * @param openPermission   permission needed to open the store
 */
public record ShopParameters(
        String title,
        boolean confirmPurchases,
        String openPermission
) {

    public static final ShopParameters DEFAULTS = new ShopParameters(
            "&6&l❄ Santa's Workshop &7- %category%",
            true,
            "ultimatechristmas.shop");

    public static ShopParameters from(ModuleConfiguration config, ShopParameters defaults) {
        return new ShopParameters(
                config.getString("menu.title", defaults.title()),
                config.getBoolean("confirm-purchases", defaults.confirmPurchases()),
                config.getString("open-permission", defaults.openPermission()));
    }
}
