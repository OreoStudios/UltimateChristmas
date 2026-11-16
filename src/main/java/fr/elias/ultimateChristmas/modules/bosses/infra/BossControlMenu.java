package fr.elias.ultimateChristmas.modules.bosses.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.menu.PagedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The boss control center: an admin GUI to spawn, inspect and despawn bosses.
 *
 * <p>One icon per configured boss, showing its live state — whether it is out,
 * its current health, how many players are fighting it and the top damage so
 * far — with click actions to run it. It repaints itself on a short timer-free
 * basis: each action refreshes the view, and a manual refresh button re-reads
 * the live fight state.
 *
 * <p>Left-click spawns the boss at the admin's feet (or despawns it if it is
 * already out), right-click despawns it, and shift-click teleports the admin to
 * a live boss. This replaces hunting for the boss and typing coordinates.
 */
public final class BossControlMenu extends PagedMenu {

    private final BossControl control;

    public BossControlMenu(BossControl control) {
        this.control = control;
    }

    @Override
    public Component title(Player player) {
        long active = control.types().stream()
                .filter(type -> control.activeFight(type.id()) != null)
                .count();
        return Component.text("Boss Control  (" + active + " active)", NamedTextColor.DARK_RED);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.RED_STAINED_GLASS_PANE).name(" ").build());

        view.set(MenuView.slot(0, 4), MenuItem.display(ItemBuilder.of(Material.WITHER_SKELETON_SKULL)
                .name("<dark_red><bold>Boss Control Center")
                .addLore("<gray>Left-click a boss to summon it at your feet")
                .addLore("<gray>Right-click to send it away")
                .addLore("<gray>Shift-click to teleport to a live boss")
                .addBlankLore()
                .addLore(control.modelPluginAvailable()
                        ? "<green>Custom model rendering is available"
                        : "<dark_gray>No custom model renderer is available")
                .hideAll()
                .build()));

        view.set(MenuView.slot(5, 4), MenuItem.of(
                ItemBuilder.of(Material.CLOCK).name("<yellow>Refresh").build(),
                click -> click.view().refresh()));

        view.set(MenuView.slot(5, 8), MenuItem.of(
                ItemBuilder.of(Material.BARRIER).name("<red>Despawn all bosses").build(),
                click -> {
                    int removed = 0;
                    for (BossType type : control.types()) {
                        removed += control.despawn(type.id());
                    }
                    control.platform().messages().sendRaw(click.player(),
                            "<green>Despawned <white>" + removed + "</white> boss(es).",
                            java.util.Map.of());
                    click.view().refresh();
                }));
    }

    @Override
    protected List<MenuItem> entries(MenuView view) {
        List<MenuItem> items = new ArrayList<>();
        for (BossType type : control.types()) {
            BossFight fight = control.activeFight(type.id());
            items.add(bossIcon(type, fight));
        }
        return items;
    }

    private MenuItem bossIcon(BossType type, BossFight fight) {
        boolean active = fight != null;
        ItemBuilder builder = ItemBuilder.of(iconFor(type))
                .name(type.displayName())
                .addLore("<dark_gray>id: " + type.id())
                .addLore("<gray>Mob: <white>" + type.entityType().name().toLowerCase(Locale.ROOT))
                .addLore("<gray>Health: <white>" + (long) type.health())
                .addLore("<gray>Abilities: <white>"
                        + (type.abilities().isEmpty() ? "none"
                           : String.join(", ", type.abilities())))
                .addLore("<gray>Model: " + (type.hasModel()
                        ? "<aqua>" + type.model() + " <dark_gray>(" + providerLabel(type) + ")"
                        : "<dark_gray>vanilla"))
                .addBlankLore();

        if (active) {
            builder.addLore("<green>● ACTIVE")
                    .addLore("<gray>Health: <red>" + (long) Math.ceil(
                            fight.healthFraction() * fight.maxHealth())
                            + "<dark_gray>/" + (long) fight.maxHealth())
                    .addLore("<gray>Fighters: <white>" + fight.contributorCount())
                    .addLore("<gray>Damage done: <white>"
                            + EconomyService.format(Math.round(fight.totalDamage())))
                    .addBlankLore()
                    .addLore("<yellow>Left-click: despawn")
                    .addLore("<yellow>Shift-click: teleport to it")
                    .glint(true);
        } else {
            builder.addLore("<dark_gray>○ not active")
                    .addBlankLore()
                    .addLore("<green>Left-click: summon at your feet")
                    .addLore(type.spawnLocation() != null
                            ? "<dark_gray>(has a fixed spawn in config)"
                            : "<dark_gray>(spawns where you stand)");
        }

        return MenuItem.of(builder.hideAll().build(), click -> handleClick(click, type, fight));
    }

    private void handleClick(MenuItem.Click click, BossType type, BossFight fight) {
        Player admin = click.player();
        boolean active = control.activeFight(type.id()) != null;

        if (click.shift() && active) {
            BossFight live = control.activeFight(type.id());
            if (live != null) {
                admin.teleport(live.location());
                admin.playSound(admin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.0f);
            }
            return;
        }

        if (active) {
            int removed = control.despawn(type.id());
            control.platform().messages().sendRaw(admin,
                    "<green>Despawned <white>" + removed + "</white> " + type.id() + ".",
                    java.util.Map.of());
        } else {
            boolean spawned = control.spawn(type.id(),
                    type.spawnLocation() != null ? type.spawnLocation() : admin.getLocation());
            if (!spawned) {
                control.platform().messages().sendRaw(admin,
                        "<red>Could not spawn " + type.id() + ".", java.util.Map.of());
            }
        }
        click.view().refresh();
    }

    /** A thematic icon per boss, falling back to a skull. */
    private Material iconFor(BossType type) {
        return switch (type.id().toLowerCase(Locale.ROOT)) {
            case "grinch" -> Material.ZOMBIE_HEAD;
            case "krampus" -> Material.WITHER_SKELETON_SKULL;
            case "frost_monarch", "frostmonarch" -> Material.SKELETON_SKULL;
            default -> Material.CREEPER_HEAD;
        };
    }

    private String providerLabel(BossType type) {
        return switch (type.modelProvider()) {
            case DISPLAY -> "built-in";
            case MODEL_ENGINE -> "ModelEngine";
            case BETTER_MODEL -> "BetterModel";
            case AUTO -> "auto";
            case NONE -> "off";
        };
    }
}
