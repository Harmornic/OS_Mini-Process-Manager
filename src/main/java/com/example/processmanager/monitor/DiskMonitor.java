package com.example.processmanager.monitor;

import com.example.processmanager.model.system.DiskInfo;
import com.example.processmanager.model.system.DiskIoInfo;
import com.example.processmanager.util.Rates;
import oshi.hardware.HWDiskStore;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.util.ArrayList;
import java.util.List;

class DiskMonitor {

    private final FileSystem fileSystem;
    // Physical disks are enumerated once; drives plugged in after start-up are not included in read/write rates.
    private final List<HWDiskStore> diskStores;

    private long previousReadBytes;
    private long previousWriteBytes;
    private long previousNanos;

    DiskMonitor(FileSystem fileSystem, List<HWDiskStore> diskStores) {
        this.fileSystem = fileSystem;
        this.diskStores = diskStores;
        updateCounters();
        this.previousReadBytes = totalReadBytes();
        this.previousWriteBytes = totalWriteBytes();
        this.previousNanos = System.nanoTime();
    }

    List<DiskInfo> sampleSpace() {
        List<DiskInfo> disks = new ArrayList<>();
        for (OSFileStore store : fileSystem.getFileStores(true)) {
            long total = store.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long free = store.getFreeSpace();
            long used = total - free;
            disks.add(new DiskInfo(
                    store.getMount(),
                    store.getName(),
                    total,
                    used,
                    free,
                    used * 100.0 / total
            ));
        }
        return disks;
    }

    DiskIoInfo sampleIo() {
        updateCounters();
        long now = System.nanoTime();
        long read = totalReadBytes();
        long write = totalWriteBytes();

        DiskIoInfo io = new DiskIoInfo(
                Rates.perSecond(previousReadBytes, read, now - previousNanos),
                Rates.perSecond(previousWriteBytes, write, now - previousNanos)
        );

        previousReadBytes = read;
        previousWriteBytes = write;
        previousNanos = now;
        return io;
    }

    private void updateCounters() {
        diskStores.forEach(HWDiskStore::updateAttributes);
    }

    private long totalReadBytes() {
        return diskStores.stream().mapToLong(HWDiskStore::getReadBytes).sum();
    }

    private long totalWriteBytes() {
        return diskStores.stream().mapToLong(HWDiskStore::getWriteBytes).sum();
    }
}
