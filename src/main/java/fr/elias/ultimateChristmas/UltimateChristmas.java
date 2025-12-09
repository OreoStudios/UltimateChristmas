// File: src/main/java/fr/elias/ultimateChristmas/UltimateChristmas.java
package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.boss.GrinchBossListener;
import fr.elias.ultimateChristmas.boss.GrinchBossManager;
import fr.elias.ultimateChristmas.commands.PlayFestiveMusicCommand;
import fr.elias.ultimateChristmas.commands.SantaAdminCommand;
import fr.elias.ultimateChristmas.commands.ShardsCommand;
import fr.elias.ultimateChristmas.commands.SnowCommand;
import fr.elias.ultimateChristmas.commands.UltimateChristmasCommand;
import fr.elias.ultimateChristmas.daily.AdventCalendarCommand;
import fr.elias.ultimateChristmas.daily.DailyGiftCommand;
import fr.elias.ultimateChristmas.daily.DailyGiftListener;
import fr.elias.ultimateChristmas.daily.DailyGiftManager;
import fr.elias.ultimateChristmas.daily.DailyProgressStore;
import fr.elias.ultimateChristmas.debug.DebugCombatListener;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.economy.ShardShopGUI;
import fr.elias.ultimateChristmas.economy.ShardShopListener;
import fr.elias.ultimateChristmas.effects.SnowEffectManager;
import fr.elias.ultimateChristmas.integration.WorldGuardIntegration;
import fr.elias.ultimateChristmas.listeners.*;
import fr.elias.ultimateChristmas.music.MusicManager;
import fr.elias.ultimateChristmas.santa.SantaManager;
import fr.elias.ultimateChristmas.santa.SantaProtectionListener;
import fr.elias.ultimateChristmas.santa.SantaWalkController;
import fr.elias.ultimateChristmas.util.ConfigUtil;
import fr.elias.ultimateChristmas.util.Debug;
import fr.elias.ultimateChristmas.util.Lang;
import fr.elias.ultimateChristmas.util.Msg;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInvsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class UltimateChristmas extends JavaPlugin {

    /* -------------------------------------------------
     * STATIC INSTANCE
     * ------------------------------------------------- */
    private static UltimateChristmas instance;

    public static UltimateChristmas get() {
        return instance;
    }

    /* -------------------------------------------------
     * MANAGERS / CORE COMPONENTS
     * ------------------------------------------------- */
    private ShardManager shardManager;
    private InventoryManager invManager;
    private ShardShopGUI shardShopGUI;
    private DailyGiftManager dailyGiftManager;
    private DailyProgressStore dailyProgressStore;
    private MusicManager musicManager;
    private WorldGuardIntegration wgIntegration;
    private SantaManager santaManager;
    private GrinchBossManager grinchBossManager;
    private SnowEffectManager snowEffectManager;

    // active pathfinder walker for Santa (Paper only)
    private SantaWalkController activeWalkController;

    // capability detection
    private boolean paperPathfinderAvailable;
    private DebugCombatListener debugListener;

    /* -------------------------------------------------
     * ENABLE / DISABLE
     * ------------------------------------------------- */
    @Override
    public void onEnable() {
        instance = this;

        // Ensure data folder + music subfolder exist
        getDataFolder().mkdirs();
        new File(getDataFolder(), "music").mkdirs();

        /*
         * 1) Load / create all configs
         */
        saveDefaultConfig();                      // config.yml
        ConfigUtil.load(this, "shards.yml");
        ConfigUtil.load(this, "shop.yml");
        ConfigUtil.load(this, "daily_gifts.yml");
        ConfigUtil.load(this, "music.yml");
        ConfigUtil.load(this, "santa.yml");
        ConfigUtil.load(this, "snowball.yml");
        ConfigUtil.load(this, "grinchboss.yml");
        ConfigUtil.load(this, "snow.yml");        // snow settings
        ConfigUtil.load(this, "lang.yml");        // messages

        /*
         * 2) Init debug + lang
         */
        Debug.init(this);
        Lang.init(this);

        /*
         * 3) Detect optional server capabilities
         */
        this.paperPathfinderAvailable = checkPaperPathfinder();

        Debug.info("Server brand: " + Bukkit.getName() + " " + Bukkit.getBukkitVersion());
        Debug.info("Paper pathfinder API available: " + paperPathfinderAvailable);
        Debug.info("LibsDisguises present: " + isPluginEnabled("LibsDisguises"));
        Debug.info("WorldGuard present: " + isPluginEnabled("WorldGuard"));

        /*
         * 4) Core managers
         */
        // Shard economy
        this.shardManager = new ShardManager(this);
        this.shardManager.reloadConfig(); // load balances / config from shards.yml

        // Daily gift claim / cooldown logic
        this.dailyProgressStore = new DailyProgressStore(this);
        this.dailyGiftManager = new DailyGiftManager(this);
        this.dailyGiftManager.setProgressStore(this.dailyProgressStore);

        // Music / festive song playback
        this.musicManager = new MusicManager(this);

        // Region leash + safety integration for Santa / Grinch
        this.wgIntegration = new WorldGuardIntegration(this);

        // Snow effect manager + /snowify command
        this.snowEffectManager = new SnowEffectManager(this);
        if (getCommand("snowify") != null) {
            getCommand("snowify").setExecutor(new SnowCommand(snowEffectManager));
        }

        /*
         * 5) SmartInvs / ShardShop GUI setup
         */
        this.invManager = new InventoryManager(this);
        this.invManager.init();

        // Some versions of SmartInvs require informing the static plugin holder.
        SmartInvsPlugin.setPlugin(this);

        // Shard shop GUI (uses invManager + shardManager)
        this.shardShopGUI = new ShardShopGUI(this, this.shardManager, this.invManager);

        /*
         * 6) Santa & Grinch managers
         */
        this.santaManager = new SantaManager(this, this.wgIntegration, this.shardManager);
        this.grinchBossManager = new GrinchBossManager(this);

        /*
         * 7) Register listeners
         */
        var pm = getServer().getPluginManager();

        // earn shards from breaking blocks / killing mobs etc
        pm.registerEvents(new BlockBreakListener(this, shardManager), this);
        pm.registerEvents(new EntityKillListener(this, shardManager), this);

        // SmartInvs-based daily gift/advent GUI interactions
        pm.registerEvents(new DailyGiftListener(this, dailyGiftManager, invManager), this);

        // snowball fights / villager hit rewards
        pm.registerEvents(new SnowballHitListener(this), this);

        // shop close hook (currently placeholder for future cleanup)
        pm.registerEvents(new ShardShopListener(this, shardShopGUI), this);

        // join/quit etc player events, messages, etc
        pm.registerEvents(new PlayerListener(this), this);

        // protect Santa from damage / block renames / hologram bars etc
        pm.registerEvents(new SantaProtectionListener(this, santaManager), this);
        pm.registerEvents(new PresentOpenListener(this, santaManager), this);

        // custom “uses left” durability system
        pm.registerEvents(new CustomDurabilityListener(this), this);

        // Grinch combat lifecycle (hit + death → rewards)
        pm.registerEvents(new GrinchBossListener(this, grinchBossManager), this);


        // Debug combat overlay (optional)
        boolean debug = getConfig().getBoolean("debug", false);
        if (debug) {
            debugListener = new DebugCombatListener(this, true);
            pm.registerEvents(debugListener, this);
        }

        /*
         * 8) Register commands
         */
        // /shards
        if (getCommand("shards") != null) {
            getCommand("shards").setExecutor(
                    new ShardsCommand(this, shardManager, shardShopGUI)
            );
        }

        // /grinchboss
        if (getCommand("grinchboss") != null) {
            var cmd = new fr.elias.ultimateChristmas.commands.GrinchBossCommand(this, grinchBossManager, wgIntegration);
            getCommand("grinchboss").setExecutor(cmd);
            getCommand("grinchboss").setTabCompleter(cmd);
        }

        // /advent
        if (getCommand("advent") != null) {
            AdventCalendarCommand adventCmd =
                    new AdventCalendarCommand(invManager, dailyGiftManager, dailyProgressStore);
            getCommand("advent").setExecutor(adventCmd);
            getCommand("advent").setTabCompleter(adventCmd);
        }

        // /playchristmas
        if (getCommand("playchristmas") != null) {
            PlayFestiveMusicCommand cmdExec = new PlayFestiveMusicCommand(this, musicManager);
            getCommand("playchristmas").setExecutor(cmdExec);
            getCommand("playchristmas").setTabCompleter(cmdExec);
        }

        // /santaadmin
        if (getCommand("santaadmin") != null) {
            getCommand("santaadmin").setExecutor(
                    new SantaAdminCommand(this, santaManager)
            );
        }

        // /ucgift (optional helper)
        if (getCommand("ucgift") != null) {
            getCommand("ucgift").setExecutor(new DailyGiftCommand(this));
        }

        // /ultimatechristmas (or alias /uc) – reload command
        if (getCommand("ultimatechristmas") != null) {
            UltimateChristmasCommand mainCmd = new UltimateChristmasCommand(this);
            getCommand("ultimatechristmas").setExecutor(mainCmd);
        }

        /*
         * 9) Start Santa's repeating task
         */
        santaManager.startScheduler();

        Msg.info("ultimateChristmas enabled. Ho ho ho!");
    }

    @Override
    public void onDisable() {
        /*
         * Graceful shutdown:
         * - Despawn Santa and cancel his timers
         * - Stop any music loops
         * - Persist shard balances & daily progress
         */
        if (santaManager != null) {
            santaManager.despawnSantaIfAny();
        }

        if (musicManager != null) {
            musicManager.stopAll();
        }

        if (shardManager != null) {
            shardManager.save();
        }

        if (dailyProgressStore != null) {
            dailyProgressStore.save();
        }

        Msg.info("ultimateChristmas disabled.");
    }

    /* -------------------------------------------------
     * RELOAD LOGIC
     * ------------------------------------------------- */

    /**
     * Reload all plugin configs and refresh managers that support it.
     */
    public void reloadAll() {
        // 1) Reload all YMLs
        ConfigUtil.reloadAll(this);

        // 2) Re-init debug + lang
        Debug.init(this);
        Lang.reload();

        // 3) Managers that cache config
        if (shardManager != null) {
            shardManager.reloadConfig();
        }
        if (snowEffectManager != null) {
            snowEffectManager.reloadConfig();
        }
        if (musicManager != null) {
            musicManager.reloadTracks();      // ← HERE
        }

        getLogger().info("[UltimateChristmas] All configs reloaded.");
    }


    /* -------------------------------------------------
     * WALK CONTROLLER HOOK
     * ------------------------------------------------- */

    /**
     * Called by SantaManager when Paper async walking AI is started/stopped.
     */
    public void setActiveWalkController(SantaWalkController controller) {
        if (this.activeWalkController != null) {
            this.activeWalkController.stopWalking();
        }
        this.activeWalkController = controller;
    }

    /* -------------------------------------------------
     * GETTERS / HELPERS
     * ------------------------------------------------- */

    /**
     * Wrapper to get custom YMLs we loaded with ConfigUtil.load(...).
     * Example: getConfig("shop.yml"), getConfig("santa.yml")
     */
    public FileConfiguration getConfig(String fileName) {
        return ConfigUtil.get(this, fileName);
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public ShardShopGUI getShardShopGUI() {
        return shardShopGUI;
    }

    public DailyGiftManager getDailyGiftManager() {
        return dailyGiftManager;
    }

    public DailyProgressStore getDailyProgressStore() {
        return dailyProgressStore;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public WorldGuardIntegration getWgIntegration() {
        return wgIntegration;
    }

    public SantaManager getSantaManager() {
        return santaManager;
    }

    public InventoryManager getInvManager() {
        return invManager;
    }

    public GrinchBossManager getGrinchBossManager() {
        return grinchBossManager;
    }

    /**
     * Convenience so other plugins/listeners can ask
     * "is this entity THE Santa NPC?"
     */
    public boolean isSantaEntity(org.bukkit.entity.Entity e) {
        return santaManager != null && santaManager.isSanta(e);
    }

    /**
     * Convenience so other plugins/listeners can ask
     * "is this entity THE Grinch boss?"
     */
    public boolean isGrinchEntity(org.bukkit.entity.Entity e) {
        try {
            return grinchBossManager != null && grinchBossManager.isGrinch(e);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * True if server looks like Paper/Purpur and supports Mob#getPathfinder().
     */
    public boolean hasPaperPathfinder() {
        return paperPathfinderAvailable;
    }

    /**
     * Check if optional plugin is present & enabled (WorldGuard, LibsDisguises, etc).
     */
    private boolean isPluginEnabled(String name) {
        Plugin p = getServer().getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    /**
     * Detect whether Paper's pathfinder API exists on this server build.
     */
    private boolean checkPaperPathfinder() {
        try {
            Class.forName("org.bukkit.entity.Mob").getMethod("getPathfinder");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
