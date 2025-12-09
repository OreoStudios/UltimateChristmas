package fr.elias.ultimateChristmas.commands;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

// Example /uc command
public class UltimateChristmasCommand implements CommandExecutor {

    private final UltimateChristmas plugin;

    public UltimateChristmasCommand(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command cmd,
                             String label,
                             String[] args) {

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§e/uc reload");
            return true;
        }

        if (!sender.hasPermission("ultimatechristmas.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        plugin.reloadAll();  // <--- IMPORTANT
        sender.sendMessage("§aUltimateChristmas configs reloaded.");
        return true;
    }
}
