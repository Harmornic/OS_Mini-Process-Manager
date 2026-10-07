package com.example.processmanager.monitor;

import com.example.processmanager.model.system.CpuInfo;
import oshi.hardware.CentralProcessor;

import java.util.Arrays;

class CpuMonitor {

    private final CentralProcessor processor;
    private final String model;
    private final int physicalCores;
    private final int logicalCores;
    private final Long maxFreqHz;

    private long[] previousTicks;

    CpuMonitor(CentralProcessor processor) {
        this.processor = processor;
        this.model = processor.getProcessorIdentifier().getName().trim();
        this.physicalCores = processor.getPhysicalProcessorCount();
        this.logicalCores = processor.getLogicalProcessorCount();

        long max = processor.getMaxFreq();
        if (max <= 0) {
            max = processor.getProcessorIdentifier().getVendorFreq();
        }
        this.maxFreqHz = max > 0 ? max : null;

        this.previousTicks = processor.getSystemCpuLoadTicks();
    }

    CpuInfo sample() {
        double load = processor.getSystemCpuLoadBetweenTicks(previousTicks);
        previousTicks = processor.getSystemCpuLoadTicks();

        return new CpuInfo(
                Math.max(0, load) * 100.0,
                model,
                physicalCores,
                logicalCores,
                maxFreqHz,
                currentFreqHz()
        );
    }

    private Long currentFreqHz() {
        long[] perCore = processor.getCurrentFreq();
        double average = Arrays.stream(perCore)
                .filter(hz -> hz > 0)
                .average()
                .orElse(0);
        return average > 0 ? Math.round(average) : null;
    }
}
