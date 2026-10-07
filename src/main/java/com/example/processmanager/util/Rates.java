package com.example.processmanager.util;

public final class Rates {

    private Rates() {
    }

    /**
     * Converts the change of a cumulative counter into a per-second rate.
     * Returns 0 for a non-positive interval or when the counter went backwards (reset / adapter re-plugged).
     */
    public static double perSecond(long previous, long current, long elapsedNanos) {
        if (elapsedNanos <= 0 || current < previous) {
            return 0.0;
        }
        return (current - previous) / (elapsedNanos / 1_000_000_000.0);
    }
}
