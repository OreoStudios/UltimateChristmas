package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.core.reward.Amount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The amount parser is the shared basis of every "how many shards" field in
 * every config, so its edge cases are worth pinning down.
 */
class AmountTest {

    @Test
    void parsesAPlainNumber() {
        Amount amount = Amount.parse(5);
        assertEquals(5, amount.min());
        assertEquals(5, amount.max());
        assertEquals(5, amount.roll());
    }

    @Test
    void parsesANumericString() {
        assertEquals(42, Amount.parse("42").roll());
    }

    @Test
    void parsesAHyphenRange() {
        Amount amount = Amount.parse("5-10");
        assertEquals(5, amount.min());
        assertEquals(10, amount.max());
    }

    @Test
    void parsesADoubleDotRange() {
        Amount amount = Amount.parse("50..120");
        assertEquals(50, amount.min());
        assertEquals(120, amount.max());
    }

    @Test
    void readsANegativeRangeRatherThanFailingToSplit() {
        Amount amount = Amount.parse("-5--1");
        assertEquals(-5, amount.min());
        assertEquals(-1, amount.max());
    }

    @Test
    void normalisesAReversedRange() {
        Amount amount = Amount.parse("10-5");
        assertEquals(5, amount.min());
        assertEquals(10, amount.max());
    }

    @Test
    void rollStaysWithinRange() {
        Amount amount = Amount.parse("3-7");
        for (int attempt = 0; attempt < 500; attempt++) {
            long value = amount.roll();
            assertTrue(value >= 3 && value <= 7, "roll out of range: " + value);
        }
    }

    @Test
    void unreadableValuesBecomeZero() {
        assertTrue(Amount.parse("not a number").isZero());
        assertTrue(Amount.parse("").isZero());
        assertTrue(Amount.parse(null).isZero());
    }

    @Test
    void zeroIsRecognised() {
        assertTrue(Amount.parse(0).isZero());
        assertFalse(Amount.parse(1).isZero());
    }
}
