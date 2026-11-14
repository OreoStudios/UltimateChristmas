package fr.elias.ultimateChristmas.modules.economy.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.modules.economy.api.EarningService;
import fr.elias.ultimateChristmas.modules.economy.infra.PayCommand;
import fr.elias.ultimateChristmas.modules.economy.infra.ShardAdminCommand;
import fr.elias.ultimateChristmas.modules.economy.infra.ShardEarningListener;
import fr.elias.ultimateChristmas.modules.economy.infra.ShardSources;
import fr.elias.ultimateChristmas.modules.economy.infra.ShardsCommand;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Material;

/**
 * The shard currency: how it is earned, and the commands around it.
 *
 * <p>Reloadable in place. The earning rules and the parameter record are both
 * replaced on reload, and the listener reads them through a supplier, so
 * {@code /christmas module reload economy} picks up new rules without
 * re-registering anything.
 */
@PluginModule(value = "economy", dependencies = "platform::platform", configuration = true)
public final class EconomyModule extends FeatureModule implements EarningService {

    private final ShardSources sources;
    private volatile EconomyParameters parameters;

    public EconomyModule(ChristmasPlatform platform, EconomyParameters parameters) {
        super(platform);
        this.parameters = parameters;
        this.sources = new ShardSources(platform.plugin().getLogger());
    }

    @Override
    protected void onEnable() {
        sources.load(section("earning"));

        listen(new ShardEarningListener(platform, sources, () -> parameters, this::emit));

        command(new ShardsCommand(platform));
        command(new ShardAdminCommand(platform));
        command(new PayCommand(platform, () -> parameters));

        publish(EarningService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = EconomyParameters.from(config(), EconomyParameters.DEFAULTS);
        sources.load(section("earning"));
    }

    // ------------------------------------------------------------ public API

    @Override
    public boolean paysForBlock(Material material) {
        return sources.paysForBlock(material);
    }

    @Override
    public int sourceCount() {
        return sources.total();
    }
}
