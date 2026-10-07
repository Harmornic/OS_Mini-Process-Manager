package com.example.processmanager.model.system;

/**
 * Read/write throughput summed over all physical disks.
 */
public record DiskIoInfo(
        double readBytesPerSec,
        double writeBytesPerSec) {
}
