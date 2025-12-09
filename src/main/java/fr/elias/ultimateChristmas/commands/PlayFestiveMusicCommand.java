package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.music.MusicManager;
import fr.elias.ultimateChristmas.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class PlayFestiveMusicCommand implements CommandExecutor, TabCompleter {

    // Permissions
    private static final String PERM_USE   = "ultimatechristmas.music";
    private static final String PERM_OTHERS = "ultimatechristmas.music.others";
    private static final String PERM_STOP = "ultimatechristmas.music.stop";

    private final UltimateChristmas plugin;
    private final MusicManager music;
    private final Logger log;
    private final boolean debug;

    public PlayFestiveMusicCommand(UltimateChristmas plugin, MusicManager musicManager) {
        this.plugin = plugin;
        this.music = musicManager;
        this.log = plugin.getLogger();
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    /* ------------- Lang helpers ------------- */

    private FileConfiguration langCfg() {
        // Uses UltimateChristmas#getConfig(String) -> ConfigUtil
        return plugin.getConfig("lang.yml");
    }

    private String msg(String path, String def) {
        return Msg.color(langCfg().getString(path, def));
    }

    private String msg(String path, String def, java.util.Map<String, String> placeholders) {
        String base = langCfg().getString(path, def);
        if (base == null) base = def;
        for (var entry : placeholders.entrySet()) {
            base = base.replace(entry.getKey(), entry.getValue());
        }
        return Msg.color(base);
    }

    private void d(String s) {
        if (debug) log.info("[PlayFestiveMusic][DEBUG] " + s);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(msg(
                "music.usage",
                "&eUsage: &f/%label% &7<trackKey|list|stop> [&7self|playerName&f]",
                java.util.Map.of("%label%", label)
        ));
        sender.sendMessage(msg(
                "music.usage-list",
                "&8- &flist &7: shows available tracks",
                java.util.Collections.emptyMap()
        ));
        sender.sendMessage(msg(
                "music.usage-stop",
                "&8- &fstop &7: stops the music",
                java.util.Collections.emptyMap()
        ));
        if (!music.isNoteBlockAvailable()) {
            sender.sendMessage(msg(
                    "music.noteblock-missing",
                    "&cNoteBlockAPI is not loaded. Install it and add &fsoftdepend: [NoteBlockAPI]&c.",
                    java.util.Collections.emptyMap()
            ));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(msg(
                    "music.no-permission",
                    "&cYou don't have permission.",
                    java.util.Collections.emptyMap()
            ));
            return true;
        }

        // /playchristmas
        if (args.length == 0) {
            sendUsage(sender, label);

            // Quick helper showing available keys
            List<String> keys = music.getKeys();
            if (keys.isEmpty()) {
                sender.sendMessage(msg(
                        "music.no-tracks",
                        "&7No tracks found. Configure &fmusic.yml &7→ &etracks:*",
                        java.util.Collections.emptyMap()
                ));
            } else {
                String joined = String.join("&7, &f", keys);
                sender.sendMessage(msg(
                        "music.tracks-list",
                        "&7Tracks: &f%tracks%",
                        java.util.Map.of("%tracks%", joined)
                ));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Target selection logic
        Player explicitTarget = null;
        boolean selfOnly = false;

        if (args.length >= 2) {
            String targetArg = args[1];
            if ("self".equalsIgnoreCase(targetArg)) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(msg(
                            "music.self-only-player",
                            "&cOnly a player can use &fself&c.",
                            java.util.Collections.emptyMap()
                    ));
                    return true;
                }
                explicitTarget = (Player) sender;
                selfOnly = true;
            } else {
                // a player name was given
                if (!sender.hasPermission(PERM_OTHERS)) {
                    sender.sendMessage(msg(
                            "music.no-permission-others",
                            "&cYou don't have permission to target other players.",
                            java.util.Collections.emptyMap()
                    ));
                    return true;
                }
                explicitTarget = Bukkit.getPlayerExact(targetArg);
                if (explicitTarget == null) {
                    sender.sendMessage(msg(
                            "music.player-not-found",
                            "&cPlayer not found: &f%player%",
                            java.util.Map.of("%player%", targetArg)
                    ));
                    return true;
                }
            }
        }

        // Handle subcommands
        switch (sub) {
            case "list": {
                List<String> keys = music.getKeys();
                if (keys.isEmpty()) {
                    sender.sendMessage(msg(
                            "music.no-tracks-short",
                            "&7No available tracks (&fmusic.yml&7).",
                            java.util.Collections.emptyMap()
                    ));
                } else {
                    String joined = String.join("&7, &f", keys);
                    sender.sendMessage(msg(
                            "music.tracks-list",
                            "&7Tracks: &f%tracks%",
                            java.util.Map.of("%tracks%", joined)
                    ));
                }
                return true;
            }
            case "stop": {
                if (!sender.hasPermission(PERM_STOP)) {
                    sender.sendMessage(msg(
                            "music.no-permission-stop",
                            "&cMissing permission: &f%perm%",
                            java.util.Map.of("%perm%", PERM_STOP)
                    ));
                    return true;
                }
                // stop for self or a specific player or globally if console/no args
                if (explicitTarget != null) {
                    String m = music.stopFor(explicitTarget);
                    sender.sendMessage(Msg.color(m)); // result already a message key
                } else if (sender instanceof Player p) {
                    String m = music.stopFor(p);
                    sender.sendMessage(Msg.color(m));
                } else {
                    // console: stop for all
                    int count = music.stopAll();
                    sender.sendMessage(msg(
                            "music.stop-all",
                            "&aStopped music for &f%count% &aplayer(s).",
                            java.util.Map.of("%count%", String.valueOf(count))
                    ));
                }
                return true;
            }
            default:
                // Play a track key
                break;
        }

        // From here, sub is considered a track key
        String trackKey = sub;

        // Resolve default target (if none specified)
        Player target = explicitTarget;
        if (target == null && sender instanceof Player p) {
            // default to sender
            target = p;
            selfOnly = true;
        }

        // If still null → console must specify a player
        if (target == null) {
            sender.sendMessage(msg(
                    "music.console-missing-player",
                    "&cFrom console, please specify &f/%label% %track% <player>&c.",
                    java.util.Map.of(
                            "%label%", label,
                            "%track%", trackKey
                    )
            ));
            return true;
        }

        d("Invoked by=" + sender.getName()
                + " trackKey=" + trackKey
                + " target=" + target.getName()
                + " selfOnly=" + selfOnly
                + " nbapiAvailable=" + music.isNoteBlockAvailable());

        String result = music.playTrack(trackKey, selfOnly ? target : null);
        sender.sendMessage(Msg.color(result));
        return true;
    }

    // ---------- TAB COMPLETE ----------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String alias,
                                      @NotNull String[] args) {

        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("list");
            base.add("stop");
            base.addAll(music.getKeys());
            String cur = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : base) {
                if (s.toLowerCase(Locale.ROOT).startsWith(cur)) out.add(s);
            }
            Collections.sort(out);
            return out;
        }

        if (args.length == 2) {
            // Suggest "self" and online players
            String cur = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("self".startsWith(cur)) out.add("self");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(cur)) out.add(p.getName());
            }
            Collections.sort(out);
            return out;
        }

        return Collections.emptyList();
    }
}
