package fr.elias.ultimateChristmas.modules.quests.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for festive objectives, built by its functor.
 *
 * @param announceCompletions broadcast when someone finishes an objective
 */
public record QuestParameters(boolean announceCompletions) {

    public static final QuestParameters DEFAULTS = new QuestParameters(false);

    public static QuestParameters from(ModuleConfiguration config, QuestParameters defaults) {
        return new QuestParameters(
                config.getBoolean("announce-completions", defaults.announceCompletions()));
    }
}
