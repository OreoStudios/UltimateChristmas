package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.modules.secretsanta.infra.Derangement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Secret Santa pairing must never hand anyone their own name, and must
 * cover everyone exactly once. Both are easy to get subtly wrong, so they are
 * pinned here across a range of group sizes and many trials.
 */
class DerangementTest {

    @Test
    void fewerThanTwoCannotBePaired() {
        assertNull(Derangement.of(List.of()));
        assertNull(Derangement.of(List.of(UUID.randomUUID())));
    }

    @Test
    void nobodyIsEverPairedWithThemselves() {
        for (int size = 2; size <= 30; size++) {
            List<UUID> participants = participants(size);
            for (int trial = 0; trial < 50; trial++) {
                Map<UUID, UUID> pairing = Derangement.of(participants);
                pairing.forEach((giver, receiver) ->
                        assertNotEquals(giver, receiver,
                                "a player was assigned to themselves"));
            }
        }
    }

    @Test
    void everyoneGivesAndEveryoneReceivesExactlyOnce() {
        List<UUID> participants = participants(12);
        Map<UUID, UUID> pairing = Derangement.of(participants);

        assertEquals(participants.size(), pairing.size(),
                "not every participant is a giver");
        Set<UUID> receivers = new HashSet<>(pairing.values());
        assertEquals(participants.size(), receivers.size(),
                "a receiver appears twice, or someone receives nothing");
        assertEquals(new HashSet<>(participants), receivers,
                "the set of receivers is not the set of participants");
    }

    private static List<UUID> participants(int size) {
        List<UUID> list = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            list.add(UUID.randomUUID());
        }
        return list;
    }
}
