package fr.elias.ultimateChristmas.core.reward;

import fr.elias.ultimateChristmas.core.item.ItemCodec;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * One grantable prize, parsed once from config.
 *
 * <p>Every prize in the plugin — advent doors, Santa's gifts, boss loot, shop
 * purchases, quest and pass payouts, snowball hits, found presents — is one of
 * these. The 3.x code reimplemented command dispatch, message sending, firework
 * spawning and sound playing separately in each of those places, with slightly
 * different config keys and bugs; this is the single definition.
 *
 * <pre>
 * weight: 25                  # relative chance within its table ('chance' also works)
 * display:                    # how it looks in a GUI preview
 *   material: EMERALD
 *   name: "&amp;aRare prize"
 *   lore: ["&amp;7Lucky!"]
 * shards: 25-50               # fixed number or inclusive range
 * xp: 100                     # vanilla experience
 * pass-xp: 50                 # festive pass progress
 * naughty-nice: 5             # nudge the player's standing
 * items:                      # given directly, dropped if the inventory is full
 *   - { material: DIAMOND, amount: 3 }
 * commands: ["give %player% cookie 5"]     # from console
 * player-commands: ["warp tree"]           # as the player
 * messages: ["&amp;aYou got something nice!"]
 * broadcast: ["&amp;e%player% opened a rare gift!"]
 * title: "&amp;6Merry Christmas"
 * subtitle: "&amp;7Enjoy your prize"
 * sound: ENTITY_PLAYER_LEVELUP
 * particle: HAPPY_VILLAGER
 * firework: true
 * effects: ["SPEED:600:1", "REGENERATION:200:0"]
 * </pre>
 */
public final class Reward {

    private final String id;
    private final int weight;

    private final Amount shards;
    private final Amount xp;
    private final Amount passXp;
    private final int naughtyNice;

    private final List<ItemStack> items;
    private final List<String> consoleCommands;
    private final List<String> playerCommands;
    private final List<String> messages;
    private final List<String> broadcasts;
    private final List<PotionEffect> effects;

    private final String title;
    private final String subtitle;
    private final Sound sound;
    private final float soundVolume;
    private final float soundPitch;
    private final Particle particle;
    private final boolean firework;

    private final ItemStack display;

    private Reward(Builder builder) {
        this.id = builder.id;
        this.weight = builder.weight;
        this.shards = builder.shards;
        this.xp = builder.xp;
        this.passXp = builder.passXp;
        this.naughtyNice = builder.naughtyNice;
        this.items = List.copyOf(builder.items);
        this.consoleCommands = List.copyOf(builder.consoleCommands);
        this.playerCommands = List.copyOf(builder.playerCommands);
        this.messages = List.copyOf(builder.messages);
        this.broadcasts = List.copyOf(builder.broadcasts);
        this.effects = List.copyOf(builder.effects);
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.sound = builder.sound;
        this.soundVolume = builder.soundVolume;
        this.soundPitch = builder.soundPitch;
        this.particle = builder.particle;
        this.firework = builder.firework;
        this.display = builder.display;
    }

    /**
     * Reads a reward from a config section.
     *
     * <p>Unreadable sounds, particles and effects are logged and skipped rather
     * than aborting: a typo in one cosmetic key should not stop a player
     * receiving the actual prize.
     */
    public static Reward read(ConfigurationSection section, Logger logger) {
        Builder builder = new Builder(section.getName());

        builder.weight = Math.max(0, section.getInt("weight", section.getInt("chance", 1)));
        builder.shards = Amount.parse(section.get("shards"));
        builder.xp = Amount.parse(section.get("xp"));
        builder.passXp = Amount.parse(section.get("pass-xp"));
        builder.naughtyNice = section.getInt("naughty-nice", 0);

        builder.consoleCommands.addAll(section.getStringList("commands"));
        builder.playerCommands.addAll(section.getStringList("player-commands"));
        builder.messages.addAll(section.getStringList("messages"));
        builder.broadcasts.addAll(section.getStringList("broadcast"));

        builder.title = section.getString("title");
        builder.subtitle = section.getString("subtitle");

        // 3.x nested these under 'event'; both spellings are accepted.
        ConfigurationSection event = section.getConfigurationSection("event");
        String soundName = section.getString("sound",
                event == null ? null : event.getString("sound"));
        builder.sound = resolveSound(soundName, section.getCurrentPath(), logger);
        builder.soundVolume = (float) section.getDouble("sound-volume", 1.0);
        builder.soundPitch = (float) section.getDouble("sound-pitch", 1.0);
        builder.firework = section.getBoolean("firework",
                event != null && event.getBoolean("firework", false));
        builder.particle = resolveParticle(section.getString("particle"),
                section.getCurrentPath(), logger);

        for (String raw : section.getStringList("effects")) {
            PotionEffect effect = parseEffect(raw, section.getCurrentPath(), logger);
            if (effect != null) {
                builder.effects.add(effect);
            }
        }

        builder.items.addAll(readItems(section, logger));

        ConfigurationSection display = section.getConfigurationSection("display");
        if (display == null) {
            display = section.getConfigurationSection("icon");
        }
        builder.display = display != null
                ? ItemCodec.read(display, Material.CHEST, logger)
                : null;

        return new Reward(builder);
    }

    private static List<ItemStack> readItems(ConfigurationSection section, Logger logger) {
        List<ItemStack> out = new ArrayList<>();
        // A list of inline maps: items: [{material: DIAMOND, amount: 3}]
        // ItemCodec reads a ConfigurationSection, so each map is wrapped in a
        // scratch section that is removed again straight away.
        List<?> inline = section.getList("items");
        if (inline != null) {
            int index = 0;
            for (Object entry : inline) {
                if (entry instanceof java.util.Map<?, ?> map) {
                    String scratch = "__item_" + index++;
                    ConfigurationSection synthetic = section.createSection(scratch, map);
                    out.add(ItemCodec.read(synthetic, Material.STONE, logger));
                    section.set(scratch, null);
                }
            }
        }
        // Or a keyed section: items: { gift: {material: DIAMOND} }
        ConfigurationSection keyed = section.getConfigurationSection("items");
        if (keyed != null) {
            for (String key : keyed.getKeys(false)) {
                ConfigurationSection child = keyed.getConfigurationSection(key);
                if (child != null) {
                    out.add(ItemCodec.read(child, Material.STONE, logger));
                }
            }
        }
        return out;
    }

    private static Sound resolveSound(String name, String path, Logger logger) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalised = name.trim().toLowerCase(Locale.ROOT).replace('.', '_');
        Sound sound = org.bukkit.Registry.SOUNDS.get(
                org.bukkit.NamespacedKey.minecraft(normalised));
        if (sound == null && logger != null) {
            logger.warning("Unknown sound '" + name + "' at " + path);
        }
        return sound;
    }

    private static Particle resolveParticle(String name, String path, Logger logger) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            if (logger != null) {
                logger.warning("Unknown particle '" + name + "' at " + path);
            }
            return null;
        }
    }

    /** Parses {@code TYPE:ticks:amplifier}. */
    private static PotionEffect parseEffect(String raw, String path, Logger logger) {
        String[] parts = raw.split(":");
        PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                org.bukkit.NamespacedKey.minecraft(
                        parts[0].trim().toLowerCase(Locale.ROOT)));
        if (type == null) {
            if (logger != null) {
                logger.warning("Unknown potion effect '" + parts[0] + "' at " + path);
            }
            return null;
        }
        int duration = 200;
        int amplifier = 0;
        try {
            if (parts.length > 1) {
                duration = Integer.parseInt(parts[1].trim());
            }
            if (parts.length > 2) {
                amplifier = Integer.parseInt(parts[2].trim());
            }
        } catch (NumberFormatException ex) {
            if (logger != null) {
                logger.warning("Unreadable effect numbers in '" + raw + "' at " + path);
            }
        }
        return new PotionEffect(type, duration, amplifier, true, true, true);
    }

    // ---------------------------------------------------------------- getters

    public String id() {
        return id;
    }

    public int weight() {
        return weight;
    }

    public Amount shards() {
        return shards;
    }

    public Amount xp() {
        return xp;
    }

    public Amount passXp() {
        return passXp;
    }

    public int naughtyNice() {
        return naughtyNice;
    }

    public List<ItemStack> items() {
        return items;
    }

    public List<String> consoleCommands() {
        return consoleCommands;
    }

    public List<String> playerCommands() {
        return playerCommands;
    }

    public List<String> messages() {
        return messages;
    }

    public List<String> broadcasts() {
        return broadcasts;
    }

    public List<PotionEffect> effects() {
        return effects;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public Sound sound() {
        return sound;
    }

    public float soundVolume() {
        return soundVolume;
    }

    public float soundPitch() {
        return soundPitch;
    }

    public Particle particle() {
        return particle;
    }

    public boolean firework() {
        return firework;
    }

    /** The configured preview icon, or {@code null} if none was set. */
    public ItemStack display() {
        return display == null ? null : display.clone();
    }

    /** True if this reward would do nothing at all, which usually means a typo. */
    public boolean isEmpty() {
        return shards.isZero() && xp.isZero() && passXp.isZero() && naughtyNice == 0
                && items.isEmpty() && consoleCommands.isEmpty() && playerCommands.isEmpty()
                && messages.isEmpty() && broadcasts.isEmpty() && effects.isEmpty();
    }

    private static final class Builder {
        private final String id;
        private int weight = 1;
        private Amount shards = Amount.ZERO;
        private Amount xp = Amount.ZERO;
        private Amount passXp = Amount.ZERO;
        private int naughtyNice;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private final List<String> broadcasts = new ArrayList<>();
        private final List<PotionEffect> effects = new ArrayList<>();
        private String title;
        private String subtitle;
        private Sound sound;
        private float soundVolume = 1.0f;
        private float soundPitch = 1.0f;
        private Particle particle;
        private boolean firework;
        private ItemStack display;

        private Builder(String id) {
            this.id = id;
        }
    }
}
