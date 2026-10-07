package com.example.processmanager.model.system;

import java.time.Instant;
import java.util.List;

/**
 * Everything sampled in one monitoring tick. Any component that failed to sample is {@code null}
 * (lists are empty instead); the UI should show "N/A" for it.
 *
 * @param runningProcessCount processes whose state is RUNNING; on Windows this excludes only suspended
 *                            processes (e.g. background UWP apps), so it is usually equal to processCount
 */
public record SystemSnapshot(
        Instant timestamp,
        CpuInfo cpu,
        MemoryInfo memory,
        List<DiskInfo> disks,
        DiskIoInfo diskIo,
        List<GpuInfo> gpus,
        NetworkInfo network,
        Long uptimeSeconds,
        Integer processCount,
        Integer runningProcessCount) {

    public SystemSnapshot {
        disks = disks == null ? List.of() : List.copyOf(disks);
        gpus = gpus == null ? List.of() : List.copyOf(gpus);
    }
}
