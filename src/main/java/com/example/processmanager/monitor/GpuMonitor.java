package com.example.processmanager.monitor;

import com.example.processmanager.model.system.GpuInfo;
import com.sun.jna.Platform;
import oshi.hardware.GpuStats;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * GPU name and VRAM total come from OSHI on every OS. Usage % and VRAM used come from Windows performance counters
 * on Windows, and from OSHI elsewhere; whatever cannot be read is reported as null.
 */
class GpuMonitor implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(GpuMonitor.class.getName());

    private final List<GraphicsCard> cards;
    private final List<GpuStats> sessions = new ArrayList<>();
    private final WindowsGpuMetrics windowsMetrics;

    private boolean windowsSampleFailed;

    GpuMonitor(HardwareAbstractionLayer hardware) {
        this.cards = hardware.getGraphicsCards();

        for (GraphicsCard card : cards) {
            GpuStats session = null;
            try {
                session = card.createStatsSession();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "GPU stats unavailable for " + card.getName(), e);
            }
            sessions.add(session);
        }

        WindowsGpuMetrics metrics = null;
        if (Platform.isWindows()) {
            try {
                metrics = new WindowsGpuMetrics();
            } catch (RuntimeException | LinkageError e) {
                LOG.log(System.Logger.Level.WARNING, "Windows GPU performance counters unavailable", e);
            }
        }
        this.windowsMetrics = metrics;
    }

    List<GpuInfo> sample() {
        boolean windowsAvailable = windowsMetrics != null && sampleWindowsMetrics();

        List<GpuInfo> gpus = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            GraphicsCard card = cards.get(i);
            GpuStats session = sessions.get(i);

            Double usage;
            Long vramUsed;
            if (windowsAvailable) {
                usage = windowsMetrics.usagePercent(card.getName());
                vramUsed = windowsMetrics.dedicatedUsedBytes(card.getName());
                if (vramUsed == null) {
                    vramUsed = oshiVramUsed(session);
                }
            } else if (windowsMetrics != null) {
                // On Windows OSHI's utilization is known to be wrong, so leave usage unavailable.
                usage = null;
                vramUsed = oshiVramUsed(session);
            } else {
                usage = oshiUsage(session);
                vramUsed = oshiVramUsed(session);
            }

            gpus.add(new GpuInfo(
                    card.getName(),
                    usage,
                    vramUsed,
                    card.getVRam(),
                    usage != null && vramUsed != null
            ));
        }
        return gpus;
    }

    private boolean sampleWindowsMetrics() {
        try {
            windowsMetrics.sample();
            windowsSampleFailed = false;
            return true;
        } catch (RuntimeException e) {
            if (!windowsSampleFailed) {
                LOG.log(System.Logger.Level.WARNING, "Failed to read GPU performance counters", e);
            }
            windowsSampleFailed = true;
            return false;
        }
    }

    private static Double oshiUsage(GpuStats session) {
        if (session == null) {
            return null;
        }
        try {
            double usage = session.getGpuUtilization();
            return usage >= 0 ? usage : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long oshiVramUsed(GpuStats session) {
        if (session == null) {
            return null;
        }
        try {
            long used = session.getVramUsed();
            return used >= 0 ? used : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void close() {
        for (GpuStats session : sessions) {
            if (session != null) {
                session.close();
            }
        }
    }
}
