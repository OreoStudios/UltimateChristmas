package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.entity.Entity;

import java.util.Locale;

/**
 * Applies a custom Blockbench model to an entity via ModelEngine or BetterModel.
 *
 * <p>Both plugins render a {@code .bbmodel} on top of a vanilla mob. Neither is
 * required: a boss with no model, or whose model plugin is absent, simply keeps
 * its vanilla appearance, so the feature degrades cleanly.
 *
 * <p>Reached by reflection on purpose. Neither plugin publishes to a stable
 * public Maven repo this project could depend on, the surface used here is one
 * call, and the two APIs differ — so a compile dependency would tie builds to
 * their availability for no real benefit. Each attempt is wrapped so a changed
 * or missing method logs once and falls back rather than breaking a spawn.
 */
public final class ModelHook {

    /** Which renderer a model should use. */
    public enum Provider {
        /** Prefer our built-in display renderer, then ModelEngine, then BetterModel. */
        AUTO,
        /** The plugin's own animated display-entity renderer (no plugin needed). */
        DISPLAY,
        MODEL_ENGINE,
        BETTER_MODEL,
        NONE;

        public static Provider parse(String raw) {
            if (raw == null) {
                return AUTO;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
                case "display", "builtin", "internal", "native" -> DISPLAY;
                case "modelengine", "me" -> MODEL_ENGINE;
                case "bettermodel", "bm" -> BETTER_MODEL;
                case "none", "off", "false", "vanilla" -> NONE;
                default -> AUTO;
            };
        }
    }

    private final UltimateChristmas plugin;
    private boolean modelEngine;
    private boolean betterModel;
    private boolean warnedModelEngine;
    private boolean warnedBetterModel;

    public ModelHook(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    /** Detects which model plugins are installed. Called once at startup. */
    public void detect() {
        modelEngine = Hooks.present("ModelEngine");
        betterModel = Hooks.present("BetterModel");
        if (modelEngine || betterModel) {
            plugin.getLogger().info("Model rendering available via: "
                    + (modelEngine ? "ModelEngine " : "") + (betterModel ? "BetterModel" : ""));
        }
    }

    public boolean anyAvailable() {
        return modelEngine || betterModel;
    }

    public boolean has(Provider provider) {
        return switch (provider) {
            case MODEL_ENGINE -> modelEngine;
            case BETTER_MODEL -> betterModel;
            case AUTO -> modelEngine || betterModel;
            // DISPLAY is the plugin's own renderer, not an external plugin this
            // hook tracks, so this only reports the external ones.
            case DISPLAY, NONE -> false;
        };
    }

    /**
     * Applies {@code modelId} to {@code entity} using the preferred provider.
     *
     * @return the provider that actually rendered it, or {@link Provider#NONE}
     *         if nothing did (the entity keeps its vanilla look)
     */
    public Provider apply(Entity entity, String modelId, Provider preferred) {
        if (modelId == null || modelId.isBlank() || preferred == Provider.NONE) {
            return Provider.NONE;
        }

        // Honour an explicit choice; otherwise try ModelEngine, then BetterModel.
        if (preferred == Provider.MODEL_ENGINE || preferred == Provider.AUTO) {
            if (modelEngine && applyModelEngine(entity, modelId)) {
                return Provider.MODEL_ENGINE;
            }
        }
        if (preferred == Provider.BETTER_MODEL || preferred == Provider.AUTO) {
            if (betterModel && applyBetterModel(entity, modelId)) {
                return Provider.BETTER_MODEL;
            }
        }
        return Provider.NONE;
    }

    // --------------------------------------------------------- ModelEngine R4

    private boolean applyModelEngine(Entity entity, String modelId) {
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");

            // createModeledEntity(Entity) in R4; getOrCreateModeledEntity in some builds.
            Object modeled = invokeFirst(api, new String[]{
                    "createModeledEntity", "getOrCreateModeledEntity"},
                    new Class<?>[]{Entity.class}, null, entity);
            Object activeModel = invokeFirst(api, new String[]{"createActiveModel"},
                    new Class<?>[]{String.class}, null, modelId);
            if (modeled == null || activeModel == null) {
                return warnModelEngine("its API returned no model for '" + modelId + "'");
            }

            // addModel(ActiveModel, boolean) — the boolean asks it to override base.
            for (var method : modeled.getClass().getMethods()) {
                if (method.getName().equals("addModel") && method.getParameterCount() == 2) {
                    method.invoke(modeled, activeModel, true);
                    return true;
                }
            }
            return warnModelEngine("no addModel(model, boolean) method was found");
        } catch (ClassNotFoundException ex) {
            return warnModelEngine("the ModelEngineAPI class is missing (version mismatch?)");
        } catch (Exception | NoClassDefFoundError error) {
            return warnModelEngine(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private boolean warnModelEngine(String why) {
        if (!warnedModelEngine) {
            warnedModelEngine = true;
            plugin.getLogger().warning("Could not apply a ModelEngine model: " + why
                    + ". Bosses will use their vanilla appearance.");
        }
        return false;
    }

    // ----------------------------------------------------------- BetterModel

    private boolean applyBetterModel(Entity entity, String modelId) {
        try {
            // BetterModel 1.x: BetterModel.model(name) -> Optional<ModelRenderer>,
            // then renderer.create(entity) or create(entity, ...) attaches a tracker.
            Class<?> api = Class.forName("kr.toxicity.model.api.BetterModel");
            Object optional = invokeFirst(api, new String[]{"model", "renderer"},
                    new Class<?>[]{String.class}, null, modelId);
            Object renderer = unwrapOptional(optional);
            if (renderer == null) {
                return warnBetterModel("no model named '" + modelId + "' is loaded");
            }
            // create(Entity) returns a tracker; some builds take (Entity, Consumer).
            for (var method : renderer.getClass().getMethods()) {
                if (method.getName().equals("create") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(entity.getClass())) {
                    method.invoke(renderer, entity);
                    return true;
                }
            }
            for (var method : renderer.getClass().getMethods()) {
                if (method.getName().equals("create") && method.getParameterCount() == 1) {
                    method.invoke(renderer, entity);
                    return true;
                }
            }
            return warnBetterModel("no create(entity) method was found on its renderer");
        } catch (ClassNotFoundException ex) {
            return warnBetterModel("the BetterModel API class is missing (version mismatch?)");
        } catch (Exception | NoClassDefFoundError error) {
            return warnBetterModel(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private boolean warnBetterModel(String why) {
        if (!warnedBetterModel) {
            warnedBetterModel = true;
            plugin.getLogger().warning("Could not apply a BetterModel model: " + why
                    + ". Bosses will use their vanilla appearance.");
        }
        return false;
    }

    // --------------------------------------------------------------- helpers

    /** Invokes the first matching static method by name, or returns null. */
    private static Object invokeFirst(Class<?> owner, String[] names, Class<?>[] signature,
                                      Object target, Object... args) throws Exception {
        for (String name : names) {
            try {
                var method = owner.getMethod(name, signature);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
                // Try the next candidate name.
            }
        }
        return null;
    }

    /** Unwraps a {@link java.util.Optional} if that is what was returned. */
    private static Object unwrapOptional(Object value) {
        if (value instanceof java.util.Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }
}
