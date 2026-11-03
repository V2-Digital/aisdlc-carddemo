package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InterestTest {
    @Test
    void truncatesPositive() {
        // 1164.87 * 15.00 / 1200 = 14.5609 -> 14.56 (not rounded; 14.5609 would round to the same, see next)
        assertEquals(1456, Cbact04c.monthlyInterest(116487, 1500));
    }

    @Test
    void truncatesPositiveWhereRoundingWouldDiffer() {
        // 100.00 * 1.99 / 1200 = 0.16583 -> 0.16 ; 100.00 * 11.99/1200 = 0.99916 -> 0.99 (rounding gives 1.00)
        assertEquals(99, Cbact04c.monthlyInterest(10000, 1199));
    }

    @Test
    void truncatesNegative() {
        assertEquals(-1456, Cbact04c.monthlyInterest(-116487, 1500));
    }

    @Test
    void truncatesNegativeTowardZero() {
        assertEquals(-99, Cbact04c.monthlyInterest(-10000, 1199));
    }

    @Test
    void negativeWrittenWithOverpunch() {
        assertEquals("0000000145O", Zoned.encode(Cbact04c.monthlyInterest(-116487, 1500), 11));
    }
}
