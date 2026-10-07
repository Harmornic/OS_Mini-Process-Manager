package com.example.processmanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatesTest {

    @Test
    void deltaOverOneSecond() {
        assertEquals(1_000.0, Rates.perSecond(5_000, 6_000, 1_000_000_000L), 1e-9);
    }

    @Test
    void scalesByActualElapsedTime() {
        assertEquals(2_000.0, Rates.perSecond(0, 1_000, 500_000_000L), 1e-9);
        assertEquals(800.0, Rates.perSecond(0, 1_000, 1_250_000_000L), 1e-9);
    }

    @Test
    void counterResetGivesZero() {
        assertEquals(0.0, Rates.perSecond(10_000, 50, 1_000_000_000L));
    }

    @Test
    void nonPositiveIntervalGivesZero() {
        assertEquals(0.0, Rates.perSecond(0, 1_000, 0));
        assertEquals(0.0, Rates.perSecond(0, 1_000, -1));
    }
}
