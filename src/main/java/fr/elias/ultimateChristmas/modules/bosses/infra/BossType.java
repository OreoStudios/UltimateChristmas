package fr.elias.ultimateChristmas.modules.bosses.infra;

import fr.elias.ultimateChristmas.core.hook.ModelHook;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * One configured festive boss, e.g. the Grinch or Krampus.
 *
 * <p>Parsed once from config. The reward table is the standard weighted one, so
 * boss loot is configured exactly like every other prize in the plugin.
 */
public final class BossType {

    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final double health;
    private final double speed;
    private final double hitboxScale;
    private final double attackDamage;
    private final boolean glowing;
    private final List<String> abilities;
    private final int rewardTop;
    private final RewardTable rewards;
    private final List<String> spawnLines;
    private final List<String> deathLines;
    private final Location spawnLocation;
    private final BossAutoSpawn autoSpawn;
    private final String model;
    private final ModelHook.Provider modelProvider;
    private final ModelSettings modelSettings;
    private final String minionModel;
    private final ModelHook.Provider minionProvider;
    private final ModelSettings minionSettings;
    private final List<String> minionTypes;
    private final double minionHealthMultiplier;

    private BossType(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.entityType = builder.entityType;
        this.health = builder.health;
        this.speed = builder.speed;
        this.hitboxScale = builder.hitboxScale;
        this.attackDamage = builder.attackDamage;
        this.glowing = builder.glowing;
        this.abilities = List.copyOf(builder.abilities);
        this.rewardTop = builder.rewardTop;
        this.rewards = builder.rewards;
        this.spawnLines = List.copyOf(builder.spawnLines);
        this.deathLines = List.copyOf(builder.deathLines);
        this.spawnLocation = builder.spawnLocation;
        this.autoSpawn = builder.autoSpawn;
        this.model = builder.model;
        this.modelProvider = builder.modelProvider;
        this.modelSettings = builder.modelSettings;
        this.minionModel = builder.minionModel;
        this.minionProvider = builder.minionProvider;
        this.minionSettings = builder.minionSettings;
        this.minionTypes = List.copyOf(builder.minionTypes);
        this.minionHealthMultiplier = builder.minionHealthMultiplier;
    }

    /**
     * How the built-in display renderer draws and animates a boss.
     *
     * @param scale       uniform model scale
     * @param offsetY     blocks to lift the model so its feet reach the ground
     * @param yawOffset   degrees added to the mob's facing
     * @param idle        looping clip when still
     * @param walk        looping clip when moving
     * @param run         looping clip when moving fast
     * @param attackClip  one-shot played when the boss lands a melee hit
     * @param spawnClip   one-shot played when the boss appears, or {@code null}
     * @param abilityClips ability id -> one-shot clip played when it is used
     */
    public record ModelSettings(float scale, double offsetY, float yawOffset,
                                String idle, String walk, String run,
                                String attackClip, String spawnClip,
                                String hurtClip, String deathClip, String rageClip,
                                String victoryClip, String carryClip, double rageThreshold,
                                double runThreshold, double movementRate,
                                java.util.Map<String, String> abilityClips) {
    }

    /** Reads a boss from a config section, or {@code null} if it is unusable. */
    public static BossType read(ConfigurationSection section, Logger logger) {
        Builder builder = new Builder();
        builder.id = section.getName();
        builder.displayName = section.getString("display-name", "&c" + builder.id);

        String typeName = section.getString("entity-type", "WITHER_SKELETON");
        try {
            builder.entityType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            logger.warning("bosses.yml: '" + builder.id + "' has unknown entity-type '"
                    + typeName + "'; using WITHER_SKELETON.");
            builder.entityType = EntityType.WITHER_SKELETON;
        }
        if (!builder.entityType.isAlive()) {
            logger.warning("bosses.yml: '" + builder.id + "' entity-type " + typeName
                    + " is not a living mob; skipping this boss.");
            return null;
        }

        builder.health = Math.max(1, section.getDouble("health", 500));
        builder.speed = Math.max(0, section.getDouble("speed", 0.28));
        builder.hitboxScale = Math.max(.1, Math.min(4, section.getDouble("hitbox-scale", 1)));
        builder.attackDamage = section.getDouble("attack-damage", 8);
        builder.glowing = section.getBoolean("glowing", true);
        builder.abilities.addAll(section.getStringList("abilities"));
        builder.rewardTop = Math.max(1, section.getInt("reward-top-contributors", 3));
        builder.rewards = RewardTable.read(section.getConfigurationSection("rewards"), logger);
        builder.spawnLines.addAll(section.getStringList("spawn-messages"));
        builder.deathLines.addAll(section.getStringList("death-messages"));
        builder.spawnLocation = readLocation(section.getConfigurationSection("spawn"));
        builder.autoSpawn = BossAutoSpawn.read(section.getConfigurationSection("auto-spawn"),
                builder.spawnLocation, logger);

        // Optional custom model, rendered by ModelEngine or BetterModel. The
        // 'model' is the model id those plugins loaded from its .bbmodel.
        builder.model = section.getString("model.id", section.getString("model", ""));
        builder.modelProvider = ModelHook.Provider.parse(
                section.getString("model.provider", "auto"));
        builder.modelSettings = readModelSettings(section.getConfigurationSection("model"));

        // Optional model for this boss's summoned minions.
        ConfigurationSection minions = section.getConfigurationSection("minions");
        ConfigurationSection minionModel = minions == null
                ? null : minions.getConfigurationSection("model");
        builder.minionModel = minionModel == null ? ""
                : minionModel.getString("id", minions.getString("model", ""));
        builder.minionProvider = ModelHook.Provider.parse(
                minionModel == null ? "auto" : minionModel.getString("provider", "auto"));
        builder.minionSettings = readModelSettings(minionModel);
        builder.minionTypes.addAll(section.getStringList("minions.types").stream()
                .map(id -> id.toLowerCase(Locale.ROOT)).toList());
        builder.minionHealthMultiplier = Math.max(.01,
                section.getDouble("minions.health-multiplier", .35));

        if (builder.rewards.isEmpty()) {
            logger.warning("bosses.yml: '" + builder.id + "' has no rewards; "
                    + "slayers will get nothing.");
        }
        return new BossType(builder);
    }

    /** Reads the display-renderer settings, with sensible defaults throughout. */
    private static ModelSettings readModelSettings(ConfigurationSection section) {
        if (section == null) {
            return new ModelSettings(1.0f, 0.0, 180f, "idle", "walk", "run",
                    "attack", null, "hurt", "death", "rage", "celebrate", null,
                    .35, .14, 1, java.util.Map.of());
        }
        ConfigurationSection anims = section.getConfigurationSection("animations");
        ConfigurationSection abilityAnims = section.getConfigurationSection("ability-animations");
        java.util.Map<String, String> abilityClips = new java.util.LinkedHashMap<>();
        if (abilityAnims != null) {
            for (String key : abilityAnims.getKeys(false)) {
                abilityClips.put(key.toLowerCase(Locale.ROOT), abilityAnims.getString(key));
            }
        }
        return new ModelSettings(
                (float) section.getDouble("scale", 1.0),
                section.getDouble("offset-y", 0.0),
                (float) section.getDouble("yaw-offset", 180.0),
                anims == null ? "idle" : anims.getString("idle", "idle"),
                anims == null ? "walk" : anims.getString("walk", "walk"),
                anims == null ? "run" : anims.getString("run", "run"),
                anims == null ? "attack" : anims.getString("attack", "attack"),
                anims == null ? null : anims.getString("spawn"),
                anims == null ? "hurt" : anims.getString("hurt", "hurt"),
                anims == null ? "death" : anims.getString("death", "death"),
                anims == null ? "rage" : anims.getString("rage", "rage"),
                anims == null ? "celebrate" : anims.getString("victory", "celebrate"),
                anims == null ? null : anims.getString("carry"),
                Math.max(0, Math.min(1, section.getDouble("rage-health-threshold", .35))),
                Math.max(.016, section.getDouble("run-speed-threshold", .14)),
                Math.max(.1, Math.min(2, section.getDouble("movement-animation-speed", 1))),
                abilityClips);
    }

    private static Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = org.bukkit.Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType entityType() {
        return entityType;
    }

    public double health() {
        return health;
    }

    public double speed() {
        return speed;
    }

    public double hitboxScale() { return hitboxScale; }
    public List<String> minionTypes() { return minionTypes; }
    public double minionHealthMultiplier() { return minionHealthMultiplier; }

    public double attackDamage() {
        return attackDamage;
    }

    public boolean glowing() {
        return glowing;
    }

    public List<String> abilities() {
        return abilities;
    }

    public int rewardTop() {
        return rewardTop;
    }

    public RewardTable rewards() {
        return rewards;
    }

    public List<String> spawnLines() {
        return spawnLines;
    }

    public List<String> deathLines() {
        return deathLines;
    }

    /** The configured spawn point, or {@code null} to require one at spawn time. */
    public Location spawnLocation() {
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    /** Optional automatic spawn policy, disabled by default. */
    public BossAutoSpawn autoSpawn() {
        return autoSpawn;
    }

    /** The custom model id, or an empty string for the vanilla appearance. */
    public String model() {
        return model;
    }

    public boolean hasModel() {
        return !model.isBlank();
    }

    public ModelHook.Provider modelProvider() {
        return modelProvider;
    }

    public ModelSettings modelSettings() {
        return modelSettings;
    }

    public String minionModel() {
        return minionModel;
    }

    public boolean hasMinionModel() {
        return !minionModel.isBlank();
    }

    public ModelHook.Provider minionProvider() {
        return minionProvider;
    }

    public ModelSettings minionSettings() {
        return minionSettings;
    }

    private static final class Builder {
        private String id;
        private String displayName;
        private EntityType entityType;
        private double health;
        private double speed;
        private double hitboxScale;
        private double attackDamage;
        private boolean glowing;
        private final List<String> abilities = new ArrayList<>();
        private int rewardTop;
        private RewardTable rewards;
        private final List<String> spawnLines = new ArrayList<>();
        private final List<String> deathLines = new ArrayList<>();
        private Location spawnLocation;
        private BossAutoSpawn autoSpawn;
        private String model = "";
        private ModelHook.Provider modelProvider = ModelHook.Provider.AUTO;
        private ModelSettings modelSettings;
        private String minionModel = "";
        private ModelHook.Provider minionProvider = ModelHook.Provider.AUTO;
        private ModelSettings minionSettings;
        private final List<String> minionTypes = new ArrayList<>();
        private double minionHealthMultiplier;
    }
}
