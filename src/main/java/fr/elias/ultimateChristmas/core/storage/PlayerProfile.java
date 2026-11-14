package fr.elias.ultimateChristmas.core.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the plugin remembers about one player.
 *
 * <p>Mutated on the main thread and written out by {@link ProfileManager} on an
 * async worker, so mutation bumps a dirty flag rather than touching the disk.
 * Collections are kept synchronised because the writer reads them off-thread.
 */
public final class PlayerProfile {

    /** Well-known stat keys. Any other key is accepted; these are the tracked ones. */
    public static final String STAT_SHARDS = "shards";
    public static final String STAT_SHARDS_EARNED = "shards_earned";
    public static final String STAT_GIFTS_OPENED = "gifts_opened";
    public static final String STAT_PRESENTS_FOUND = "presents_found";
    public static final String STAT_SNOWBALLS_HIT = "snowballs_hit";
    public static final String STAT_BOSS_DAMAGE = "boss_damage";
    public static final String STAT_BOSSES_SLAIN = "bosses_slain";
    public static final String STAT_ADVENT_CLAIMED = "advent_claimed";
    public static final String STAT_ORNAMENTS_HUNG = "ornaments_hung";
    public static final String STAT_CAROLS_SUNG = "carols_sung";
    public static final String STAT_GIFTS_GIVEN = "gifts_given";

    private final UUID id;
    private String name;

    private final Map<String, Long> stats = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> flags = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Integer> adventClaimed = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> cosmeticsOwned = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<String, String> cosmeticsEquipped = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Long> cooldowns = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Long> questProgress = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> questsCompleted = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Integer> passClaimed = Collections.synchronizedSet(new LinkedHashSet<>());

    private int naughtyNice;
    private int adventStreak;
    private int adventBestStreak;
    private long adventLastDay = Long.MIN_VALUE;
    private long passXp;
    private boolean passPremium;
    private long firstSeen;
    private long lastSeen;
    private String secretSantaTarget;
    private boolean secretSantaDelivered;

    private volatile boolean dirty;

    public PlayerProfile(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.firstSeen = System.currentTimeMillis();
        this.lastSeen = this.firstSeen;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String value) {
        if (!java.util.Objects.equals(this.name, value)) {
            this.name = value;
            markDirty();
        }
    }

    // ----------------------------------------------------------------- stats

    public long stat(String key) {
        return stats.getOrDefault(key, 0L);
    }

    /** Adds to a stat and returns the new total. Negative deltas are allowed. */
    public long addStat(String key, long delta) {
        if (delta == 0) {
            return stat(key);
        }
        long updated = stats.merge(key, delta, Long::sum);
        markDirty();
        return updated;
    }

    public void setStat(String key, long value) {
        stats.put(key, value);
        markDirty();
    }

    /** A snapshot copy, safe to iterate off the main thread. */
    public Map<String, Long> stats() {
        synchronized (stats) {
            return Map.copyOf(stats);
        }
    }

    // ---------------------------------------------------------------- shards

    public long shards() {
        return stat(STAT_SHARDS);
    }

    public void shards(long value) {
        setStat(STAT_SHARDS, Math.max(0, value));
    }

    // ----------------------------------------------------------------- flags

    public boolean flag(String key) {
        return flags.contains(key);
    }

    public void flag(String key, boolean value) {
        boolean changed = value ? flags.add(key) : flags.remove(key);
        if (changed) {
            markDirty();
        }
    }

    public Set<String> flags() {
        synchronized (flags) {
            return Set.copyOf(flags);
        }
    }

    // --------------------------------------------------------------- advent

    /**
     * Which calendar days this player has opened.
     *
     * <p>3.x stored only {@code last_claim_epoch_day}, so an advent calendar
     * could never show more than one opened door and a missed day was
     * unrecoverable. Tracking the set fixes that and makes catch-up tokens
     * possible.
     */
    public Set<Integer> adventClaimed() {
        synchronized (adventClaimed) {
            return Set.copyOf(adventClaimed);
        }
    }

    public boolean hasClaimedDay(int day) {
        return adventClaimed.contains(day);
    }

    public void claimDay(int day) {
        if (adventClaimed.add(day)) {
            addStat(STAT_ADVENT_CLAIMED, 1);
            markDirty();
        }
    }

    public void clearAdvent() {
        adventClaimed.clear();
        adventStreak = 0;
        adventLastDay = Long.MIN_VALUE;
        markDirty();
    }

    public int adventStreak() {
        return adventStreak;
    }

    public int adventBestStreak() {
        return adventBestStreak;
    }

    public long adventLastDay() {
        return adventLastDay;
    }

    /**
     * Records a claim on {@code epochDay}, extending the streak when it is the
     * day right after the previous claim and restarting it otherwise.
     */
    public void recordAdventClaim(long epochDay) {
        if (adventLastDay == epochDay) {
            return;
        }
        adventStreak = (adventLastDay == epochDay - 1) ? adventStreak + 1 : 1;
        adventBestStreak = Math.max(adventBestStreak, adventStreak);
        adventLastDay = epochDay;
        markDirty();
    }

    // --------------------------------------------------------- naughty / nice

    public int naughtyNice() {
        return naughtyNice;
    }

    /** Clamped to -100..100; the ends are "deeply naughty" and "perfectly nice". */
    public void addNaughtyNice(int delta) {
        int updated = Math.max(-100, Math.min(100, naughtyNice + delta));
        if (updated != naughtyNice) {
            naughtyNice = updated;
            markDirty();
        }
    }

    public void naughtyNice(int value) {
        naughtyNice = Math.max(-100, Math.min(100, value));
        markDirty();
    }

    // ------------------------------------------------------------------ pass

    public long passXp() {
        return passXp;
    }

    public void addPassXp(long delta) {
        if (delta != 0) {
            passXp = Math.max(0, passXp + delta);
            markDirty();
        }
    }

    public boolean passPremium() {
        return passPremium;
    }

    public void passPremium(boolean value) {
        if (passPremium != value) {
            passPremium = value;
            markDirty();
        }
    }

    public Set<Integer> passClaimed() {
        synchronized (passClaimed) {
            return Set.copyOf(passClaimed);
        }
    }

    public boolean hasClaimedTier(int tier) {
        return passClaimed.contains(tier);
    }

    public void claimTier(int tier) {
        if (passClaimed.add(tier)) {
            markDirty();
        }
    }

    // ------------------------------------------------------------- cosmetics

    public Set<String> cosmeticsOwned() {
        synchronized (cosmeticsOwned) {
            return Set.copyOf(cosmeticsOwned);
        }
    }

    public boolean owns(String cosmeticId) {
        return cosmeticsOwned.contains(cosmeticId);
    }

    public void grantCosmetic(String cosmeticId) {
        if (cosmeticsOwned.add(cosmeticId)) {
            markDirty();
        }
    }

    /** The cosmetic equipped in a slot, or {@code null} for none. */
    public String equipped(String slot) {
        return cosmeticsEquipped.get(slot);
    }

    public void equip(String slot, String cosmeticId) {
        if (cosmeticId == null) {
            if (cosmeticsEquipped.remove(slot) != null) {
                markDirty();
            }
        } else if (!cosmeticId.equals(cosmeticsEquipped.put(slot, cosmeticId))) {
            markDirty();
        }
    }

    public Map<String, String> cosmeticsEquipped() {
        synchronized (cosmeticsEquipped) {
            return Map.copyOf(cosmeticsEquipped);
        }
    }

    // ------------------------------------------------------------- cooldowns

    /** Whether {@code key} is still cooling down. */
    public boolean onCooldown(String key) {
        Long until = cooldowns.get(key);
        return until != null && until > System.currentTimeMillis();
    }

    public long cooldownRemaining(String key) {
        Long until = cooldowns.get(key);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    public void setCooldown(String key, long millis) {
        if (millis <= 0) {
            cooldowns.remove(key);
        } else {
            cooldowns.put(key, System.currentTimeMillis() + millis);
        }
        markDirty();
    }

    /** Drops expired entries so the map cannot grow without bound. */
    void pruneCooldowns() {
        long now = System.currentTimeMillis();
        synchronized (cooldowns) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
    }

    public Map<String, Long> cooldowns() {
        synchronized (cooldowns) {
            return Map.copyOf(cooldowns);
        }
    }

    // ---------------------------------------------------------------- quests

    public long questProgress(String questId) {
        return questProgress.getOrDefault(questId, 0L);
    }

    public long addQuestProgress(String questId, long delta) {
        long updated = questProgress.merge(questId, delta, Long::sum);
        markDirty();
        return updated;
    }

    public void resetQuests(Set<String> questIds) {
        synchronized (questProgress) {
            questProgress.keySet().removeAll(questIds);
        }
        questsCompleted.removeAll(questIds);
        markDirty();
    }

    public boolean questCompleted(String questId) {
        return questsCompleted.contains(questId);
    }

    public void completeQuest(String questId) {
        if (questsCompleted.add(questId)) {
            markDirty();
        }
    }

    public Map<String, Long> questProgressAll() {
        synchronized (questProgress) {
            return Map.copyOf(questProgress);
        }
    }

    public Set<String> questsCompleted() {
        synchronized (questsCompleted) {
            return Set.copyOf(questsCompleted);
        }
    }

    // --------------------------------------------------------- secret santa

    public String secretSantaTarget() {
        return secretSantaTarget;
    }

    public void secretSantaTarget(String target) {
        this.secretSantaTarget = target;
        this.secretSantaDelivered = false;
        markDirty();
    }

    public boolean secretSantaDelivered() {
        return secretSantaDelivered;
    }

    public void secretSantaDelivered(boolean value) {
        this.secretSantaDelivered = value;
        markDirty();
    }

    // ------------------------------------------------------------- bookkeeping

    public long firstSeen() {
        return firstSeen;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void touch() {
        lastSeen = System.currentTimeMillis();
        markDirty();
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    void markClean() {
        dirty = false;
    }

    // ----------------------------------------------- serialisation support

    /** Replaces all state from storage. Used only by the store implementations. */
    void restore(Map<String, Long> loadedStats, Set<String> loadedFlags,
                 Set<Integer> loadedAdvent, Set<String> loadedCosmetics,
                 Map<String, String> loadedEquipped, Map<String, Long> loadedCooldowns,
                 Map<String, Long> loadedQuests, Set<String> loadedQuestsDone,
                 Set<Integer> loadedPassTiers) {
        stats.clear();
        stats.putAll(loadedStats);
        flags.clear();
        flags.addAll(loadedFlags);
        adventClaimed.clear();
        adventClaimed.addAll(loadedAdvent);
        cosmeticsOwned.clear();
        cosmeticsOwned.addAll(loadedCosmetics);
        cosmeticsEquipped.clear();
        cosmeticsEquipped.putAll(loadedEquipped);
        cooldowns.clear();
        cooldowns.putAll(loadedCooldowns);
        questProgress.clear();
        questProgress.putAll(loadedQuests);
        questsCompleted.clear();
        questsCompleted.addAll(loadedQuestsDone);
        passClaimed.clear();
        passClaimed.addAll(loadedPassTiers);
        pruneCooldowns();
    }

    void scalars(int naughty, int streak, int bestStreak, long lastDay, long xp,
                 boolean premium, long created, long seen, String santaTarget,
                 boolean santaDelivered) {
        this.naughtyNice = naughty;
        this.adventStreak = streak;
        this.adventBestStreak = bestStreak;
        this.adventLastDay = lastDay;
        this.passXp = xp;
        this.passPremium = premium;
        this.firstSeen = created;
        this.lastSeen = seen;
        this.secretSantaTarget = santaTarget;
        this.secretSantaDelivered = santaDelivered;
    }

    /** Mutable views for the store implementations only. */
    Set<String> rawFlags() {
        return flags;
    }

    Set<Integer> rawAdvent() {
        return adventClaimed;
    }

    @Override
    public String toString() {
        return "PlayerProfile[" + name + " " + id + " shards=" + shards() + "]";
    }

    /** Equality is identity by UUID; two profiles for one player is a bug. */
    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerProfile profile && profile.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Convenience for building an empty profile keyed to a player. */
    public static PlayerProfile blank(UUID id, String name) {
        return new PlayerProfile(id, name == null ? id.toString().substring(0, 8) : name);
    }

    /** Exposed so stores can rebuild the set types they read into. */
    public static Set<String> newStringSet() {
        return new HashSet<>();
    }
}
