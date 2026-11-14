package fr.elias.ultimateChristmas.modules.leaderboard.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.storage.ProfileStore;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.leaderboard.api.LeaderboardService;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Festive leaderboards.
 *
 * <p>Queries run off the main thread and are cached briefly, because a board is
 * usually opened by several players at once and the YAML backend answers it by
 * scanning the player-data folder.
 */
@PluginModule(value = "leaderboards", dependencies = "platform::platform",
        configuration = true)
public final class LeaderboardModule extends FeatureModule implements LeaderboardService {

    /** One cached board result. */
    private record Cached(List<ProfileStore.Entry> rows, long expiresAt) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, String> boards = new LinkedHashMap<>();
    private volatile LeaderboardParameters parameters;

    public LeaderboardModule(ChristmasPlatform platform, LeaderboardParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        loadBoards();
        command(new TopCommand());
        publish(LeaderboardService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = LeaderboardParameters.from(config(), LeaderboardParameters.DEFAULTS);
        loadBoards();
        cache.clear();
    }

    /**
     * Reads the board definitions: a display name per stat key.
     *
     * <p>Falling back to a sensible set means a server that deletes the section
     * still gets working boards rather than none.
     */
    private void loadBoards() {
        boards.clear();
        var section = section("boards");
        for (String stat : section.getKeys(false)) {
            boards.put(stat.toLowerCase(Locale.ROOT), section.getString(stat, stat));
        }
        if (boards.isEmpty()) {
            boards.put("shards", "Shards");
            boards.put("shards_earned", "Shards earned");
            boards.put("gifts_opened", "Gifts opened");
            boards.put("presents_found", "Presents found");
            boards.put("snowballs_hit", "Snowball hits");
            boards.put("boss_damage", "Boss damage");
        }
    }

    // ------------------------------------------------------------ public API

    @Override
    public java.util.Set<String> boards() {
        return boards.keySet();
    }

    @Override
    public CompletableFuture<List<ProfileStore.Entry>> top(String board, int limit) {
        String key = board.toLowerCase(Locale.ROOT);
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached.rows());
        }
        return platform.profiles().top(key, limit).thenApply(rows -> {
            cache.put(key, new Cached(rows,
                    System.currentTimeMillis() + parameters.cacheSeconds() * 1000L));
            return rows;
        });
    }

    /** {@code /christmas top [board] [count]}. */
    private final class TopCommand implements SubCommand {

        @Override
        public String name() {
            return "top";
        }

        @Override
        public List<String> aliases() {
            return List.of("leaderboard", "baltop");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.top";
        }

        @Override
        public String usage() {
            return "[board] [count]";
        }

        @Override
        public String description() {
            return "See the festive leaderboards";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String board = args.length == 0
                    ? boards.keySet().iterator().next()
                    : args[0].toLowerCase(Locale.ROOT);
            if (!boards.containsKey(board)) {
                throw new CommandError("No board called '" + board + "'. Try: "
                        + String.join(", ", boards.keySet()));
            }
            int limit = parameters.defaultSize();
            if (args.length > 1) {
                try {
                    limit = Math.max(1, Math.min(parameters.maxSize(),
                            Integer.parseInt(args[1])));
                } catch (NumberFormatException ex) {
                    throw new CommandError("'" + args[1] + "' is not a number.");
                }
            }

            final int size = limit;
            platform.messages().send(sender, "leaderboard.loading");
            top(board, size).thenAccept(rows -> Bukkit.getScheduler().runTask(
                    platform.plugin(), () -> render(sender, board, rows)));
        }

        private void render(CommandSender sender, String board,
                            List<ProfileStore.Entry> rows) {
            platform.messages().send(sender, "leaderboard.header",
                    Messages.of("stat", boards.get(board)));
            if (rows.isEmpty()) {
                platform.messages().send(sender, "leaderboard.empty");
                return;
            }
            int rank = 1;
            for (ProfileStore.Entry entry : rows) {
                platform.messages().send(sender, "leaderboard.row", Messages.of(
                        "rank", String.valueOf(rank++),
                        "player", entry.name() == null ? "?" : entry.name(),
                        "value", EconomyService.format(entry.value())));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return new ArrayList<>(boards.keySet());
            }
            if (args.length == 2) {
                return List.of("5", "10", "25");
            }
            return List.of();
        }
    }
}
