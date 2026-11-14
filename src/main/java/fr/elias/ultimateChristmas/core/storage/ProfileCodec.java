package fr.elias.ultimateChristmas.core.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts a {@link PlayerProfile} to and from a plain nested map.
 *
 * <p>Both stores share this, so a server can switch between YAML and SQLite and
 * read back exactly the same data. Number coercion is deliberately lenient:
 * YAML hands back {@code Integer} or {@code Long} depending on magnitude, and
 * JSON hands back {@code Double}, so every numeric read goes through
 * {@link #asLong}.
 */
final class ProfileCodec {

    private ProfileCodec() {
    }

    static Map<String, Object> encode(PlayerProfile profile) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", profile.name());
        out.put("stats", new LinkedHashMap<>(profile.stats()));
        out.put("flags", new ArrayList<>(profile.flags()));
        out.put("advent-claimed", new ArrayList<>(profile.adventClaimed()));
        out.put("advent-streak", profile.adventStreak());
        out.put("advent-best-streak", profile.adventBestStreak());
        out.put("advent-last-day", profile.adventLastDay());
        out.put("naughty-nice", profile.naughtyNice());
        out.put("pass-xp", profile.passXp());
        out.put("pass-premium", profile.passPremium());
        out.put("pass-claimed", new ArrayList<>(profile.passClaimed()));
        out.put("cosmetics-owned", new ArrayList<>(profile.cosmeticsOwned()));
        out.put("cosmetics-equipped", new LinkedHashMap<>(profile.cosmeticsEquipped()));
        out.put("cooldowns", new LinkedHashMap<>(profile.cooldowns()));
        out.put("quest-progress", new LinkedHashMap<>(profile.questProgressAll()));
        out.put("quests-completed", new ArrayList<>(profile.questsCompleted()));
        out.put("secret-santa-target", profile.secretSantaTarget());
        out.put("secret-santa-delivered", profile.secretSantaDelivered());
        out.put("first-seen", profile.firstSeen());
        out.put("last-seen", profile.lastSeen());
        return out;
    }

    static PlayerProfile decode(UUID id, Map<String, Object> data) {
        String name = asString(data.get("name"), id.toString().substring(0, 8));
        PlayerProfile profile = new PlayerProfile(id, name);

        profile.restore(
                longMap(data.get("stats")),
                stringSet(data.get("flags")),
                intSet(data.get("advent-claimed")),
                stringSet(data.get("cosmetics-owned")),
                stringMap(data.get("cosmetics-equipped")),
                longMap(data.get("cooldowns")),
                longMap(data.get("quest-progress")),
                stringSet(data.get("quests-completed")),
                intSet(data.get("pass-claimed")));

        profile.scalars(
                (int) asLong(data.get("naughty-nice"), 0),
                (int) asLong(data.get("advent-streak"), 0),
                (int) asLong(data.get("advent-best-streak"), 0),
                asLong(data.get("advent-last-day"), Long.MIN_VALUE),
                asLong(data.get("pass-xp"), 0),
                asBoolean(data.get("pass-premium")),
                asLong(data.get("first-seen"), System.currentTimeMillis()),
                asLong(data.get("last-seen"), System.currentTimeMillis()),
                asString(data.get("secret-santa-target"), null),
                asBoolean(data.get("secret-santa-delivered")));

        profile.markClean();
        return profile;
    }

    // ------------------------------------------------------------- coercion

    static long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text && !text.isEmpty() ? text : fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        // Bukkit hands nested YAML back as a ConfigurationSection, not a Map.
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            return new LinkedHashMap<>(section.getValues(false));
        }
        return Map.of();
    }

    static Map<String, Long> longMap(Object value) {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, Object> entry : asMap(value).entrySet()) {
            out.put(entry.getKey(), asLong(entry.getValue(), 0));
        }
        return out;
    }

    static Map<String, String> stringMap(Object value) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Object> entry : asMap(value).entrySet()) {
            if (entry.getValue() != null) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return out;
    }

    static Set<String> stringSet(Object value) {
        Set<String> out = new HashSet<>();
        if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        }
        return out;
    }

    static Set<Integer> intSet(Object value) {
        Set<Integer> out = new HashSet<>();
        if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                out.add((int) asLong(item, 0));
            }
        }
        return out;
    }

    /** Flattens a nested map into dotted paths, for writing into a YAML section. */
    static void flattenInto(org.bukkit.configuration.ConfigurationSection section,
                            Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            section.set(entry.getKey(), entry.getValue());
        }
    }

    /** Reads a YAML section back into the plain-map shape {@link #decode} expects. */
    static Map<String, Object> fromSection(org.bukkit.configuration.ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof org.bukkit.configuration.ConfigurationSection nested) {
                out.put(key, new LinkedHashMap<>(nested.getValues(false)));
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    /** Stat keys worth indexing separately for leaderboard queries. */
    static List<String> indexedStats() {
        return List.of(PlayerProfile.STAT_SHARDS, PlayerProfile.STAT_SHARDS_EARNED,
                PlayerProfile.STAT_GIFTS_OPENED, PlayerProfile.STAT_PRESENTS_FOUND,
                PlayerProfile.STAT_SNOWBALLS_HIT, PlayerProfile.STAT_BOSS_DAMAGE,
                PlayerProfile.STAT_BOSSES_SLAIN, PlayerProfile.STAT_ADVENT_CLAIMED,
                PlayerProfile.STAT_ORNAMENTS_HUNG, PlayerProfile.STAT_CAROLS_SUNG,
                PlayerProfile.STAT_GIFTS_GIVEN);
    }
}
