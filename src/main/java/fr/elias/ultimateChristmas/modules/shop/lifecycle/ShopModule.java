package fr.elias.ultimateChristmas.modules.shop.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.shop.api.ShopService;
import fr.elias.ultimateChristmas.modules.shop.infra.ShopCatalog;
import fr.elias.ultimateChristmas.modules.shop.infra.ShopMenu;
import fr.elias.ultimateChristmas.modules.shop.infra.ShopPurchases;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Santa's Workshop: the shard store.
 *
 * <p>Entries are rewards, so anything the advent calendar or a boss can give
 * out can also be sold, using the same config shape.
 */
@PluginModule(value = "shop", dependencies = "platform::platform", configuration = true)
public final class ShopModule extends FeatureModule implements ShopService {

    private ShopCatalog catalog;
    private ShopPurchases purchases;
    private volatile ShopParameters parameters;

    public ShopModule(ChristmasPlatform platform, ShopParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        catalog = new ShopCatalog(platform.plugin().getLogger());
        catalog.load(section("categories"));
        purchases = new ShopPurchases(platform, this::emit);

        command(new OpenShopCommand());
        publish(ShopService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = ShopParameters.from(config(), ShopParameters.DEFAULTS);
        catalog.load(section("categories"));
    }

    // ------------------------------------------------------------ public API

    @Override
    public void openShop(Player player) {
        if (catalog.isEmpty()) {
            platform.messages().send(player, "shop.empty");
            return;
        }
        ShopCatalog.Category start = catalog.soleCategory() != null
                ? catalog.soleCategory()
                : catalog.categories().iterator().next();
        platform.menus().open(player,
                new ShopMenu(catalog, purchases, parameters.title(),
                        parameters.confirmPurchases(), start));
    }

    @Override
    public Set<String> categories() {
        return catalog.categoryIds();
    }

    @Override
    public int entryCount() {
        return catalog.entryCount();
    }

    /** {@code /christmas shop} — opens the store. */
    private final class OpenShopCommand implements SubCommand {

        @Override
        public String name() {
            return "shop";
        }

        @Override
        public List<String> aliases() {
            return List.of("store", "workshop");
        }

        @Override
        public String permission() {
            return parameters.openPermission();
        }

        @Override
        public String usage() {
            return "";
        }

        @Override
        public String description() {
            return "Open Santa's Workshop store";
        }

        @Override
        public boolean playerOnly() {
            return true;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                throw new CommandError("Only a player can shop.");
            }
            openShop(player);
        }
    }
}
