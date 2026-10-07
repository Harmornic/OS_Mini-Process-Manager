package com.example.processmanager.model;

import java.time.Duration;
import java.time.Instant;

public class ProcessInfo {

    public static final int UNKNOWN = -1;

    private final long pid;
    private final long parentPid;
    private final String name;
    private final String command;
    private final String status;
    private final Instant startTime;
    private final Duration cpuTime;
    private final double cpuUsage;
    private final int threadCount;
    private final long memoryBytes;

    public ProcessInfo(
            long pid,
            long parentPid,
            String name,
            String command,
            String status,
            Instant startTime,
            Duration cpuTime) {

        this(
                pid,
                parentPid,
                name,
                command,
                status,
                startTime,
                cpuTime,
                0.0
        );
    }

    public ProcessInfo(
            long pid,
            long parentPid,
            String name,
            String command,
            String status,
            Instant startTime,
            Duration cpuTime,
            double cpuUsage) {

        this(
                pid,
                parentPid,
                name,
                command,
                status,
                startTime,
                cpuTime,
                cpuUsage,
                UNKNOWN,
                UNKNOWN
        );
    }

    /**
     * @param threadCount {@link #UNKNOWN} when it cannot be read
     * @param memoryBytes working set in bytes, {@link #UNKNOWN} when it cannot be read
     */
    public ProcessInfo(
            long pid,
            long parentPid,
            String name,
            String command,
            String status,
            Instant startTime,
            Duration cpuTime,
            double cpuUsage,
            int threadCount,
            long memoryBytes) {

        this.pid = pid;
        this.parentPid = parentPid;
        this.name = name;
        this.command = command;
        this.status = status;
        this.startTime = startTime;
        this.cpuTime = cpuTime;
        this.cpuUsage = cpuUsage;
        this.threadCount = threadCount;
        this.memoryBytes = memoryBytes;
    }

    public ProcessInfo withCpuUsage(double newCpuUsage) {
        return new ProcessInfo(
                pid,
                parentPid,
                name,
                command,
                status,
                startTime,
                cpuTime,
                newCpuUsage,
                threadCount,
                memoryBytes
        );
    }

    public long getPid() {
        return pid;
    }

    public long getParentPid() {
        return parentPid;
    }

    public String getName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Duration getCpuTime() {
        return cpuTime;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public long getMemoryBytes() {
        return memoryBytes;
    }
}

