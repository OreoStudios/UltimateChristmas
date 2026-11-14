package fr.elias.ultimateChristmas.modules.quests.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.quests.api.QuestService;
import fr.elias.ultimateChristmas.modules.quests.infra.QuestCatalog;
import fr.elias.ultimateChristmas.modules.quests.infra.QuestMenu;
import fr.elias.ultimateChristmas.modules.quests.infra.QuestTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Daily, weekly and season-long festive objectives.
 *
 * <p>Tracks whatever the rest of the plugin records, so adding an objective is
 * a config change rather than a code change.
 */
@PluginModule(value = "quests", dependencies = "platform::platform", configuration = true)
public final class QuestModule extends FeatureModule implements QuestService {

    private QuestCatalog catalog;
    private QuestTracker tracker;
    private volatile QuestParameters parameters;

    public QuestModule(ChristmasPlatform platform, QuestParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        catalog = new QuestCatalog(platform.plugin().getLogger());
        catalog.load(section("objectives"));

        tracker = new QuestTracker(platform, catalog, this::emit);
        platform.progress().subscribe(tracker);
        // Unsubscribe with the module, otherwise a disable would leave a dead
        // tracker still advancing objectives.
        context().lifecycle().onClose(() -> platform.progress().unsubscribe(tracker));

        command(new QuestCommand());
        publish(QuestService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = QuestParameters.from(config(), QuestParameters.DEFAULTS);
        catalog.load(section("objectives"));
    }

    // ------------------------------------------------------------ public API

    @Override
    public Set<String> activeQuests(Player player) {
        PlayerProfile profile = platform.profiles().get(player);
        tracker.rollOverIfNeeded(profile);
        Set<String> active = new HashSet<>(catalog.ids());
        active.removeAll(profile.questsCompleted());
        return active;
    }

    @Override
    public long progressOf(Player player, String questId) {
        return platform.profiles().get(player).questProgress(questId);
    }

    @Override
    public int completedCount(Player player) {
        PlayerProfile profile = platform.profiles().get(player);
        int count = 0;
        for (String id : catalog.ids()) {
            if (profile.questCompleted(id)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void openMenu(Player player) {
        if (catalog.isEmpty()) {
            platform.messages().send(player, "quests.none");
            return;
        }
        platform.menus().open(player, new QuestMenu(platform, catalog, tracker));
    }

    /** {@code /christmas quests [reset <player>]}. */
    private final class QuestCommand implements SubCommand {

        @Override
        public String name() {
            return "quests";
        }

        @Override
        public List<String> aliases() {
            return List.of("objectives", "quest");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.quests";
        }

        @Override
        public String usage() {
            return "[reset <player>]";
        }

        @Override
        public String description() {
            return "See your festive objectives";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
                if (!sender.hasPermission("ultimatechristmas.admin.quests")) {
                    throw new CommandError("You may not reset objectives.");
                }
                if (args.length < 2) {
                    throw new CommandError("Usage: /christmas quests reset <player>");
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    throw new CommandError("'" + args[1] + "' is not online.");
                }
                tracker.resetAll(platform.profiles().get(target));
                platform.messages().send(sender, "quests.reset",
                        Messages.of("player", target.getName()));
                return;
            }

            if (!(sender instanceof Player player)) {
                throw new CommandError("Only a player has objectives.");
            }
            openMenu(player);
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1 && sender.hasPermission("ultimatechristmas.admin.quests")) {
                return List.of("reset");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            return List.of();
        }
    }

    /** Whether finishing an objective is announced to the server. */
    public boolean announceCompletions() {
        return parameters.announceCompletions();
    }
}
