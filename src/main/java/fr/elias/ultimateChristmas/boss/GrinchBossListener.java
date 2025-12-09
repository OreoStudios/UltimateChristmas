package fr.elias.ultimateChristmas.boss;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Player;

public class GrinchBossListener implements Listener {

    private final GrinchBossManager mgr;

    public GrinchBossListener(UltimateChristmas plugin, GrinchBossManager mgr) {
        this.mgr = mgr;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!mgr.isGrinch(e.getEntity())) return;
        if (e.getDamager() instanceof Player p) {
            mgr.recordDamage(p, e.getFinalDamage(), e.getCause());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!mgr.isGrinch(e.getEntity())) return;

        // prevent vanilla drops/xp
        e.getDrops().clear();
        e.setDroppedExp(0);

        mgr.handleDeathAndRewards(e.getEntity().getLocation());
    }
}
