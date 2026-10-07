package com.example.processmanager.model.system;

/**
 * Traffic summed over physical network adapters that are up.
 *
 * @param totalBytesReceived cumulative since the adapters came up (usually since boot)
 * @param totalBytesSent     cumulative since the adapters came up (usually since boot)
 */
public record NetworkInfo(
        double downloadBytesPerSec,
        double uploadBytesPerSec,
        double totalThroughputBytesPerSec,
        long totalBytesReceived,
        long totalBytesSent) {
}
