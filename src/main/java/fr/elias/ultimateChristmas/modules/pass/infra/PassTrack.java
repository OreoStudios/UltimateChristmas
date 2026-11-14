package fr.elias.ultimateChristmas.modules.pass.infra;

import fr.elias.ultimateChristmas.core.reward.Reward;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The pass tiers: how much experience each costs and what it pays out.
 *
 * <p>Each tier can carry a free reward and a premium one. A server that does
 * not sell a premium pass simply configures only the free side, and nothing
 * else changes.
 */
public final class PassTrack {

    /**
     * One tier.
     *
     * @param tier    1-based tier number
     * @param xp      total experience needed to reach it
     * @param free    reward for everyone, or {@code null}
     * @param premium extra reward for premium holders, or {@code null}
     */
    public record Tier(int tier, long xp, Reward free, Reward premium) {
    }

    private final Logger logger;
    private final List<Tier> tiers = new ArrayList<>();

    public PassTrack(Logger logger) {
        this.logger = logger;
    }

    /**
     * Rebuilds the track from the {@code tiers} section.
     *
     * <p>Tiers are sorted by their experience requirement, so an owner can add
     * one in the middle of the file without renumbering everything.
     */
    public void load(ConfigurationSection section) {
        tiers.clear();
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                logger.warning("pass.yml: tier key '" + key + "' is not a number; skipping.");
                continue;
            }
            long xp = child.getLong("xp", -1);
            if (xp < 0) {
                logger.warning("pass.yml: tier " + key + " has no 'xp' requirement; skipping.");
                continue;
            }
            tiers.add(new Tier(number, xp,
                    readReward(child.getConfigurationSection("free")),
                    readReward(child.getConfigurationSection("premium"))));
        }
        tiers.sort(java.util.Comparator.comparingLong(Tier::xp));
        logger.info("Festive pass: " + tiers.size() + " tier(s).");
    }

    private Reward readReward(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Reward reward = Reward.read(section, logger);
        return reward.isEmpty() ? null : reward;
    }

    /** The highest tier reached at a given amount of experience. */
    public int tierFor(long xp) {
        int reached = 0;
        for (Tier tier : tiers) {
            if (xp >= tier.xp()) {
                reached = Math.max(reached, tier.tier());
            }
        }
        return reached;
    }

    public Tier tier(int number) {
        for (Tier tier : tiers) {
            if (tier.tier() == number) {
                return tier;
            }
        }
        return null;
    }

    public List<Tier> all() {
        return List.copyOf(tiers);
    }

    public int maxTier() {
        int highest = 0;
        for (Tier tier : tiers) {
            highest = Math.max(highest, tier.tier());
        }
        return highest;
    }

    /** Experience needed for the next tier after {@code xp}, or -1 at the top. */
    public long nextRequirement(long xp) {
        long best = -1;
        for (Tier tier : tiers) {
            if (tier.xp() > xp && (best < 0 || tier.xp() < best)) {
                best = tier.xp();
            }
        }
        return best;
    }

    public boolean isEmpty() {
        return tiers.isEmpty();
    }
}
