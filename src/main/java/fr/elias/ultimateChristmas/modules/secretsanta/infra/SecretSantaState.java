package fr.elias.ultimateChristmas.modules.secretsanta.infra;

import dev.oreo.modulith.core.ModuleConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server state for the Secret Santa exchange.
 *
 * <p>The participant list and whether pairings have been drawn are shared
 * state, so they live in the module config. Each player's assigned target lives
 * on their own profile instead, because it is per-player and must be readable
 * for an offline player when their gift arrives.
 *
 * <p>Pending gifts for players who are offline are held here too, serialised as
 * item stacks, and handed over on their next join.
 */
public final class SecretSantaState {

    private final ModuleConfiguration config;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private boolean drawn;

    public SecretSantaState(ModuleConfiguration config) {
        this.config = config;
        read();
    }

    public void read() {
        participants.clear();
        for (String raw : config.getStringList("state.participants")) {
            try {
                participants.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed entry rather than failing the whole draw.
            }
        }
        this.drawn = config.getBoolean("state.drawn", false);
    }

    public boolean joined(UUID id) {
        return participants.contains(id);
    }

    public boolean join(UUID id) {
        if (!participants.add(id)) {
            return false;
        }
        persistParticipants();
        return true;
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public int participantCount() {
        return participants.size();
    }

    public boolean drawn() {
        return drawn;
    }

    public void markDrawn(boolean value) {
        this.drawn = value;
        config.set("state.drawn", value);
        config.save();
    }

    /** Clears everything: participants, the drawn flag and pending gifts. */
    public void reset() {
        participants.clear();
        drawn = false;
        config.set("state.participants", new ArrayList<String>());
        config.set("state.drawn", false);
        config.set("pending", null);
        config.save();
    }

    private void persistParticipants() {
        List<String> ids = new ArrayList<>();
        participants.forEach(id -> ids.add(id.toString()));
        config.set("state.participants", ids);
        config.save();
    }

    // ---------------------------------------------------------- pending gifts

    /** Holds a gift for a recipient who is offline. */
    public void storePending(UUID recipient, ItemStack gift, String fromHint) {
        String base = "pending." + recipient;
        config.set(base + ".item", gift);
        config.set(base + ".from", fromHint);
        config.save();
    }

    /** The gift waiting for a recipient, or {@code null}. */
    public ItemStack takePending(UUID recipient) {
        Object node = config.get("pending." + recipient + ".item");
        if (!(node instanceof ItemStack gift)) {
            return null;
        }
        config.set("pending." + recipient, null);
        config.save();
        return gift;
    }

    public boolean hasPending(UUID recipient) {
        return config.get("pending." + recipient + ".item") instanceof ItemStack;
    }

    public String pendingFrom(UUID recipient) {
        Object node = config.get("pending." + recipient);
        if (node instanceof ConfigurationSection section) {
            return section.getString("from", "someone");
        }
        return "someone";
    }
}
