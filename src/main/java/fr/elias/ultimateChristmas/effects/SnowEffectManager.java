package fr.elias.ultimateChristmas.effects;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Player-local snowfall manager:
 * - Particles always visible around the player (unless under a roof, if respect-roof: true).
 * - Thin ground snow layers placed near the player, except in blocked WG regions or under a roof.
 * - Safe-zones near players and interactables to avoid misclicks.
 * - Toggleable per player via /snowify (handled by SnowCommand).
 */
public class SnowEffectManager implements Listener {

    private final UltimateChristmas plugin;

    // Runtime state
    private final Set<Player> activePlayers = new HashSet<>();
    private final Set<Player> optedOut = new HashSet<>();
    private final Set<Material> bannedBaseBlocks = EnumSet.noneOf(Material.class);

    // Config (mutable so we can reload)
    private boolean snowEnabled;
    private boolean applyToAllPlayers;
    private boolean keepSnowOnDisable;
    private int radius;
    private int particleDensity;
    private long durationTicks;
    private double noSnowNearPlayer;
    private double noSnowNearEntity;

    private boolean biasFront;
    private double lookAheadFactor;

    // Ground placement rules
    private boolean respectRoof;          // do not place/show snow when player is under a cover
    private Set<String> blockedWGRegions; // WG regions where ground snow is blocked

    // ----------------------------------------------------------------------------------------------------
    // CONSTRUCTOR + CONFIG LOAD
    // ----------------------------------------------------------------------------------------------------

    public SnowEffectManager(UltimateChristmas plugin) {
        this.plugin = plugin;

        // initial load
        loadFromConfig();

        // register events once
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Auto-enable around currently online players if globally enabled
        if (snowEnabled && applyToAllPlayers) {
            Bukkit.getScheduler().runTaskLater(plugin, this::enableForOnlinePlayers, 40L);
        }
    }

    /**
     * Called from UltimateChristmas.reloadAll()
     */
    public void reloadConfig() {
        loadFromConfig();

        // If snow was disabled in config while some players had it active → stop their tasks
        if (!snowEnabled) {
            for (Player p : new HashSet<>(activePlayers)) {
                disableFor(p, !keepSnowOnDisable);
            }
        } else if (snowEnabled && applyToAllPlayers) {
            // If it just got enabled or radius changed etc., re-apply to online players
            enableForOnlinePlayers();
        }
    }

    private void loadFromConfig() {
        FileConfiguration cfg = plugin.getConfig("snow.yml");
        if (cfg == null) {
            plugin.getLogger().warning("[Snow] Could not load snow.yml (FileConfiguration is null).");
            return;
        }

        // clear sets
        bannedBaseBlocks.clear();
        if (blockedWGRegions == null) {
            blockedWGRegions = new HashSet<>();
        } else {
            blockedWGRegions.clear();
        }

        // Snow main section
        this.snowEnabled        = cfg.getBoolean("snow.enabled", true);
        this.applyToAllPlayers  = cfg.getBoolean("snow.apply-to-all-players", true);
        this.keepSnowOnDisable  = cfg.getBoolean("snow.keep-snow-on-disable", true);

        this.radius             = Math.max(2, cfg.getInt("snow.radius", 24));
        this.particleDensity    = Math.max(0, cfg.getInt("snow.density", 40));

        int seconds             = Math.max(0, cfg.getInt("snow.duration", 0));
        this.durationTicks      = (seconds == 0 ? 0L : seconds * 20L);

        this.noSnowNearPlayer   = Math.max(0.25, cfg.getDouble("snow.no-snow-near-player-radius", 1.5));
        this.noSnowNearEntity   = Math.max(0.25, cfg.getDouble("snow.no-snow-near-entity-radius", 1.25));

        // Presnow bias
        this.biasFront          = cfg.getBoolean("snow.presnow.bias-front", true);
        this.lookAheadFactor    = Math.min(2.0, Math.max(0.0, cfg.getDouble("snow.presnow.lookahead-factor", 0.66)));

        // Ground placement rules
        this.respectRoof        = cfg.getBoolean("snow.respect-roof", true);
        this.blockedWGRegions.addAll(cfg.getStringList("snow.blocked-wg-regions"));

        // Banned base blocks for snow layers
        for (String raw : cfg.getStringList("snowify.banned-blocks")) {
            Material mat = Material.matchMaterial(raw);
            if (mat != null) {
                bannedBaseBlocks.add(mat);
            } else {
                plugin.getLogger().warning("[Snow] Unknown banned-block: " + raw);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // PUBLIC API (used by /snowify)
    // ----------------------------------------------------------------------------------------------------

    /** Toggle the effect for a specific player. */
    public void toggleSnowCommand(Player player) {
        if (optedOut.contains(player)) {
            optedOut.remove(player);
            player.sendMessage("§b❄ Snow effect re-enabled.");
            enableFor(player);
            return;
        }
        if (activePlayers.contains(player)) {
            disableFor(player, !keepSnowOnDisable);
            optedOut.add(player);
            player.sendMessage("§7☁ Snow effect disabled for you.");
            return;
        }
        enableFor(player);
        player.sendMessage("§f❄ Snow begins to fall around you...");
    }

    // ----------------------------------------------------------------------------------------------------
    // JOIN AUTO-ENABLE
    // ----------------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (snowEnabled && applyToAllPlayers && !optedOut.contains(p)) {
            enableFor(p);
        }
    }

    public void enableForOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!optedOut.contains(p)) enableFor(p);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // CORE LOOP
    // ----------------------------------------------------------------------------------------------------

    private void enableFor(Player player) {
        if (!snowEnabled) return;
        if (activePlayers.contains(player)) return;

        activePlayers.add(player);

        new BukkitRunnable() {
            long ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !activePlayers.contains(player)) {
                    cancel();
                    return;
                }

                // If config got turned off while task is running, stop gracefully
                if (!snowEnabled) {
                    disableFor(player, !keepSnowOnDisable);
                    cancel();
                    return;
                }

                // 1) Particles (respect-roof: if true and under cover -> no particles OR ground snow)
                if (!(respectRoof && isUnderCover(player))) {
                    spawnSnowParticles(player);

                    // 2) Place thin ground snow (every 10 ticks), but NEVER inside blocked WG regions
                    if ((ticks % 10) == 0) {
                        applyPreSnow(player);
                    }
                }

                ticks += 2;

                // 3) Optional auto-timeout per player
                if (durationTicks > 0 && ticks > durationTicks) {
                    disableFor(player, !keepSnowOnDisable);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void disableFor(Player player, boolean cleanupArea) {
        activePlayers.remove(player);
        if (cleanupArea) {
            revertThinSnowNear(player);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // PARTICLES
    // ----------------------------------------------------------------------------------------------------

    private void spawnSnowParticles(Player player) {
        if (particleDensity <= 0) return;
        World world = player.getWorld();
        Location base = player.getLocation();

        for (int i = 0; i < particleDensity; i++) {
            double x = base.getX() + (Math.random() * radius * 2 - radius);
            double y = base.getY() + 3.0 + Math.random() * 2.5;
            double z = base.getZ() + (Math.random() * radius * 2 - radius);
            world.spawnParticle(Particle.SNOWFLAKE, x, y, z, 1, 0, 0, 0, 0.01);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // GROUND PRE-SNOW
    // ----------------------------------------------------------------------------------------------------

    private void applyPreSnow(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();

        int px = loc.getBlockX();
        int pz = loc.getBlockZ();

        // Optional look-ahead shaping (places more in front of the player)
        Vector dir = loc.getDirection().normalize();
        int lookAheadX = biasFront ? (int) (dir.getX() * (radius * lookAheadFactor)) : 0;
        int lookAheadZ = biasFront ? (int) (dir.getZ() * (radius * lookAheadFactor)) : 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                // Light “front bias” mask (skip some behind the player)
                if (biasFront && dx < -radius / 2 && Math.abs(dz) < radius / 2) continue;

                int bx = px + dx + lookAheadX;
                int bz = pz + dz + lookAheadZ;

                // Keep a small click-safe ring around the player's feet
                if (Math.abs(bx - px) <= noSnowNearPlayer && Math.abs(bz - pz) <= noSnowNearPlayer) continue;

                Block ground = world.getHighestBlockAt(bx, bz);
                Block top = ground.getRelative(0, 1, 0);

                if (!ground.getType().isSolid() || ground.isLiquid()) continue;
                if (top.getType() != Material.AIR) continue;

                // Never place snow on banned base blocks (stairs, slabs, specific blocks)
                if (isBannedBase(ground)) continue;

                // DO NOT place ground snow inside any configured WG region
                if (isInsideBlockedWGRegion(top.getLocation().add(0.5, 0.0, 0.5))) continue;

                // Avoid interactables and Santa (so we don't steal right-clicks)
                if (isNearInteractable(top.getLocation().add(0.5, 0.5, 0.5), noSnowNearEntity, 1.5)) continue;

                top.setType(Material.SNOW, false); // thin layer
            }
        }
    }

    /**
     * True if we must NOT place snow on top of this base block.
     * - Config-driven list (banned-blocks in snow.yml)
     * - Automatically bans all *_SLAB and *_STAIRS.
     */
    private boolean isBannedBase(Block base) {
        if (base == null) return true;
        Material type = base.getType();

        // Explicit list from config
        if (bannedBaseBlocks.contains(type)) return true;

        // Auto-block all slabs & stairs so you don’t have to list every variant
        String name = type.name();
        return name.endsWith("_SLAB") || name.endsWith("_STAIRS");
    }

    private boolean isNearInteractable(Location center, double xr, double yr) {
        World w = center.getWorld();
        if (w == null) return false;

        for (Entity e : w.getNearbyEntities(center, xr, yr, xr)) {
            if (isInteractableOrSanta(e)) return true;
        }
        return false;
    }

    private boolean isInteractableOrSanta(Entity e) {
        // Treat our Santa as an interactable target as well
        try {
            UltimateChristmas uc = UltimateChristmas.get();
            if (uc != null && uc.isSantaEntity(e)) return true;
        } catch (Throwable ignored) {}

        EntityType type = e.getType();
        if (e instanceof Villager) return true;
        if (e instanceof ArmorStand) return true;
        if (e instanceof ItemFrame) return true;
        if (type.name().equalsIgnoreCase("GLOW_ITEM_FRAME")) return true; // version-safe
        return isInteractionEntity(e);
    }

    private boolean isInteractionEntity(Entity e) {
        // Version-safe check for the Interaction entity (no hard dependency)
        return e.getType().name().equalsIgnoreCase("INTERACTION");
    }

    // ----------------------------------------------------------------------------------------------------
    // CLEANUP
    // ----------------------------------------------------------------------------------------------------

    private void revertThinSnowNear(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        int px = base.getBlockX();
        int pz = base.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block top = world.getHighestBlockAt(px + dx, pz + dz);
                if (top.getType() == Material.SNOW) {
                    top.setType(Material.AIR, false); // only remove thin layer, not SNOW_BLOCK
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------------------------------------------

    /**
     * True if any non-air block exists above the player's head (i.e., under a roof).
     */
    private boolean isUnderCover(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        if (w == null) return false;

        int x = l.getBlockX();
        int z = l.getBlockZ();
        int startY = Math.min(w.getMaxHeight() - 1, l.getBlockY() + 1);

        for (int y = startY; y < w.getMaxHeight(); y++) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR) return true;
        }
        return false;
    }

    /**
     * Returns true if the location is inside any configured WorldGuard region to block ground snow.
     * Particles are NOT affected by this — only ground placement is skipped.
     */
    private boolean isInsideBlockedWGRegion(Location loc) {
        if (blockedWGRegions == null || blockedWGRegions.isEmpty()
                || loc == null || loc.getWorld() == null) {
            return false;
        }

        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

            for (var region : set.getRegions()) {
                String id = region.getId();
                for (String blocked : blockedWGRegions) {
                    if (id.equalsIgnoreCase(blocked)) return true;
                }
            }
        } catch (Throwable ignored) {
            // Stay resilient to WG API changes
        }
        return false;
    }
}
