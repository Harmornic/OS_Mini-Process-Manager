package com.example.processmanager.monitor;

import com.example.processmanager.model.system.MemoryInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.VirtualMemory;

class MemoryMonitor {

    private final GlobalMemory memory;

    MemoryMonitor(GlobalMemory memory) {
        this.memory = memory;
    }

    MemoryInfo sample() {
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;
        VirtualMemory virtual = memory.getVirtualMemory();

        return new MemoryInfo(
                total,
                used,
                available,
                total > 0 ? used * 100.0 / total : 0.0,
                virtual.getVirtualMax(),
                virtual.getVirtualInUse()
        );
    }
}
