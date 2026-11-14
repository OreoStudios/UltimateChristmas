package fr.elias.ultimateChristmas.modules.pass.lifecycle;

import dev.oreo.modulith.core.ModuleListener;
import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.progress.ProgressService;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.advent.api.AdventClaimedEvent;
import fr.elias.ultimateChristmas.modules.pass.api.PassService;
import fr.elias.ultimateChristmas.modules.pass.api.PassTierUnlockedEvent;
import fr.elias.ultimateChristmas.modules.pass.infra.PassMenu;
import fr.elias.ultimateChristmas.modules.pass.infra.PassTrack;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.quests.api.QuestCompletedEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The festive pass: earn experience all season, unlock tiers, claim rewards.
 *
 * <p>Experience comes from three places, all of them loosely coupled: a
 * per-stat rate table fed by the progress service, and two domain events from
 * the advent and quest modules. Those are declared as {@code uses} rather than
 * {@code dependencies}, so the pass keeps working with either of them disabled.
 */
@PluginModule(
        value = "pass",
        dependencies = "platform::platform",
        uses = {"advent::advent", "quests::quests"},
        configuration = true)
public final class PassModule extends FeatureModule implements PassService {

    private PassTrack track;
    private volatile PassParameters parameters;
    private XpFromProgress progressListener;

    public PassModule(ChristmasPlatform platform, PassParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        track = new PassTrack(platform.plugin().getLogger());
        track.load(section("tiers"));

        progressListener = new XpFromProgress();
        platform.progress().subscribe(progressListener);
        context().lifecycle().onClose(
                () -> platform.progress().unsubscribe(progressListener));

        // Modulith unsubscribes these with the module's lifecycle.
        subscribe(this);

        command(new PassCommand());
        publish(PassService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = PassParameters.from(config(), PassParameters.DEFAULTS);
        track.load(section("tiers"));
    }

    // --------------------------------------------------- cross-module events

    /** Advent doors are worth pass experience. */
    @ModuleListener
    public void onAdventClaim(AdventClaimedEvent event) {
        Player player = Bukkit.getPlayer(event.player());
        if (player != null) {
            addXp(player, parameters.xpPerAdventDoor());
        }
    }

    /** So are completed objectives. */
    @ModuleListener
    public void onQuestCompleted(QuestCompletedEvent event) {
        Player player = Bukkit.getPlayer(event.player());
        if (player != null) {
            addXp(player, parameters.xpPerQuest());
        }
    }

    /** Converts tracked stats into pass experience at a configured rate. */
    private final class XpFromProgress implements ProgressService.Listener {

        @Override
        public void onProgress(Player player, String stat, long delta, long total) {
            double rate = parameters.xpRates().getOrDefault(
                    stat.toLowerCase(Locale.ROOT), 0.0);
            if (rate <= 0) {
                return;
            }
            long gained = Math.round(delta * rate);
            if (gained > 0) {
                addXp(player, gained);
            }
        }
    }

    // ------------------------------------------------------------ public API

    @Override
    public int tierOf(Player player) {
        return track.tierFor(platform.profiles().get(player).passXp());
    }

    @Override
    public int maxTier() {
        return track.maxTier();
    }

    @Override
    public void addXp(Player player, long amount) {
        if (amount <= 0 || track.isEmpty()) {
            return;
        }
        PlayerProfile profile = platform.profiles().get(player);
        int before = track.tierFor(profile.passXp());
        profile.addPassXp(amount);
        int after = track.tierFor(profile.passXp());

        // Announce every tier crossed, not just the last, so a large reward
        // that jumps two tiers still tells the player about both.
        for (int tier = before + 1; tier <= after; tier++) {
            platform.messages().send(player, "pass.tier-up",
                    Messages.of("tier", String.valueOf(tier)));
            emit(new PassTierUnlockedEvent(player.getUniqueId(), tier));
        }
    }

    @Override
    public boolean isPremium(Player player) {
        return platform.profiles().get(player).passPremium()
                || player.hasPermission(parameters.premiumPermission());
    }

    @Override
    public void openMenu(Player player) {
        if (track.isEmpty()) {
            platform.messages().send(player, "reward.none-configured");
            return;
        }
        platform.menus().open(player, new PassMenu(platform, track, this::claimTier));
    }

    /** Claims one tier, reporting why if it cannot be claimed. */
    private void claimTier(Player player, int tierNumber) {
        PassTrack.Tier tier = track.tier(tierNumber);
        if (tier == null) {
            return;
        }
        PlayerProfile profile = platform.profiles().get(player);
        if (track.tierFor(profile.passXp()) < tierNumber) {
            platform.messages().send(player, "pass.locked",
                    Messages.of("tier", String.valueOf(tierNumber)));
            return;
        }
        if (profile.hasClaimedTier(tierNumber)) {
            platform.messages().send(player, "pass.already-claimed");
            return;
        }

        // Mark claimed before granting: a reward that throws must not leave the
        // tier claimable again and duplicable.
        profile.claimTier(tierNumber);
        if (tier.free() != null) {
            platform.rewards().grant(player, tier.free(),
                    Messages.of("tier", String.valueOf(tierNumber)));
        }
        if (tier.premium() != null) {
            if (isPremium(player)) {
                platform.rewards().grant(player, tier.premium(),
                        Messages.of("tier", String.valueOf(tierNumber)));
            } else {
                platform.messages().send(player, "pass.premium-only");
            }
        }
        platform.messages().send(player, "pass.claimed",
                Messages.of("tier", String.valueOf(tierNumber)));
    }

    /** {@code /christmas pass [premium <player>]}. */
    private final class PassCommand implements SubCommand {

        @Override
        public String name() {
            return "pass";
        }

        @Override
        public List<String> aliases() {
            return List.of("battlepass");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.pass";
        }

        @Override
        public String usage() {
            return "[premium <player>]";
        }

        @Override
        public String description() {
            return "Open your festive pass";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("premium")) {
                if (!sender.hasPermission("ultimatechristmas.admin.pass")) {
                    throw new CommandError("You may not grant premium passes.");
                }
                if (args.length < 2) {
                    throw new CommandError("Usage: /christmas pass premium <player>");
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    throw new CommandError("'" + args[1] + "' is not online.");
                }
                platform.profiles().get(target).passPremium(true);
                platform.messages().send(sender, "pass.premium-granted",
                        Messages.of("player", target.getName()));
                return;
            }

            if (!(sender instanceof Player player)) {
                throw new CommandError("Only a player has a pass.");
            }
            PlayerProfile profile = platform.profiles().get(player);
            long next = track.nextRequirement(profile.passXp());
            platform.messages().send(player, "pass.status", Map.of(
                    "tier", String.valueOf(tierOf(player)),
                    "xp", String.valueOf(profile.passXp()),
                    "needed", next < 0 ? "max" : String.valueOf(next)));
            openMenu(player);
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1 && sender.hasPermission("ultimatechristmas.admin.pass")) {
                return List.of("premium");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("premium")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            return List.of();
        }
    }
}
