package com.example.processmanager.service;

import com.example.processmanager.model.ProcessInfo;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessService {

    private static final System.Logger LOG =
            System.getLogger(ProcessService.class.getName());

    // getAllProcesses() is called every second for CPU usage; thread count and memory only need the
    // 5-second list refresh, so the OSHI process query is reused for this long.
    private static final long OS_PROCESSES_MAX_AGE_NANOS = 4_500_000_000L;

    private final WindowsProcessMetrics windowsProcessMetrics =
            new WindowsProcessMetrics();

    private final OperatingSystem operatingSystem =
            new SystemInfo().getOperatingSystem();

    private Map<Long, OSProcess> osProcesses = Map.of();

    private long osProcessesQueriedAt;

    private boolean osProcessQueryFailed;

    public List<ProcessInfo> getAllProcesses() {

        List<ProcessInfo> processes = new ArrayList<>();

        Map<Long, OSProcess> osProcessesByPid = osProcesses();

        ProcessHandle.allProcesses().forEach(process -> {

            ProcessHandle.Info info = process.info();

            // Process name
            String name = info.command()
                    .map(command -> {
                        int lastSlash = Math.max(
                                command.lastIndexOf('/'),
                                command.lastIndexOf('\\')
                        );

                        return lastSlash >= 0
                                ? command.substring(lastSlash + 1)
                                : command;
                    })
                    .orElse("Unknown");

            // Command / Path
            String command = info.command()
                    .orElse("Unknown");

            // Process status
            String status = process.isAlive()
                    ? "Running"
                    : "Terminated";

            // Parent PID
            long parentPid = process.parent()
                    .map(ProcessHandle::pid)
                    .orElse(-1L);

            // CPU Time - Windows Native API
            Duration cpuTime =
                    windowsProcessMetrics.getCpuTime(process.pid());

            // Thread count / Memory (Working Set) - OSHI
            OSProcess osProcess =
                    osProcessesByPid.get(process.pid());

            int threadCount = ProcessInfo.UNKNOWN;
            long memoryBytes = ProcessInfo.UNKNOWN;

            if (osProcess != null) {
                threadCount = osProcess.getThreadCount();
                memoryBytes = osProcess.getResidentMemory();
            }

            ProcessInfo processInfo = new ProcessInfo(
                    process.pid(),
                    parentPid,
                    name,
                    command,
                    status,
                    info.startInstant().orElse(null),
                    cpuTime,
                    0.0,
                    threadCount,
                    memoryBytes
            );

            processes.add(processInfo);
        });

        return processes;
    }

    private Map<Long, OSProcess> osProcesses() {

        long now = System.nanoTime();

        if (osProcessesQueriedAt != 0
                && now - osProcessesQueriedAt < OS_PROCESSES_MAX_AGE_NANOS) {
            return osProcesses;
        }

        osProcessesQueriedAt = now;

        try {
            Map<Long, OSProcess> byPid = new HashMap<>();

            for (OSProcess osProcess : operatingSystem.getProcesses()) {
                byPid.put((long) osProcess.getProcessID(), osProcess);
            }

            osProcesses = byPid;
            osProcessQueryFailed = false;

        } catch (RuntimeException e) {

            if (!osProcessQueryFailed) {
                LOG.log(
                        System.Logger.Level.WARNING,
                        "Cannot read process thread count / memory",
                        e
                );
            }

            osProcessQueryFailed = true;
            osProcesses = Map.of();
        }

        return osProcesses;
    }
}