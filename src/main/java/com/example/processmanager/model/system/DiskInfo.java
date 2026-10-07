package com.example.processmanager.model.system;

/**
 * Space usage of one mounted volume (e.g. {@code C:\}).
 */
public record DiskInfo(
        String mount,
        String name,
        long totalBytes,
        long usedBytes,
        long freeBytes,
        double usedPercent) {
}
