package fr.elias.ultimateChristmas.modules.advent.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.reward.Reward;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.advent.api.AdventClaimedEvent;
import fr.elias.ultimateChristmas.modules.advent.lifecycle.AdventParameters;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The claim rules behind the advent calendar.
 *
 * <p>Each door maps to a reward, either explicitly in config or by rolling the
 * module's reward table. A player may open today's door, and — if catch-up is
 * enabled — buy back a door they missed earlier in the season. Doors in the
 * future are never claimable.
 *
 * <p>3.x stored only {@code last_claim_epoch_day} per player, so a calendar
 * could show at most one opened door and a missed day was gone forever. Doors
 * are now tracked as a set on the profile, which is what makes both an accurate
 * calendar and catch-up possible.
 */
public final class AdventCalendar {

    /** Why a claim was refused, or that it succeeded. */
    public enum Outcome {
        CLAIMED,
        ALREADY_OPEN,
        NOT_YET,
        OUTSIDE_SEASON,
        NO_REWARD,
        CATCH_UP_DISABLED,
        CANNOT_AFFORD
    }

    private final ChristmasPlatform platform;
    private final Consumer<Object> emit;

    private RewardTable rewards = RewardTable.read(null, null);
    private final Map<Integer, String> doorRewards = new HashMap<>();
    private volatile AdventParameters parameters;

    public AdventCalendar(ChristmasPlatform platform, AdventParameters parameters,
                          Consumer<Object> emit) {
        this.platform = platform;
        this.parameters = parameters;
        this.emit = emit;
    }

    /**
     * Reloads rewards and the per-door mapping.
     *
     * @param rewardsSection the {@code rewards} section
     * @param doorsSection   the {@code doors} section, mapping day number to reward id
     */
    public void load(ConfigurationSection rewardsSection, ConfigurationSection doorsSection,
                     AdventParameters parameters) {
        this.parameters = parameters;
        this.rewards = RewardTable.read(rewardsSection, platform.plugin().getLogger());
        doorRewards.clear();
        for (String key : doorsSection.getKeys(false)) {
            int day;
            try {
                day = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                platform.plugin().getLogger().warning(
                        "advent.yml: door key '" + key + "' is not a day number.");
                continue;
            }
            String rewardId = doorsSection.getString(key);
            if (rewardId == null || rewardId.isBlank()) {
                continue;
            }
            if (rewards.byId(rewardId) == null) {
                platform.plugin().getLogger().warning("advent.yml: door " + day
                        + " points at reward '" + rewardId + "', which is not defined.");
                continue;
            }
            doorRewards.put(day, rewardId);
        }
    }

    // ------------------------------------------------------------- questions

    public int doorCount() {
        return Math.min(parameters.doors(), platform.season().length());
    }

    /** The reward behind a door, or {@code null} if it is randomly rolled. */
    public Reward fixedRewardFor(int day) {
        String id = doorRewards.get(day);
        return id == null ? null : rewards.byId(id);
    }

    public boolean opened(Player player, int day) {
        return platform.profiles().get(player).hasClaimedDay(day);
    }

    /** Whether a door can be opened right now, and why not if it cannot. */
    public Outcome check(Player player, int day) {
        Season season = platform.season();
        int today = season.currentDay();
        if (today < 0) {
            return Outcome.OUTSIDE_SEASON;
        }
        if (day < 1 || day > doorCount()) {
            return Outcome.NOT_YET;
        }
        if (opened(player, day)) {
            return Outcome.ALREADY_OPEN;
        }
        if (day > today) {
            return Outcome.NOT_YET;
        }
        if (day < today) {
            if (!parameters.allowCatchUp()) {
                return Outcome.CATCH_UP_DISABLED;
            }
            long cost = catchUpCost(today - day);
            if (!platform.economy().has(platform.profiles().get(player), cost)) {
                return Outcome.CANNOT_AFFORD;
            }
        }
        return Outcome.CLAIMED;
    }

    /** What a catch-up costs, scaling with how late it is. */
    public long catchUpCost(int daysLate) {
        return (long) parameters.catchUpCost() * Math.max(1, daysLate);
    }

    // ----------------------------------------------------------------- claim

    /**
     * Opens a door for a player.
     *
     * @return what happened; only {@link Outcome#CLAIMED} granted anything
     */
    public Outcome claim(Player player, int day) {
        Outcome outcome = check(player, day);
        if (outcome != Outcome.CLAIMED) {
            return outcome;
        }

        Reward reward = fixedRewardFor(day);
        if (reward == null) {
            reward = rewards.roll();
        }
        if (reward == null) {
            platform.plugin().getLogger().warning(
                    "Advent door " + day + " has no reward and the table is empty.");
            return Outcome.NO_REWARD;
        }

        PlayerProfile profile = platform.profiles().get(player);
        int today = platform.season().currentDay();
        boolean catchUp = day < today;
        if (catchUp) {
            long cost = catchUpCost(today - day);
            if (!platform.economy().take(profile, cost)) {
                return Outcome.CANNOT_AFFORD;
            }
            platform.messages().send(player, "advent.caught-up", Messages.of(
                    "day", String.valueOf(day),
                    "cost", EconomyService.format(cost)));
        }

        Map<String, String> placeholders = Messages.of(
                "day", String.valueOf(day),
                "doors", String.valueOf(doorCount()));

        platform.rewards().grant(player, reward, placeholders);
        profile.claimDay(day);
        // Only an on-time claim should extend the streak; buying back a missed
        // day must not retroactively repair it.
        if (!catchUp) {
            profile.recordAdventClaim(platform.season().todayEpochDay());
        }
        platform.progress().record(player, PlayerProfile.STAT_GIFTS_OPENED, 1);
        if (parameters.nicePointsPerDoor() > 0) {
            profile.addNaughtyNice(parameters.nicePointsPerDoor());
        }

        if (parameters.celebrate()) {
            Fx.helix(player.getLocation(), org.bukkit.Particle.HAPPY_VILLAGER, 2.0, 0.6);
            Fx.jingle(player.getLocation());
        }

        emit.accept(new AdventClaimedEvent(player.getUniqueId(), day, reward.id(),
                profile.adventStreak(), catchUp));

        int streak = profile.adventStreak();
        placeholders.put("streak", String.valueOf(streak));
        placeholders.put("opened", String.valueOf(profile.adventClaimed().size()));
        platform.messages().send(player, "advent.claimed", placeholders);

        grantStreakBonus(player, profile, streak);
        return Outcome.CLAIMED;
    }

    /** Pays a bonus every {@code streak-bonus-every} consecutive days. */
    private void grantStreakBonus(Player player, PlayerProfile profile, int streak) {
        int every = parameters.streakBonusEvery();
        if (every <= 0 || streak == 0 || streak % every != 0) {
            return;
        }
        long bonus = (long) parameters.streakBonusShards() * (streak / every);
        if (bonus <= 0) {
            return;
        }
        platform.economy().give(profile, bonus);
        platform.messages().send(player, "advent.streak-bonus", Messages.of(
                "streak", String.valueOf(streak),
                "amount", EconomyService.format(bonus)));
        Fx.festiveFirework(player.getLocation());
    }

    public RewardTable rewards() {
        return rewards;
    }

    public AdventParameters parameters() {
        return parameters;
    }

    public ChristmasPlatform platform() {
        return platform;
    }
}
