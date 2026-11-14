package fr.elias.ultimateChristmas.modules;

import dev.oreo.modulith.core.ModuleContext;
import dev.oreo.modulith.paper.PaperModulith;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.modules.admin.lifecycle.AdminModule;
import fr.elias.ultimateChristmas.modules.admin.lifecycle.AdminParameters;
import fr.elias.ultimateChristmas.modules.advent.lifecycle.AdventModule;
import fr.elias.ultimateChristmas.modules.advent.lifecycle.AdventParameters;
import fr.elias.ultimateChristmas.modules.ambience.lifecycle.AmbienceModule;
import fr.elias.ultimateChristmas.modules.ambience.lifecycle.AmbienceParameters;
import fr.elias.ultimateChristmas.modules.bosses.lifecycle.BossModule;
import fr.elias.ultimateChristmas.modules.bosses.lifecycle.BossParameters;
import fr.elias.ultimateChristmas.modules.cosmetics.lifecycle.CosmeticsModule;
import fr.elias.ultimateChristmas.modules.cosmetics.lifecycle.CosmeticsParameters;
import fr.elias.ultimateChristmas.modules.countdown.lifecycle.CountdownModule;
import fr.elias.ultimateChristmas.modules.countdown.lifecycle.CountdownParameters;
import fr.elias.ultimateChristmas.modules.economy.lifecycle.EconomyModule;
import fr.elias.ultimateChristmas.modules.economy.lifecycle.EconomyParameters;
import fr.elias.ultimateChristmas.modules.feasts.lifecycle.FeastModule;
import fr.elias.ultimateChristmas.modules.feasts.lifecycle.FeastParameters;
import fr.elias.ultimateChristmas.modules.leaderboard.lifecycle.LeaderboardModule;
import fr.elias.ultimateChristmas.modules.leaderboard.lifecycle.LeaderboardParameters;
import fr.elias.ultimateChristmas.modules.music.lifecycle.MusicModule;
import fr.elias.ultimateChristmas.modules.music.lifecycle.MusicParameters;
import fr.elias.ultimateChristmas.modules.pass.lifecycle.PassModule;
import fr.elias.ultimateChristmas.modules.pass.lifecycle.PassParameters;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.platform.infra.PlatformServices;
import fr.elias.ultimateChristmas.modules.platform.lifecycle.PlatformModule;
import fr.elias.ultimateChristmas.modules.presents.lifecycle.PresentHuntModule;
import fr.elias.ultimateChristmas.modules.presents.lifecycle.PresentHuntParameters;
import fr.elias.ultimateChristmas.modules.quests.lifecycle.QuestModule;
import fr.elias.ultimateChristmas.modules.quests.lifecycle.QuestParameters;
import fr.elias.ultimateChristmas.modules.santa.lifecycle.SantaModule;
import fr.elias.ultimateChristmas.modules.santa.lifecycle.SantaParameters;
import fr.elias.ultimateChristmas.modules.secretsanta.lifecycle.SecretSantaModule;
import fr.elias.ultimateChristmas.modules.secretsanta.lifecycle.SecretSantaParameters;
import fr.elias.ultimateChristmas.modules.shop.lifecycle.ShopModule;
import fr.elias.ultimateChristmas.modules.shop.lifecycle.ShopParameters;
import fr.elias.ultimateChristmas.modules.snow.lifecycle.SnowModule;
import fr.elias.ultimateChristmas.modules.snow.lifecycle.SnowParameters;
import fr.elias.ultimateChristmas.modules.snowball.lifecycle.SnowballModule;
import fr.elias.ultimateChristmas.modules.snowball.lifecycle.SnowballParameters;
import fr.elias.ultimateChristmas.modules.tree.lifecycle.TreeModule;
import fr.elias.ultimateChristmas.modules.tree.lifecycle.TreeParameters;

/**
 * Binds every feature module to the Modulith runtime through a typed functor.
 *
 * <p>Each binding passes a <em>defaults</em> parameter record. The functor then
 * merges that module's own {@code modules/<id>.yml} over those defaults and
 * hands the resulting immutable record to the module's constructor. The upshot:
 *
 * <ul>
 *   <li>a module never reads raw config in its constructor, so it is trivially
 *       unit-testable by passing a parameter record directly;</li>
 *   <li>bad or missing config is resolved to a documented default in one place
 *       per module, rather than scattered through feature code;</li>
 *   <li>the same module template could be bound twice with different defaults
 *       via {@code bind(instanceId, ...)} if a server ever wanted two of
 *       something.</li>
 * </ul>
 *
 * <p>Modulith works out the start order from each module's declared
 * {@code dependencies}; {@code platform} therefore always comes up first, and
 * nothing else starts if it fails.
 */
public final class ModuleWiring {

    private ModuleWiring() {
    }

    public static PaperModulith start(UltimateChristmas plugin, PlatformServices services) {
        return PaperModulith.builder(plugin)
                // Our own /christmas module command exposes lifecycle control,
                // and Modulith's /modulith is diagnostics-only, so leaving it
                // off keeps one command surface and avoids clashing with any
                // other Modulith plugin on the server.
                .diagnosticsCommand(false)

                // The platform publishes shared infrastructure; everything else
                // declares a dependency on it.
                .functor(PlatformModule.class, services,
                        (platform, context) -> new PlatformModule(platform))

                .functor(EconomyModule.class, EconomyParameters.DEFAULTS,
                        (defaults, context) -> new EconomyModule(platform(context),
                                EconomyParameters.from(context.config(), defaults)))

                .functor(ShopModule.class, ShopParameters.DEFAULTS,
                        (defaults, context) -> new ShopModule(platform(context),
                                ShopParameters.from(context.config(), defaults)))

                .functor(AdventModule.class, AdventParameters.DEFAULTS,
                        (defaults, context) -> new AdventModule(platform(context),
                                AdventParameters.from(context.config(), defaults)))

                .functor(SantaModule.class, SantaParameters.DEFAULTS,
                        (defaults, context) -> new SantaModule(platform(context),
                                SantaParameters.from(context.config(), defaults)))

                .functor(BossModule.class, BossParameters.DEFAULTS,
                        (defaults, context) -> new BossModule(platform(context),
                                BossParameters.from(context.config(), defaults)))

                .functor(SnowModule.class, SnowParameters.DEFAULTS,
                        (defaults, context) -> new SnowModule(platform(context),
                                SnowParameters.from(context.config(), defaults)))

                .functor(SnowballModule.class, SnowballParameters.DEFAULTS,
                        (defaults, context) -> new SnowballModule(platform(context),
                                SnowballParameters.from(context.config(), defaults)))

                .functor(PresentHuntModule.class, PresentHuntParameters.DEFAULTS,
                        (defaults, context) -> new PresentHuntModule(platform(context),
                                PresentHuntParameters.from(context.config(), defaults)))

                .functor(MusicModule.class, MusicParameters.DEFAULTS,
                        (defaults, context) -> new MusicModule(platform(context),
                                MusicParameters.from(context.config(), defaults)))

                .functor(QuestModule.class, QuestParameters.DEFAULTS,
                        (defaults, context) -> new QuestModule(platform(context),
                                QuestParameters.from(context.config(), defaults)))

                .functor(PassModule.class, PassParameters.DEFAULTS,
                        (defaults, context) -> new PassModule(platform(context),
                                PassParameters.from(context.config(), defaults)))

                .functor(CosmeticsModule.class, CosmeticsParameters.DEFAULTS,
                        (defaults, context) -> new CosmeticsModule(platform(context),
                                CosmeticsParameters.from(context.config(), defaults)))

                .functor(FeastModule.class, FeastParameters.DEFAULTS,
                        (defaults, context) -> new FeastModule(platform(context),
                                FeastParameters.from(context.config(), defaults)))

                .functor(TreeModule.class, TreeParameters.DEFAULTS,
                        (defaults, context) -> new TreeModule(platform(context),
                                TreeParameters.from(context.config(), defaults)))

                .functor(SecretSantaModule.class, SecretSantaParameters.DEFAULTS,
                        (defaults, context) -> new SecretSantaModule(platform(context),
                                SecretSantaParameters.from(context.config(), defaults)))

                .functor(CountdownModule.class, CountdownParameters.DEFAULTS,
                        (defaults, context) -> new CountdownModule(platform(context),
                                CountdownParameters.from(context.config(), defaults)))

                .functor(LeaderboardModule.class, LeaderboardParameters.DEFAULTS,
                        (defaults, context) -> new LeaderboardModule(platform(context),
                                LeaderboardParameters.from(context.config(), defaults)))

                .functor(AmbienceModule.class, AmbienceParameters.DEFAULTS,
                        (defaults, context) -> new AmbienceModule(platform(context),
                                AmbienceParameters.from(context.config(), defaults)))

                .functor(AdminModule.class, AdminParameters.DEFAULTS,
                        (defaults, context) -> new AdminModule(platform(context),
                                AdminParameters.from(context.config(), defaults), plugin))

                .start();
    }

    /**
     * Resolves the platform contract for a module being constructed.
     *
     * <p>Safe because every feature module declares
     * {@code dependencies = "platform::platform"}, so Modulith has already
     * started the platform and granted this module access to it.
     */
    private static ChristmasPlatform platform(ModuleContext context) {
        return context.services().require(ChristmasPlatform.class);
    }
}
