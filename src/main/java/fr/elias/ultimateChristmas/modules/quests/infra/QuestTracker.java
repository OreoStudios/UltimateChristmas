package fr.elias.ultimateChristmas.modules.quests.infra;

import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.progress.ProgressService;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.quests.api.QuestCompletedEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Advances objectives as players do things.
 *
 * <p>Subscribes to the central progress service rather than listening to the
 * game directly, which is what keeps objectives decoupled: "throw 20
 * snowballs" is satisfied by the snowball module recording a hit, with no
 * dependency in either direction.
 *
 * <p>Objectives reset lazily. The cycle a player's progress belongs to is
 * stored alongside it, so the first progress after a rollover wipes the stale
 * counters. That avoids a scheduled task that would have to walk every profile
 * — including offline ones — at midnight.
 */
public final class QuestTracker implements ProgressService.Listener {

    private static final String DAILY_CYCLE_STAT = "quest_cycle_daily";
    private static final String WEEKLY_CYCLE_STAT = "quest_cycle_weekly";

    private final ChristmasPlatform platform;
    private final QuestCatalog catalog;
    private final Consumer<Object> emit;

    public QuestTracker(ChristmasPlatform platform, QuestCatalog catalog,
                        Consumer<Object> emit) {
        this.platform = platform;
        this.catalog = catalog;
        this.emit = emit;
    }

    @Override
    public void onProgress(Player player, String stat, long delta, long total) {
        if (catalog.isEmpty() || delta <= 0) {
            return;
        }
        var matching = catalog.tracking(stat);
        if (matching.isEmpty()) {
            return;
        }
        PlayerProfile profile = platform.profiles().get(player);
        rollOverIfNeeded(profile);

        for (QuestCatalog.Quest quest : matching) {
            if (profile.questCompleted(quest.id())) {
                continue;
            }
            long progress = profile.addQuestProgress(quest.id(), delta);
            if (progress < quest.goal()) {
                continue;
            }
            complete(player, profile, quest);
        }
    }

    private void complete(Player player, PlayerProfile profile, QuestCatalog.Quest quest) {
        profile.completeQuest(quest.id());
        if (quest.reward() != null) {
            platform.rewards().grant(player, quest.reward(), Messages.of(
                    "quest", quest.name()));
        }
        platform.messages().send(player, "quests.completed",
                Messages.of("name", Text.formatted(quest.name())));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        Fx.burst(player.getLocation().add(0, 1, 0), org.bukkit.Particle.TOTEM_OF_UNDYING,
                16, 0.4);
        emit.accept(new QuestCompletedEvent(player.getUniqueId(), quest.id(),
                quest.scope().name().toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * Clears daily and weekly objectives whose cycle has passed.
     *
     * <p>Called before any progress is recorded, so a player who returns after
     * a week starts from zero rather than inheriting last week's counters.
     */
    public void rollOverIfNeeded(PlayerProfile profile) {
        long today = platform.season().todayEpochDay();
        long thisWeek = today / 7;

        if (profile.stat(DAILY_CYCLE_STAT) != today) {
            resetScope(profile, QuestCatalog.Scope.DAILY);
            profile.setStat(DAILY_CYCLE_STAT, today);
        }
        if (profile.stat(WEEKLY_CYCLE_STAT) != thisWeek) {
            resetScope(profile, QuestCatalog.Scope.WEEKLY);
            profile.setStat(WEEKLY_CYCLE_STAT, thisWeek);
        }
    }

    private void resetScope(PlayerProfile profile, QuestCatalog.Scope scope) {
        Set<String> ids = new HashSet<>();
        for (QuestCatalog.Quest quest : catalog.inScope(scope)) {
            ids.add(quest.id());
        }
        if (!ids.isEmpty()) {
            profile.resetQuests(ids);
        }
    }

    /** Wipes every objective for a player, for the admin reset command. */
    public void resetAll(PlayerProfile profile) {
        profile.resetQuests(new HashSet<>(catalog.ids()));
        profile.setStat(DAILY_CYCLE_STAT, 0);
        profile.setStat(WEEKLY_CYCLE_STAT, 0);
    }
}
