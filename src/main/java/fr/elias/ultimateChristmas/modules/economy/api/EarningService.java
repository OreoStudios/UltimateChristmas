package fr.elias.ultimateChristmas.modules.economy.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.Material;

/**
 * Read-only questions about the shard earning rules.
 *
 * <p>Balances themselves are not here: those belong to the platform's economy
 * service, which every module already has. This contract is only about what the
 * economy module privately knows, namely which sources pay out.
 */
@ModuleApi("economy")
public interface EarningService {

    /** Whether breaking this block can award shards. */
    boolean paysForBlock(Material material);

    /** How many distinct earning sources are configured. */
    int sourceCount();
}
