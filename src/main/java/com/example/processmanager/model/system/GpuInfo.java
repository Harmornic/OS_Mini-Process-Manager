package com.example.processmanager.model.system;

/**
 * @param usagePercent  null when not available on this OS/driver
 * @param vramUsedBytes null when not available on this OS/driver
 * @param supported     true when both usage and VRAM used could be read
 */
public record GpuInfo(
        String name,
        Double usagePercent,
        Long vramUsedBytes,
        long vramTotalBytes,
        boolean supported) {
}
