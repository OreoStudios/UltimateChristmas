package fr.elias.ultimateChristmas.modules.secretsanta.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.secretsanta.api.SecretSantaService;
import fr.elias.ultimateChristmas.modules.secretsanta.infra.Derangement;
import fr.elias.ultimateChristmas.modules.secretsanta.infra.SecretSantaState;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A server-wide Secret Santa gift exchange.
 *
 * <p>Players opt in, an admin draws the pairings as a derangement (nobody gets
 * themselves), and each player anonymously delivers a held item to their
 * assigned recipient. A gift for an offline recipient waits and is handed over
 * on their next join.
 */
@PluginModule(value = "secretsanta", dependencies = "platform::platform",
        configuration = true)
public final class SecretSantaModule extends FeatureModule implements SecretSantaService {

    private SecretSantaState state;
    private volatile SecretSantaParameters parameters;

    public SecretSantaModule(ChristmasPlatform platform, SecretSantaParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        state = new SecretSantaState(config());
        listen(new JoinListener());
        command(new SecretSantaCommand());
        publish(SecretSantaService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = SecretSantaParameters.from(config(), SecretSantaParameters.DEFAULTS);
        state.read();
    }

    // ------------------------------------------------------------ public API

    @Override
    public boolean drawn() {
        return state.drawn();
    }

    @Override
    public int participantCount() {
        return state.participantCount();
    }

    @Override
    public OfflinePlayer targetOf(Player player) {
        String target = platform.profiles().get(player).secretSantaTarget();
        if (target == null) {
            return null;
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(target));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // --------------------------------------------------------------- actions

    private void join(Player player) {
        if (state.drawn()) {
            throw new CommandError("Pairings have already been drawn for this exchange.");
        }
        if (state.join(player.getUniqueId())) {
            platform.messages().send(player, "secretsanta.joined");
        } else {
            platform.messages().send(player, "secretsanta.already-joined");
        }
    }

    /**
     * Draws pairings for every participant.
     *
     * <p>Targets are written onto each participant's profile, loading offline
     * participants asynchronously so someone who joined then logged off is still
     * assigned.
     */
    private void draw(CommandSender sender) {
        List<UUID> participants = new ArrayList<>(state.participants());
        Map<UUID, UUID> pairing = Derangement.of(participants);
        if (pairing == null) {
            throw new CommandError("At least two players must join first.");
        }

        pairing.forEach((giver, receiver) ->
                platform.profiles().loadAsync(giver, null).thenAccept(profile -> {
                    profile.secretSantaTarget(receiver.toString());
                }));
        // The writes above are async; flush so they are persisted promptly even
        // for participants who are offline.
        platform.profiles().flushDirty();

        state.markDrawn(true);
        platform.messages().send(sender, "secretsanta.drawn",
                Messages.of("count", String.valueOf(pairing.size())));

        // Tell everyone online who they drew.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (state.joined(player.getUniqueId())) {
                showTarget(player);
            }
        }
    }

    private void showTarget(Player player) {
        OfflinePlayer target = targetOf(player);
        if (target == null) {
            platform.messages().send(player,
                    state.drawn() ? "secretsanta.not-joined" : "secretsanta.no-target");
            return;
        }
        platform.messages().send(player, "secretsanta.target", Messages.of(
                "player", target.getName() == null ? "a player" : target.getName()));
    }

    /** Delivers the item in the player's hand to their assigned recipient. */
    private void deliver(Player player) {
        PlayerProfile profile = platform.profiles().get(player);
        if (!state.joined(player.getUniqueId())) {
            platform.messages().send(player, "secretsanta.not-joined");
            return;
        }
        if (profile.secretSantaTarget() == null) {
            platform.messages().send(player, "secretsanta.no-target");
            return;
        }
        if (profile.secretSantaDelivered()) {
            platform.messages().send(player, "secretsanta.already-delivered");
            return;
        }

        ItemStack gift = player.getInventory().getItemInMainHand();
        if (gift.getType().isAir()) {
            platform.messages().send(player, "secretsanta.empty-hand");
            return;
        }

        UUID recipientId;
        try {
            recipientId = UUID.fromString(profile.secretSantaTarget());
        } catch (IllegalArgumentException ex) {
            platform.messages().send(player, "secretsanta.no-target");
            return;
        }

        ItemStack toSend = gift.clone();
        player.getInventory().setItemInMainHand(null);
        profile.secretSantaDelivered(true);

        Player recipient = Bukkit.getPlayer(recipientId);
        if (recipient != null && recipient.isOnline()) {
            giveGift(recipient, toSend);
        } else {
            // Hold it until they next log in.
            state.storePending(recipientId, toSend, "your Secret Santa");
        }

        if (parameters.nicePoints() > 0) {
            profile.addNaughtyNice(parameters.nicePoints());
        }
        platform.progress().record(player, PlayerProfile.STAT_GIFTS_GIVEN, 1);

        OfflinePlayer target = Bukkit.getOfflinePlayer(recipientId);
        platform.messages().send(player, "secretsanta.delivered", Messages.of(
                "player", target.getName() == null ? "your recipient" : target.getName()));
        if (parameters.announceDelivery()) {
            Bukkit.broadcast(platform.messages().prefixed("secretsanta.received",
                    Map.of()));
        }
    }

    private void giveGift(Player recipient, ItemStack gift) {
        recipient.getInventory().addItem(gift).values().forEach(leftover ->
                recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover));
        platform.messages().send(recipient, "secretsanta.received", Map.of());
        Fx.helix(recipient.getLocation(), org.bukkit.Particle.HEART, 2.0, 0.6);
        Fx.sound(recipient.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
    }

    private final class JoinListener implements Listener {

        /** Hands over any gift that arrived while the player was offline. */
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (!state.hasPending(player.getUniqueId())) {
                return;
            }
            Bukkit.getScheduler().runTaskLater(platform.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                ItemStack gift = state.takePending(player.getUniqueId());
                if (gift != null) {
                    giveGift(player, gift);
                }
            }, 40L);
        }
    }

    /** {@code /christmas secretsanta [join|deliver|draw|reset]}. */
    private final class SecretSantaCommand implements SubCommand {

        @Override
        public String name() {
            return "secretsanta";
        }

        @Override
        public List<String> aliases() {
            return List.of("santa-exchange", "ss");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.secretsanta";
        }

        @Override
        public String usage() {
            return "[join|deliver|draw|reset]";
        }

        @Override
        public String description() {
            return "Join the Secret Santa gift exchange";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "status" -> {
                    if (sender instanceof Player player) {
                        showTarget(player);
                    } else {
                        platform.messages().sendRaw(sender,
                                "<gray>Secret Santa: <white>" + state.participantCount()
                                        + "</white> joined, drawn: " + state.drawn(),
                                Map.of());
                    }
                }
                case "join" -> join(requirePlayer(sender));
                case "deliver" -> deliver(requirePlayer(sender));
                case "draw" -> {
                    requireAdmin(sender);
                    draw(sender);
                }
                case "reset" -> {
                    requireAdmin(sender);
                    clearTargets();
                    state.reset();
                    platform.messages().send(sender, "secretsanta.reset");
                }
                default -> throw new CommandError("Usage: /christmas secretsanta " + usage());
            }
        }

        /** Clears the assigned target off every participant's profile. */
        private void clearTargets() {
            for (UUID id : state.participants()) {
                platform.profiles().loadAsync(id, null).thenAccept(profile -> {
                    profile.secretSantaTarget(null);
                    profile.secretSantaDelivered(false);
                });
            }
            platform.profiles().flushDirty();
        }

        private Player requirePlayer(CommandSender sender) {
            if (sender instanceof Player player) {
                return player;
            }
            throw new CommandError("Only a player can do that.");
        }

        private void requireAdmin(CommandSender sender) {
            if (!sender.hasPermission("ultimatechristmas.admin.secretsanta")) {
                throw new CommandError("You may not run the exchange.");
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(List.of("join", "deliver", "status"));
                if (sender.hasPermission("ultimatechristmas.admin.secretsanta")) {
                    options.add("draw");
                    options.add("reset");
                }
                return options;
            }
            return List.of();
        }
    }
}
