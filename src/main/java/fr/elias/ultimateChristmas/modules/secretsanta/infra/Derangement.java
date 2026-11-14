package fr.elias.ultimateChristmas.modules.secretsanta.infra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pairs each participant with a different participant to buy for.
 *
 * <p>Produces a derangement: a permutation where nobody is assigned to
 * themselves, which is exactly the Secret Santa constraint. A simple shuffle is
 * rejected and retried if it leaves anyone paired with themselves, which is
 * overwhelmingly rare past a handful of players and bounded by a retry cap.
 */
public final class Derangement {

    private Derangement() {
    }

    /**
     * Assigns givers to receivers.
     *
     * @return a map of giver to receiver, or {@code null} if fewer than two
     *         participants were supplied (no exchange is possible)
     */
    public static Map<UUID, UUID> of(List<UUID> participants) {
        if (participants.size() < 2) {
            return null;
        }

        List<UUID> receivers = new ArrayList<>(participants);
        for (int attempt = 0; attempt < 100; attempt++) {
            Collections.shuffle(receivers, ThreadLocalRandom.current());
            if (isDerangement(participants, receivers)) {
                Map<UUID, UUID> pairing = new LinkedHashMap<>();
                for (int index = 0; index < participants.size(); index++) {
                    pairing.put(participants.get(index), receivers.get(index));
                }
                return pairing;
            }
        }

        // Fallback that always succeeds: rotate by one, so everyone gives to the
        // next person in the list and nobody gives to themselves.
        Map<UUID, UUID> pairing = new LinkedHashMap<>();
        for (int index = 0; index < participants.size(); index++) {
            pairing.put(participants.get(index),
                    participants.get((index + 1) % participants.size()));
        }
        return pairing;
    }

    private static boolean isDerangement(List<UUID> givers, List<UUID> receivers) {
        for (int index = 0; index < givers.size(); index++) {
            if (givers.get(index).equals(receivers.get(index))) {
                return false;
            }
        }
        return true;
    }
}
