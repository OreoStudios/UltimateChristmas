package fr.elias.ultimateChristmas.core.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * A weighted set of {@link Reward}s, with one roll picking one prize.
 *
 * <p>Weights are relative, not percentages, so an owner can add an entry without
 * rebalancing every other number. A reward with weight 0 is kept in the table —
 * so it can still be granted by id, e.g. by a fixed advent day — but never
 * rolled at random.
 */
public final class RewardTable {

    private final String path;
    private final Map<String, Reward> byId;
    private final List<Reward> rollable;
    private final int totalWeight;

    private RewardTable(String path, Map<String, Reward> byId) {
        this.path = path;
        this.byId = Map.copyOf(byId);
        List<Reward> pool = new ArrayList<>();
        int sum = 0;
        for (Reward reward : byId.values()) {
            if (reward.weight() > 0) {
                pool.add(reward);
                sum += reward.weight();
            }
        }
        this.rollable = List.copyOf(pool);
        this.totalWeight = sum;
    }

    /**
     * Reads every child section as a reward.
     *
     * @param section the parent section, or {@code null} for an empty table
     */
    public static RewardTable read(ConfigurationSection section, Logger logger) {
        Map<String, Reward> rewards = new LinkedHashMap<>();
        if (section == null) {
            return new RewardTable("<none>", rewards);
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            Reward reward = Reward.read(child, logger);
            if (reward.isEmpty() && logger != null) {
                logger.warning("Reward '" + child.getCurrentPath()
                        + "' grants nothing; check its keys for a typo.");
            }
            rewards.put(key, reward);
        }
        return new RewardTable(section.getCurrentPath(), rewards);
    }

    /** Picks one reward by weight, or {@code null} if the table cannot be rolled. */
    public Reward roll() {
        if (rollable.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int target = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (Reward reward : rollable) {
            cumulative += reward.weight();
            if (target < cumulative) {
                return reward;
            }
        }
        return rollable.get(rollable.size() - 1);
    }

    /** A specific reward by its config key, or {@code null}. */
    public Reward byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public java.util.Collection<Reward> all() {
        return byId.values();
    }

    public java.util.Set<String> ids() {
        return byId.keySet();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public int size() {
        return byId.size();
    }

    /** The chance of a given reward as a percentage, for GUI previews. */
    public double chanceOf(Reward reward) {
        if (totalWeight <= 0 || reward.weight() <= 0) {
            return 0;
        }
        return reward.weight() * 100.0 / totalWeight;
    }

    public String path() {
        return path;
    }
}
