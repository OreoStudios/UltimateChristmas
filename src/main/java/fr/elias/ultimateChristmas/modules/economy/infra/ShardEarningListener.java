package fr.elias.ultimateChristmas.modules.economy.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.economy.api.ShardEarnedEvent;
import fr.elias.ultimateChristmas.modules.economy.lifecycle.EconomyParameters;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Grants shards for breaking blocks and killing mobs.
 *
 * <p>Blocks a player placed themselves are ignored by default, so an ore block
 * cannot be placed and re-broken for endless shards. That exploit was wide open
 * in 3.x, which paid out on any matching block break.
 */
public final class ShardEarningListener implements Listener {

    private static final String PLACED_MARKER = "uc_player_placed";

    private final ChristmasPlatform platform;
    private final ShardSources sources;
    private final Supplier<EconomyParameters> parameters;
    private final Consumer<Object> emit;

    public ShardEarningListener(ChristmasPlatform platform, ShardSources sources,
                                Supplier<EconomyParameters> parameters, Consumer<Object> emit) {
        this.platform = platform;
        this.sources = sources;
        this.parameters = parameters;
        this.emit = emit;
    }

    /**
     * Marks blocks as player-placed.
     *
     * <p>Block metadata is keyed to the block's position and is dropped when
     * the chunk unloads, which is exactly the lifetime wanted: it stops the
     * immediate place-and-break loop without needing a record of every block
     * ever placed on the server.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!parameters.get().ignorePlayerPlaced()) {
            return;
        }
        if (sources.forBlock(event.getBlock().getType()) != null) {
            event.getBlock().setMetadata(PLACED_MARKER,
                    new FixedMetadataValue(platform.plugin(), true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE
                || !player.hasPermission("ultimatechristmas.earn")) {
            return;
        }

        Block block = event.getBlock();
        if (parameters.get().ignorePlayerPlaced() && block.hasMetadata(PLACED_MARKER)) {
            block.removeMetadata(PLACED_MARKER, platform.plugin());
            return;
        }

        ShardSources.Source source = null;
        String label = null;
        if (sources.hasCustomBlocks() && platform.hooks().itemsAdder().active()) {
            String customId = platform.hooks().itemsAdder().blockIdAt(block);
            source = sources.forCustomBlock(customId);
            label = source == null ? null : "block:" + customId;
        }
        if (source == null) {
            source = sources.forBlock(block.getType());
            label = "block:" + block.getType().name();
        }
        award(player, source, label);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !killer.hasPermission("ultimatechristmas.earn")) {
            return;
        }

        ShardSources.Source source = null;
        String label = null;
        if (sources.hasMythicMobs() && platform.hooks().mythicMobs().active()) {
            String internal = platform.hooks().mythicMobs().mobTypeOf(event.getEntity());
            source = sources.forMythicMob(internal);
            label = source == null ? null : "mythic:" + internal;
        }
        if (source == null) {
            source = sources.forMob(event.getEntityType());
            label = "mob:" + event.getEntityType().name();
        }
        award(killer, source, label);
    }

    private void award(Player player, ShardSources.Source source, String label) {
        if (source == null) {
            return;
        }
        long earned = source.roll();
        if (earned <= 0) {
            return;
        }
        platform.economy().give(player, earned);
        long balance = platform.economy().balance(player);
        emit.accept(new ShardEarnedEvent(player.getUniqueId(), earned, label, balance));

        EconomyParameters settings = parameters.get();
        if (settings.announceEarnings() && earned >= settings.announceThreshold()) {
            platform.messages().actionBar(player, "economy.earned", Messages.of(
                    "amount", EconomyService.format(earned),
                    "total", EconomyService.format(balance),
                    "currency", platform.economy().currencyName(earned)));
        }
    }
}
