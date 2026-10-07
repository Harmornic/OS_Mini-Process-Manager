package com.example.processmanager.model.system;

/**
 * @param maxFreqHz     null when the OS does not report it
 * @param currentFreqHz null when the OS does not report it; on Windows this is usually the base clock, not the live boost clock
 */
public record CpuInfo(
        double usagePercent,
        String model,
        int physicalCores,
        int logicalCores,
        Long maxFreqHz,
        Long currentFreqHz) {
}
