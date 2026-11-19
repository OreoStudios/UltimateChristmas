package fr.elias.ultimateChristmas.modules.cosmetics.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.cosmetics.api.CosmeticsService;
import fr.elias.ultimateChristmas.modules.cosmetics.infra.CosmeticCatalog;
import fr.elias.ultimateChristmas.modules.cosmetics.infra.CosmeticsMenu;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hats and particle trails.
 *
 * <p>Both are cosmetic only. A hat is worn in the helmet slot, and whatever
 * armour was there is handed back to the player rather than destroyed — losing
 * someone's enchanted helmet to a cosmetic would be unforgivable.
 */
@PluginModule(value = "cosmetics", dependencies = "platform::platform", configuration = true)
public final class CosmeticsModule extends FeatureModule implements CosmeticsService {

    private CosmeticCatalog catalog;
    private volatile CosmeticsParameters parameters;

    public CosmeticsModule(ChristmasPlatform platform, CosmeticsParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        catalog = new CosmeticCatalog(platform.plugin().getLogger());
        catalog.load(section("items"));

        timer(this::tickTrails, 20L, parameters.trailTickPeriod());
        command(new CosmeticsCommand());
        publish(CosmeticsService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = CosmeticsParameters.from(config(), CosmeticsParameters.DEFAULTS);
        catalog.load(section("items"));
    }

    /** Emits one pass of every equipped trail. */
    private void tickTrails() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = platform.profiles().cached(player.getUniqueId());
            if (profile == null) {
                continue;
            }
            CosmeticCatalog.Cosmetic trail = catalog.get(
                    profile.equipped(CosmeticCatalog.Kind.TRAIL.slot()));
            if (trail == null || trail.particle() == null) {
                continue;
            }
            player.getWorld().spawnParticle(trail.particle(),
                    player.getLocation().add(0, 0.2, 0),
                    parameters.trailParticles(), 0.2, 0.1, 0.2, 0.01);
        }
    }

    // ------------------------------------------------------------ public API

    @Override
    public Set<String> catalogue() {
        return catalog.ids();
    }

    @Override
    public Set<String> owned(Player player) {
        return platform.profiles().get(player).cosmeticsOwned();
    }

    @Override
    public boolean grant(Player player, String cosmeticId) {
        CosmeticCatalog.Cosmetic cosmetic = catalog.get(cosmeticId);
        if (cosmetic == null) {
            return false;
        }
        platform.profiles().get(player).grantCosmetic(cosmeticId);
        return true;
    }

    @Override
    public boolean equip(Player player, String cosmeticId) {
        CosmeticCatalog.Cosmetic cosmetic = catalog.get(cosmeticId);
        if (cosmetic == null) {
            return false;
        }
        PlayerProfile profile = platform.profiles().get(player);
        boolean owns = cosmetic.freeToAll() || profile.owns(cosmeticId)
                || (cosmetic.permission() != null && player.hasPermission(cosmetic.permission()));
        if (!owns) {
            platform.messages().send(player, "cosmetics.locked");
            return false;
        }

        profile.equip(cosmetic.kind().slot(), cosmeticId);
        if (cosmetic.kind() == CosmeticCatalog.Kind.HAT && parameters.replaceHelmet()) {
            wearHat(player, cosmetic.icon());
        }
        platform.messages().send(player, "cosmetics.equipped",
                Messages.of("name", Text.formatted(cosmetic.name())));
        return true;
    }

    /** Puts the hat on, returning any existing helmet to the player. */
    private void wearHat(Player player, ItemStack hat) {
        ItemStack previous = player.getInventory().getHelmet();
        player.getInventory().setItem(EquipmentSlot.HEAD, hat.clone());
        if (previous != null && !previous.getType().isAir()) {
            player.getInventory().addItem(previous).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Clears a slot, taking the hat off the player's head if it is one. */
    private void unequip(Player player, CosmeticCatalog.Kind kind) {
        PlayerProfile profile = platform.profiles().get(player);
        String current = profile.equipped(kind.slot());
        profile.equip(kind.slot(), null);

        if (kind == CosmeticCatalog.Kind.HAT && current != null
                && parameters.replaceHelmet()) {
            ItemStack worn = player.getInventory().getHelmet();
            // Only clear the slot if the hat is still the thing being worn.
            if (worn != null && fr.elias.ultimateChristmas.core.item.Keys.getString(
                    worn.hasItemMeta() ? worn.getItemMeta().getPersistentDataContainer() : null,
                    fr.elias.ultimateChristmas.core.item.Keys.cosmeticId()) != null) {
                player.getInventory().setItem(EquipmentSlot.HEAD, null);
            }
        }
        platform.messages().send(player, "cosmetics.unequipped",
                Messages.of("slot", kind.slot()));
    }

    @Override
    public void openMenu(Player player) {
        if (catalog.isEmpty()) {
            platform.messages().send(player, "cosmetics.none-owned");
            return;
        }
        platform.menus().open(player,
                new CosmeticsMenu(platform, catalog, (who, id) -> equip(who, id), this::unequip));
    }

    /** {@code /christmas cosmetics [grant <player> <id>]}. */
    private final class CosmeticsCommand implements SubCommand {

        @Override
        public String name() {
            return "cosmetics";
        }

        @Override
        public List<String> aliases() {
            return List.of("wardrobe", "hats");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.cosmetics";
        }

        @Override
        public String usage() {
            return "[grant <player> <id>]";
        }

        @Override
        public String description() {
            return "Open the festive wardrobe";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("grant")) {
                if (!sender.hasPermission("ultimatechristmas.admin.cosmetics")) {
                    throw new CommandError("You may not grant cosmetics.");
                }
                if (args.length < 3) {
                    throw new CommandError("Usage: /christmas cosmetics grant <player> <id>");
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    throw new CommandError("'" + args[1] + "' is not online.");
                }
                if (!grant(target, args[2])) {
                    throw new CommandError("No cosmetic called '" + args[2] + "'. Known: "
                            + String.join(", ", catalog.ids()));
                }
                CosmeticCatalog.Cosmetic cosmetic = catalog.get(args[2]);
                platform.messages().send(sender, "cosmetics.granted", Messages.of(
                        "name", Text.formatted(cosmetic.name()), "player", target.getName()));
                return;
            }

            if (!(sender instanceof Player player)) {
                throw new CommandError("Only a player has a wardrobe.");
            }
            openMenu(player);
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1
                    && sender.hasPermission("ultimatechristmas.admin.cosmetics")) {
                return List.of("grant");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("grant")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
                return new ArrayList<>(catalog.ids());
            }
            return List.of();
        }
    }
}
