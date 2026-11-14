package fr.elias.ultimateChristmas.modules.pass.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.menu.PagedMenu;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The festive pass track, one icon per tier.
 *
 * <p>Clicking an unlocked, unclaimed tier claims it. Everything else explains
 * itself in the lore, so a player can see what is coming and what they need.
 */
public final class PassMenu extends PagedMenu {

    private final ChristmasPlatform platform;
    private final PassTrack track;
    /** Called with the player and tier to claim; reports its own failures. */
    private final BiConsumer<Player, Integer> claim;

    public PassMenu(ChristmasPlatform platform, PassTrack track,
                    BiConsumer<Player, Integer> claim) {
        this.platform = platform;
        this.track = track;
        this.claim = claim;
    }

    @Override
    public Component title(Player player) {
        return Component.text("Festive pass", NamedTextColor.GOLD);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.ORANGE_STAINED_GLASS_PANE).name(" ").build());

        PlayerProfile profile = platform.profiles().get(view.player());
        long xp = profile.passXp();
        int tier = track.tierFor(xp);
        long next = track.nextRequirement(xp);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Tier: <white>" + tier + "<dark_gray>/" + track.maxTier());
        lore.add("<gray>Experience: <aqua>" + xp);
        lore.add(next < 0
                ? "<green>You have reached the top tier."
                : "<gray>Next tier at <aqua>" + next + " <dark_gray>(" + (next - xp) + " to go)");
        lore.add("");
        lore.add(profile.passPremium()
                ? "<gold>Premium pass: <green>yes"
                : "<gold>Premium pass: <gray>no");

        view.set(MenuView.slot(0, 4), MenuItem.display(ItemBuilder.of(Material.NETHER_STAR)
                .name("<gold><bold>Your pass")
                .lore(lore)
                .hideAll()
                .build()));
    }

    @Override
    protected List<MenuItem> entries(MenuView view) {
        PlayerProfile profile = platform.profiles().get(view.player());
        long xp = profile.passXp();
        int reached = track.tierFor(xp);

        List<MenuItem> items = new ArrayList<>();
        for (PassTrack.Tier tier : track.all()) {
            boolean unlocked = tier.tier() <= reached;
            boolean claimed = profile.hasClaimedTier(tier.tier());

            Material icon = claimed ? Material.LIME_DYE
                    : unlocked ? Material.CHEST : Material.GRAY_DYE;

            ItemBuilder builder = ItemBuilder.of(icon)
                    .name((claimed ? "<green>" : unlocked ? "<yellow>" : "<dark_gray>")
                            + "Tier " + tier.tier())
                    .addLore("<gray>Needs <aqua>" + tier.xp() + "</aqua> experience");

            describe(builder, "Free", tier.free());
            if (tier.premium() != null) {
                describe(builder, profile.passPremium() ? "Premium" : "Premium (locked)",
                        tier.premium());
            }

            builder.addBlankLore();
            if (claimed) {
                builder.addLore("<green>Claimed").glint(true);
            } else if (unlocked) {
                builder.addLore("<yellow>Click to claim");
            } else {
                builder.addLore("<red>" + (tier.xp() - xp) + " more experience");
            }

            var item = builder.amount(Math.max(1, Math.min(64, tier.tier())))
                    .hideAll()
                    .build();

            if (!unlocked || claimed) {
                items.add(MenuItem.display(item));
                continue;
            }
            items.add(MenuItem.of(item, click -> {
                claim.accept(click.player(), tier.tier());
                click.player().playSound(click.player().getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                click.view().refresh();
            }));
        }
        return items;
    }

    /** Summarises a reward in a few lore lines. */
    private void describe(ItemBuilder builder, String label,
                          fr.elias.ultimateChristmas.core.reward.Reward reward) {
        if (reward == null) {
            return;
        }
        builder.addBlankLore().addLore("<gold>" + label + ":");
        if (!reward.shards().isZero()) {
            builder.addLore("<dark_gray>- <gold>" + reward.shards() + " shards");
        }
        if (!reward.xp().isZero()) {
            builder.addLore("<dark_gray>- <green>" + reward.xp() + " xp");
        }
        for (var item : reward.items()) {
            builder.addLore("<dark_gray>- <white>" + item.getAmount() + "x "
                    + item.getType().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!reward.consoleCommands().isEmpty() || !reward.playerCommands().isEmpty()) {
            builder.addLore("<dark_gray>- <light_purple>a special reward");
        }
    }
}
