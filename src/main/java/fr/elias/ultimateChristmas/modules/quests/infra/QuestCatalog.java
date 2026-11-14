package fr.elias.ultimateChristmas.modules.quests.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.reward.Reward;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The festive objectives a server has configured.
 *
 * <p>An objective is a stat key plus a target. Because progress is recorded
 * centrally by {@code ProgressService}, a new objective needs no code: "open 5
 * presents" works because the present module already records
 * {@code presents_found}.
 */
public final class QuestCatalog {

    /** How often an objective resets. */
    public enum Scope {
        DAILY,
        WEEKLY,
        SEASON;

        static Scope parse(String raw, Logger logger, String path) {
            if (raw == null) {
                return DAILY;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                logger.warning("quests.yml: unknown scope '" + raw + "' at " + path
                        + "; using daily.");
                return DAILY;
            }
        }
    }

    /**
     * One objective.
     *
     * @param id      config key
     * @param name    shown to players
     * @param stat    the tracked stat key, e.g. {@code presents_found}
     * @param goal    how much of it is needed
     * @param scope   when it resets
     * @param icon    menu icon
     * @param reward  granted on completion
     */
    public record Quest(String id, String name, String stat, long goal, Scope scope,
                        Material icon, Reward reward) {
    }

    private final Logger logger;
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    public QuestCatalog(Logger logger) {
        this.logger = logger;
    }

    public void load(ConfigurationSection section) {
        quests.clear();
        for (String id : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(id);
            if (child == null) {
                continue;
            }
            String stat = child.getString("stat");
            if (stat == null || stat.isBlank()) {
                logger.warning("quests.yml: '" + id + "' has no 'stat' to track; skipping.");
                continue;
            }
            long goal = child.getLong("goal", 1);
            if (goal <= 0) {
                logger.warning("quests.yml: '" + id + "' has goal " + goal
                        + ", which can never be completed; skipping.");
                continue;
            }
            ConfigurationSection rewardSection = child.getConfigurationSection("reward");
            Reward reward = rewardSection == null ? null : Reward.read(rewardSection, logger);
            if (reward == null || reward.isEmpty()) {
                logger.warning("quests.yml: '" + id + "' has no reward; it will still "
                        + "track but gives nothing.");
            }
            quests.put(id, new Quest(
                    id,
                    child.getString("name", id),
                    stat.toLowerCase(Locale.ROOT),
                    goal,
                    Scope.parse(child.getString("scope"), logger, id),
                    ItemBuilder.material(child.getString("icon"), Material.PAPER),
                    reward));
        }
        logger.info("Quests: " + quests.size() + " objective(s).");
    }

    public Quest get(String id) {
        return quests.get(id);
    }

    public java.util.Collection<Quest> all() {
        return quests.values();
    }

    public java.util.Set<String> ids() {
        return quests.keySet();
    }

    /** Objectives that track a given stat. */
    public List<Quest> tracking(String stat) {
        List<Quest> matches = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.stat().equals(stat)) {
                matches.add(quest);
            }
        }
        return matches;
    }

    public List<Quest> inScope(Scope scope) {
        List<Quest> matches = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.scope() == scope) {
                matches.add(quest);
            }
        }
        return matches;
    }

    public boolean isEmpty() {
        return quests.isEmpty();
    }
}
