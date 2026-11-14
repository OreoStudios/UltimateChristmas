package fr.elias.ultimateChristmas.core.reward;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Hands a {@link Reward} to a player.
 *
 * <p>Everything a reward can do is applied here, in one place, on the main
 * thread. Items that will not fit are dropped at the player's feet rather than
 * vanishing — the 3.x code called {@code give} through the console and silently
 * lost prizes when an inventory was full.
 */
public final class RewardService {

    private final UltimateChristmas plugin;

    public RewardService(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public void grant(Player player, Reward reward) {
        grant(player, reward, Map.of());
    }

    /**
     * Applies every part of {@code reward} to {@code player}.
     *
     * @param extraPlaceholders merged into the standard set for this grant
     */
    public void grant(Player player, Reward reward, Map<String, String> extraPlaceholders) {
        if (reward == null || player == null || !player.isOnline()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        placeholders.put("player", player.getName());
        placeholders.put("reward", reward.id());

        PlayerProfile profile = plugin.profiles().get(player);

        long shards = reward.shards().roll();
        if (shards != 0) {
            plugin.economy().give(profile, shards);
            placeholders.put("shards", String.valueOf(shards));
        }
        placeholders.putIfAbsent("shards", "0");
        placeholders.put("balance", String.valueOf(profile.shards()));

        long xp = reward.xp().roll();
        if (xp > 0) {
            player.giveExp((int) Math.min(Integer.MAX_VALUE, xp));
            placeholders.put("xp", String.valueOf(xp));
        }

        long passXp = reward.passXp().roll();
        if (passXp > 0) {
            profile.addPassXp(passXp);
            placeholders.put("pass_xp", String.valueOf(passXp));
        }

        if (reward.naughtyNice() != 0) {
            profile.addNaughtyNice(reward.naughtyNice());
        }

        giveItems(player, reward);
        runCommands(player, reward, placeholders);

        for (PotionEffect effect : reward.effects()) {
            player.addPotionEffect(effect);
        }

        for (String message : reward.messages()) {
            plugin.messages().sendRaw(player, message, placeholders);
        }
        for (String broadcast : reward.broadcasts()) {
            Bukkit.broadcast(Text.parse(broadcast, placeholders));
        }

        if (reward.title() != null || reward.subtitle() != null) {
            Fx.title(player, reward.title(), reward.subtitle(), placeholders);
        }
        if (reward.sound() != null) {
            player.playSound(player.getLocation(), reward.sound(),
                    reward.soundVolume(), reward.soundPitch());
        }
        if (reward.particle() != null) {
            Fx.burst(player.getLocation().add(0, 1, 0), reward.particle(), 24, 0.4);
        }
        if (reward.firework()) {
            Fx.festiveFirework(player.getLocation());
        }
    }

    /** Adds items to the inventory, dropping any that will not fit. */
    private void giveItems(Player player, Reward reward) {
        if (reward.items().isEmpty()) {
            return;
        }
        ItemStack[] copies = new ItemStack[reward.items().size()];
        for (int index = 0; index < copies.length; index++) {
            copies[index] = reward.items().get(index).clone();
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(copies);
        if (leftovers.isEmpty()) {
            return;
        }
        Location at = player.getLocation();
        for (ItemStack leftover : leftovers.values()) {
            at.getWorld().dropItemNaturally(at, leftover);
        }
        plugin.messages().send(player, "reward.inventory-full");
    }

    private void runCommands(Player player, Reward reward, Map<String, String> placeholders) {
        for (String raw : reward.consoleCommands()) {
            dispatch(Bukkit.getConsoleSender(), raw, placeholders, player);
        }
        for (String raw : reward.playerCommands()) {
            dispatch(player, raw, placeholders, player);
        }
    }

    private void dispatch(org.bukkit.command.CommandSender as, String raw,
                          Map<String, String> placeholders, Player player) {
        String command = Text.plain(raw, placeholders);
        // Placeholders are substituted as plain text, so a command never carries
        // leftover colour codes into the dispatcher.
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isBlank()) {
            return;
        }
        try {
            Bukkit.dispatchCommand(as, command);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Reward command failed: " + command, ex);
        }
    }

    /**
     * Rolls a table and grants the result, returning the reward that was given.
     *
     * @return the granted reward, or {@code null} if the table was empty
     */
    public Reward rollAndGrant(Player player, RewardTable table, Map<String, String> placeholders) {
        Reward reward = table.roll();
        if (reward == null) {
            plugin.getLogger().warning("Reward table '" + table.path()
                    + "' has nothing to roll; check its weights.");
            plugin.messages().send(player, "reward.none-configured");
            return null;
        }
        grant(player, reward, placeholders);
        return reward;
    }

    public Reward rollAndGrant(Player player, RewardTable table) {
        return rollAndGrant(player, table, Map.of());
    }
}
