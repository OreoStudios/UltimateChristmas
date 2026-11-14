package fr.elias.ultimateChristmas.modules.admin.infra;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /christmas reload} — re-reads global config and every module's config.
 *
 * <p>This is a configuration refresh, not a restart: listeners and tasks stay
 * registered. To replace those for one feature, use
 * {@code /christmas module restart <id>}.
 *
 * <p>The 3.x reload only re-read YAML. Listeners stayed registered from the
 * previous load, so reloading twice meant rewards fired twice.
 */
public final class ReloadCommand implements SubCommand {

    private final ChristmasPlatform platform;
    private final UltimateChristmas plugin;

    public ReloadCommand(ChristmasPlatform platform, UltimateChristmas plugin) {
        this.platform = platform;
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "ultimatechristmas.admin.reload";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public String description() {
        return "Reload every config, keeping modules running";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        long startedAt = System.currentTimeMillis();
        plugin.reloadEverything();
        platform.messages().send(sender, "admin.reloaded", Messages.of(
                "ms", String.valueOf(System.currentTimeMillis() - startedAt)));
    }
}
