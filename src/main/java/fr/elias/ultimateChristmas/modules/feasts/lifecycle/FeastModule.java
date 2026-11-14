package fr.elias.ultimateChristmas.modules.feasts.lifecycle;

import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.core.text.Text;
import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.modules.feasts.api.FeastService;
import fr.elias.ultimateChristmas.modules.feasts.infra.FeastFoods;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hot cocoa, gingerbread and other festive food that grants buffs.
 *
 * <p>Festive food is recognised by a persistent tag, not its name or material,
 * so a renamed stack still works and an ordinary cookie never triggers a buff.
 */
@PluginModule(value = "feasts", dependencies = "platform::platform", configuration = true)
public final class FeastModule extends FeatureModule implements FeastService {

    private FeastFoods foods;
    private volatile FeastParameters parameters;

    public FeastModule(ChristmasPlatform platform, FeastParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        foods = new FeastFoods(platform.plugin().getLogger());
        foods.load(section("foods"));

        listen(new EatListener());
        command(new FeastCommand());
        publish(FeastService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = FeastParameters.from(config(), FeastParameters.DEFAULTS);
        foods.load(section("foods"));
    }

    // ------------------------------------------------------------ public API

    @Override
    public java.util.Set<String> foods() {
        return foods.ids();
    }

    @Override
    public ItemStack create(String foodId, int amount) {
        return foods.create(foodId, amount);
    }

    @Override
    public boolean give(Player player, String foodId, int amount) {
        ItemStack item = foods.create(foodId, amount);
        if (item == null) {
            return false;
        }
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return true;
    }

    private final class EatListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onEat(PlayerItemConsumeEvent event) {
            String id = FeastFoods.foodOf(event.getItem());
            if (id == null) {
                return;
            }
            FeastFoods.Food food = foods.get(id);
            if (food == null) {
                return;
            }
            Player player = event.getPlayer();
            PlayerProfile profile = platform.profiles().get(player);

            // The cooldown lives on the profile so it cannot be reset by
            // relogging, and a food still on cooldown simply is not eaten.
            String cooldownKey = "feast-" + id;
            if (food.cooldown() > 0 && profile.onCooldown(cooldownKey)) {
                event.setCancelled(true);
                platform.messages().send(player, "feasts.cooldown", Messages.of(
                        "remaining", Season.format(java.time.Duration.ofMillis(
                                profile.cooldownRemaining(cooldownKey)))));
                return;
            }

            for (var effect : food.effects()) {
                player.addPotionEffect(effect);
            }
            if (food.restoreFood() > 0) {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + food.restoreFood()));
            }
            if (food.healHearts() > 0) {
                double max = player.getAttribute(Attribute.MAX_HEALTH) == null
                        ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(max, player.getHealth() + food.healHearts()));
            }
            if (food.cooldown() > 0) {
                profile.setCooldown(cooldownKey, food.cooldown() * 1000L);
            }

            if (food.message() != null) {
                player.sendMessage(Text.parse(food.message(),
                        Messages.of("player", player.getName())));
            } else {
                platform.messages().send(player, "feasts.eaten",
                        Messages.of("name", displayName(food, id)));
            }

            if (parameters.particlesOnEat()) {
                Fx.burst(player.getLocation().add(0, 1, 0),
                        org.bukkit.Particle.HEART, 6, 0.3);
                Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.4f);
            }
        }
    }

    /** A food's display name for messages, falling back to its id. */
    private static String displayName(FeastFoods.Food food, String id) {
        var meta = food.display().getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return Text.plain(meta.displayName());
        }
        return id;
    }

    /** {@code /christmas feast [give <food> <player> [amount]]}. */
    private final class FeastCommand implements SubCommand {

        @Override
        public String name() {
            return "feast";
        }

        @Override
        public List<String> aliases() {
            return List.of("food");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.feasts";
        }

        @Override
        public String usage() {
            return "[give <food> <player> [amount]]";
        }

        @Override
        public String description() {
            return "List or hand out festive food";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                platform.messages().sendRaw(sender,
                        "<gray>Festive food: <white>" + String.join(", ", foods.ids()),
                        java.util.Map.of());
                return;
            }
            if (!args[0].equalsIgnoreCase("give")) {
                throw new CommandError("Usage: /christmas feast " + usage());
            }
            if (!sender.hasPermission("ultimatechristmas.admin.feasts")) {
                throw new CommandError("You may not hand out food.");
            }
            if (args.length < 3) {
                throw new CommandError("Usage: /christmas feast give <food> <player> [amount]");
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                throw new CommandError("'" + args[2] + "' is not online.");
            }
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ex) {
                    throw new CommandError("'" + args[3] + "' is not a number.");
                }
            }
            if (!give(target, args[1], amount)) {
                throw new CommandError("No food called '" + args[1] + "'. Known: "
                        + String.join(", ", foods.ids()));
            }
            platform.messages().send(sender, "feasts.given", Messages.of(
                    "name", args[1], "player", target.getName()));
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("list", "give");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return new ArrayList<>(foods.ids());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            return List.of();
        }
    }
}
