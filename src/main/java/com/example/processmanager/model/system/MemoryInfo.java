package com.example.processmanager.model.system;

/**
 * @param virtualTotalBytes commit limit (RAM + page file), Task Manager "Committed" right-hand value
 * @param virtualUsedBytes  committed memory, Task Manager "Committed" left-hand value
 */
public record MemoryInfo(
        long totalBytes,
        long usedBytes,
        long availableBytes,
        double usedPercent,
        long virtualTotalBytes,
        long virtualUsedBytes) {
}
