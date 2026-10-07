package com.example.processmanager.util;

import com.example.processmanager.model.system.CpuInfo;
import com.example.processmanager.model.system.DiskInfo;
import com.example.processmanager.model.system.GpuInfo;
import com.example.processmanager.model.system.MemoryInfo;
import com.example.processmanager.model.system.NetworkInfo;
import com.example.processmanager.model.system.SystemSnapshot;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Display formatting for monitoring values. Units are binary (1 KB = 1024 B) to match Windows Task Manager.
 * Unavailable values (null, negative, NaN) are rendered as {@link #NA}.
 */
public final class FormatUtils {

    public static final String NA = "N/A";

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private FormatUtils() {
    }

    public static String bytes(long bytes) {
        if (bytes < 0) {
            return NA;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }

    public static String bytes(Long bytes) {
        return bytes == null ? NA : bytes((long) bytes);
    }

    public static String rate(double bytesPerSecond) {
        if (Double.isNaN(bytesPerSecond) || bytesPerSecond < 0) {
            return NA;
        }
        return bytes(Math.round(bytesPerSecond)) + "/s";
    }

    public static String percent(double percent) {
        if (Double.isNaN(percent) || percent < 0) {
            return NA;
        }
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    public static String percent(Double percent) {
        return percent == null ? NA : percent((double) percent);
    }

    /**
     * Formats as {@code 2d 03:14:05}.
     */
    public static String uptime(long seconds) {
        if (seconds < 0) {
            return NA;
        }
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long secs = seconds % 60;
        return String.format(Locale.ROOT, "%dd %02d:%02d:%02d", days, hours, minutes, secs);
    }

    public static String uptime(Long seconds) {
        return seconds == null ? NA : uptime((long) seconds);
    }

    public static String frequency(Long hz) {
        if (hz == null || hz <= 0) {
            return NA;
        }
        if (hz >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2f GHz", hz / 1e9);
        }
        return String.format(Locale.ROOT, "%.0f MHz", hz / 1e6);
    }

    /**
     * Plain multi-line text of a snapshot, for the temporary placeholder until the UI cards are built.
     */
    public static String summary(SystemSnapshot s) {
        StringBuilder sb = new StringBuilder();

        CpuInfo cpu = s.cpu();
        sb.append("CPU: ");
        if (cpu == null) {
            sb.append(NA);
        } else {
            sb.append(percent(cpu.usagePercent()))
                    .append(" | ").append(cpu.model())
                    .append(" | ").append(cpu.physicalCores()).append(" cores / ")
                    .append(cpu.logicalCores()).append(" logical")
                    .append(" | Max ").append(frequency(cpu.maxFreqHz()))
                    .append(" | Current ").append(frequency(cpu.currentFreqHz()));
        }

        MemoryInfo mem = s.memory();
        sb.append("\nRAM: ");
        if (mem == null) {
            sb.append(NA);
        } else {
            sb.append("Used ").append(bytes(mem.usedBytes()))
                    .append(" / Total ").append(bytes(mem.totalBytes()))
                    .append(" (").append(percent(mem.usedPercent())).append(")")
                    .append(" | Available ").append(bytes(mem.availableBytes()))
                    .append(" | Committed ").append(bytes(mem.virtualUsedBytes()))
                    .append(" / ").append(bytes(mem.virtualTotalBytes()));
        }

        sb.append("\nDisk: ");
        if (s.disks().isEmpty()) {
            sb.append(NA);
        } else {
            sb.append(s.disks().stream()
                    .map(FormatUtils::disk)
                    .collect(Collectors.joining(" | ")));
        }
        sb.append(" | Read ")
                .append(s.diskIo() == null ? NA : rate(s.diskIo().readBytesPerSec()))
                .append(" / Write ")
                .append(s.diskIo() == null ? NA : rate(s.diskIo().writeBytesPerSec()));

        sb.append("\nGPU: ");
        if (s.gpus().isEmpty()) {
            sb.append(NA);
        } else {
            sb.append(s.gpus().stream()
                    .map(FormatUtils::gpu)
                    .collect(Collectors.joining(" | ")));
        }

        NetworkInfo net = s.network();
        sb.append("\nNetwork: ");
        if (net == null) {
            sb.append(NA);
        } else {
            sb.append("Down ").append(rate(net.downloadBytesPerSec()))
                    .append(" | Up ").append(rate(net.uploadBytesPerSec()))
                    .append(" | Throughput ").append(rate(net.totalThroughputBytesPerSec()))
                    .append(" | Total recv ").append(bytes(net.totalBytesReceived()))
                    .append(" / sent ").append(bytes(net.totalBytesSent()));
        }

        sb.append("\nUptime: ").append(uptime(s.uptimeSeconds()))
                .append(" | Processes: ").append(s.processCount() == null ? NA : s.processCount())
                .append(" | Running: ").append(s.runningProcessCount() == null ? NA : s.runningProcessCount());

        return sb.toString();
    }

    private static String disk(DiskInfo d) {
        return d.mount() + " " + bytes(d.usedBytes()) + " used / " + bytes(d.freeBytes())
                + " free / " + bytes(d.totalBytes()) + " (" + percent(d.usedPercent()) + ")";
    }

    private static String gpu(GpuInfo g) {
        return g.name() + " " + percent(g.usagePercent())
                + " | VRAM " + bytes(g.vramUsedBytes()) + " / " + bytes(g.vramTotalBytes());
    }
}
