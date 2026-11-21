package fr.elias.ultimateChristmas;

import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleBoundary;
import dev.oreo.modulith.core.ModuleDependency;
import dev.oreo.modulith.core.ModuleDescriptor;
import dev.oreo.modulith.core.ModuleRuntime;
import fr.elias.ultimateChristmas.modules.admin.lifecycle.AdminModule;
import fr.elias.ultimateChristmas.modules.advent.lifecycle.AdventModule;
import fr.elias.ultimateChristmas.modules.ambience.lifecycle.AmbienceModule;
import fr.elias.ultimateChristmas.modules.bosses.lifecycle.BossModule;
import fr.elias.ultimateChristmas.modules.cosmetics.lifecycle.CosmeticsModule;
import fr.elias.ultimateChristmas.modules.countdown.lifecycle.CountdownModule;
import fr.elias.ultimateChristmas.modules.economy.lifecycle.EconomyModule;
import fr.elias.ultimateChristmas.modules.events.lifecycle.EventModule;
import fr.elias.ultimateChristmas.modules.feasts.lifecycle.FeastModule;
import fr.elias.ultimateChristmas.modules.leaderboard.lifecycle.LeaderboardModule;
import fr.elias.ultimateChristmas.modules.music.lifecycle.MusicModule;
import fr.elias.ultimateChristmas.modules.pass.lifecycle.PassModule;
import fr.elias.ultimateChristmas.modules.platform.lifecycle.PlatformModule;
import fr.elias.ultimateChristmas.modules.presents.lifecycle.PresentHuntModule;
import fr.elias.ultimateChristmas.modules.quests.lifecycle.QuestModule;
import fr.elias.ultimateChristmas.modules.santa.lifecycle.SantaModule;
import fr.elias.ultimateChristmas.modules.secretsanta.lifecycle.SecretSantaModule;
import fr.elias.ultimateChristmas.modules.shop.lifecycle.ShopModule;
import fr.elias.ultimateChristmas.modules.snow.lifecycle.SnowModule;
import fr.elias.ultimateChristmas.modules.snowball.lifecycle.SnowballModule;
import fr.elias.ultimateChristmas.modules.tree.lifecycle.TreeModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the module architecture itself.
 *
 * <p>These checks run without a server, because they only read annotations and
 * package declarations. They catch the mistakes that are easy to make and
 * tedious to debug at runtime: a module whose {@code @PluginModule} id does not
 * match its package's {@code @ApplicationModule} id (which silently removes its
 * boundary), a dependency on a module that does not exist, or two modules
 * claiming the same id.
 */
class ModuleArchitectureTest {

    /** Every module registered by {@link fr.elias.ultimateChristmas.modules.ModuleWiring}. */
    private static final List<Class<? extends MinecraftModule>> MODULES = List.of(
            PlatformModule.class, EconomyModule.class, ShopModule.class,
            AdventModule.class, SantaModule.class, BossModule.class,
            SnowModule.class, SnowballModule.class, PresentHuntModule.class,
            MusicModule.class, QuestModule.class, PassModule.class,
            CosmeticsModule.class, FeastModule.class, TreeModule.class,
            SecretSantaModule.class, CountdownModule.class,
            LeaderboardModule.class, AmbienceModule.class, EventModule.class,
            AdminModule.class);

    private static List<ModuleDescriptor> describeAll() {
        List<ModuleDescriptor> descriptors = new ArrayList<>();
        for (Class<? extends MinecraftModule> type : MODULES) {
            descriptors.add(ModuleRuntime.Builder.describe(type));
        }
        return descriptors;
    }

    @Test
    void everyModuleIdIsUnique() {
        Set<String> seen = new HashSet<>();
        for (ModuleDescriptor descriptor : describeAll()) {
            assertTrue(seen.add(descriptor.id()),
                    "Two modules both claim the id '" + descriptor.id() + "'");
        }
        assertEquals(MODULES.size(), seen.size());
    }

    /**
     * A {@code @PluginModule} class only gets a boundary if some enclosing
     * package declares {@code @ApplicationModule} with the same id. Get that
     * wrong and the module still runs, but its internals become visible to
     * everything, which is exactly the problem the layering is meant to solve.
     */
    @Test
    void everyModuleDeclaresAMatchingPackageBoundary() {
        for (Class<? extends MinecraftModule> type : MODULES) {
            var declaration = ModuleBoundary.declaration(type);
            assertTrue(declaration.isPresent(),
                    type.getSimpleName() + " has no matching @ApplicationModule package "
                            + "declaration. Check that modules/<name>/package-info.java "
                            + "declares the same id as its @PluginModule value.");
        }
    }

    /** The api/infra/lifecycle split is the convention; keep classes on the right side. */
    @Test
    void moduleClassesLiveInALifecyclePackage() {
        for (Class<? extends MinecraftModule> type : MODULES) {
            assertTrue(type.getPackageName().endsWith(".lifecycle"),
                    type.getName() + " should live in its module's lifecycle package");
        }
    }

    @Test
    void everyDependencyNamesAKnownModule() {
        List<ModuleDescriptor> descriptors = describeAll();
        Set<String> ids = new HashSet<>();
        descriptors.forEach(descriptor -> ids.add(descriptor.id()));

        for (ModuleDescriptor descriptor : descriptors) {
            List<String> selectors = new ArrayList<>(descriptor.dependencies());
            selectors.addAll(descriptor.uses());
            selectors.addAll(descriptor.after());
            for (String selector : selectors) {
                String target = ModuleDependency.parse(selector).moduleId();
                assertTrue(ids.contains(target),
                        descriptor.id() + " depends on unknown module '" + target
                                + "' (selector '" + selector + "')");
            }
        }
    }

    /**
     * The platform publishes shared infrastructure, so it must come up first and
     * must not wait on a feature module.
     */
    @Test
    void platformHasNoDependencies() {
        ModuleDescriptor platform = ModuleRuntime.Builder.describe(PlatformModule.class);
        assertEquals("platform", platform.id());
        assertTrue(platform.dependencies().isEmpty(),
                "platform must not depend on any other module");
        assertTrue(platform.uses().isEmpty());
    }

    @Test
    void everyFeatureModuleDependsOnThePlatform() {
        for (Class<? extends MinecraftModule> type : MODULES) {
            if (type == PlatformModule.class) {
                continue;
            }
            ModuleDescriptor descriptor = ModuleRuntime.Builder.describe(type);
            boolean usesPlatform = descriptor.dependencies().stream()
                    .map(selector -> ModuleDependency.parse(selector).moduleId())
                    .anyMatch("platform"::equals);
            assertTrue(usesPlatform,
                    descriptor.id() + " must declare dependencies = \"platform::platform\"");
        }
    }

    /**
     * Per-module config reload only works for modules that opted in, and every
     * feature here has settings worth reloading.
     */
    @Test
    void everyFeatureModuleEnablesConfiguration() {
        for (Class<? extends MinecraftModule> type : MODULES) {
            if (type == PlatformModule.class) {
                continue;
            }
            ModuleDescriptor descriptor = ModuleRuntime.Builder.describe(type);
            assertTrue(descriptor.configurationEnabled(),
                    descriptor.id() + " should set configuration = true so "
                            + "/christmas module reload " + descriptor.id() + " works");
        }
    }

    @Test
    void noModuleIsMarkedOptional() {
        for (ModuleDescriptor descriptor : describeAll()) {
            // Optional modules are skipped silently when dependencies are
            // missing; everything here should either start or report a failure.
            assertFalse(descriptor.optional(),
                    descriptor.id() + " should not be optional");
        }
    }
}
