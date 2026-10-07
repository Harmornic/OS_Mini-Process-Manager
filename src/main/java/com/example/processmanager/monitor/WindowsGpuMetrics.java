package com.example.processmanager.monitor;

import oshi.driver.common.windows.gpu.DxgiAdapterInfo;
import oshi.driver.common.windows.perfmon.GpuInformation.GpuAdapterMemoryProperty;
import oshi.driver.common.windows.perfmon.GpuInformation.GpuEngineProperty;
import oshi.driver.windows.perfmon.GpuInformationJNA;
import oshi.util.gpu.DxgiUtilJNA;
import oshi.util.tuples.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GPU usage % and dedicated VRAM used from Windows performance counters ({@code \GPU Engine(*)\Running Time} and
 * {@code \GPU Adapter Memory(*)\Dedicated Usage}). OSHI queries them with English counter names, so this also works
 * on localized Windows.
 * <p>
 * OSHI's own {@code GpuStats.getGpuUtilization()} is not used: in 6.12.0 it matches adapters with a lower-case LUID
 * while PDH instance names are upper-case, so it always reports 100%.
 */
class WindowsGpuMetrics {

    private static final Pattern ENGINE_INSTANCE =
            Pattern.compile("(luid_0x[0-9a-f]+_0x[0-9a-f]+)_phys_(\\d+)_eng_(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ADAPTER_INSTANCE =
            Pattern.compile("(luid_0x[0-9a-f]+_0x[0-9a-f]+)_phys_\\d+", Pattern.CASE_INSENSITIVE);

    private static final int MICROSOFT_VENDOR_ID = 0x1414;

    private final Map<String, String> luidByCardName = new HashMap<>();
    private final String onlyHardwareLuid;

    private Map<String, Long> previousRunningTimes;
    private long previousNanos;

    private Map<String, Double> usageByLuid = Map.of();
    private Map<String, Long> dedicatedByLuid = Map.of();

    WindowsGpuMetrics() {
        String hardwareLuid = null;
        int hardwareAdapters = 0;
        for (DxgiAdapterInfo adapter : DxgiUtilJNA.queryAdapters()) {
            String luid = String.format(
                    Locale.ROOT,
                    "luid_0x%08x_0x%08x",
                    adapter.getLuidHighPart(),
                    adapter.getLuidLowPart()
            );
            luidByCardName.put(DxgiUtilJNA.normalizeName(adapter.getDescription()), luid);
            if (adapter.getVendorId() != MICROSOFT_VENDOR_ID) {
                hardwareLuid = luid;
                hardwareAdapters++;
            }
        }
        this.onlyHardwareLuid = hardwareAdapters == 1 ? hardwareLuid : null;

        this.previousRunningTimes = queryRunningTimes();
        this.previousNanos = System.nanoTime();
    }

    void sample() {
        Map<String, Long> current = queryRunningTimes();
        long now = System.nanoTime();
        usageByLuid = computeUsageByLuid(previousRunningTimes, current, now - previousNanos);
        previousRunningTimes = current;
        previousNanos = now;

        dedicatedByLuid = queryDedicatedUsage();
    }

    Double usagePercent(String cardName) {
        String luid = luidFor(cardName);
        return luid == null ? null : usageByLuid.get(luid);
    }

    Long dedicatedUsedBytes(String cardName) {
        String luid = luidFor(cardName);
        return luid == null ? null : dedicatedByLuid.get(luid);
    }

    private String luidFor(String cardName) {
        String luid = luidByCardName.get(DxgiUtilJNA.normalizeName(cardName));
        return luid != null ? luid : onlyHardwareLuid;
    }

    /**
     * Utilization of each adapter = utilization of its busiest engine, the same way Task Manager reports "GPU".
     * An engine's utilization is its running time (100 ns units, summed over all processes) divided by elapsed time.
     *
     * @return lower-case LUID ({@code luid_0x00000000_0x000251b1}) to percent in [0, 100]
     */
    static Map<String, Double> computeUsageByLuid(
            Map<String, Long> previousRunningTimes,
            Map<String, Long> currentRunningTimes,
            long elapsedNanos) {

        if (elapsedNanos <= 0) {
            return Map.of();
        }
        double elapsed100ns = elapsedNanos / 100.0;

        Map<String, Long> busyPerEngine = new HashMap<>();
        Map<String, String> luidOfEngine = new HashMap<>();

        for (Map.Entry<String, Long> entry : currentRunningTimes.entrySet()) {
            Matcher m = ENGINE_INSTANCE.matcher(entry.getKey());
            if (!m.find()) {
                continue;
            }
            String luid = m.group(1).toLowerCase(Locale.ROOT);
            String engine = luid + "_phys_" + m.group(2) + "_eng_" + m.group(3);
            luidOfEngine.put(engine, luid);

            Long previous = previousRunningTimes.get(entry.getKey());
            // Processes that started or exited between the two samples have no delta.
            if (previous == null || entry.getValue() < previous) {
                busyPerEngine.putIfAbsent(engine, 0L);
                continue;
            }
            busyPerEngine.merge(engine, entry.getValue() - previous, Long::sum);
        }

        Map<String, Double> usage = new HashMap<>();
        for (Map.Entry<String, Long> entry : busyPerEngine.entrySet()) {
            double percent = Math.min(100.0, entry.getValue() / elapsed100ns * 100.0);
            usage.merge(luidOfEngine.get(entry.getKey()), percent, Math::max);
        }
        return usage;
    }

    private static Map<String, Long> queryRunningTimes() {
        Pair<List<String>, Map<GpuEngineProperty, List<Long>>> counters =
                GpuInformationJNA.queryGpuEngineCounters();
        List<String> instances = counters.getA();
        List<Long> runningTimes = counters.getB().get(GpuEngineProperty.RUNNING_TIME);

        Map<String, Long> result = new HashMap<>();
        if (runningTimes == null) {
            return result;
        }
        for (int i = 0; i < instances.size() && i < runningTimes.size(); i++) {
            result.put(instances.get(i), runningTimes.get(i));
        }
        return result;
    }

    private static Map<String, Long> queryDedicatedUsage() {
        Pair<List<String>, Map<GpuAdapterMemoryProperty, List<Long>>> counters =
                GpuInformationJNA.queryGpuAdapterMemoryCounters();
        List<String> instances = counters.getA();
        List<Long> dedicated = counters.getB().get(GpuAdapterMemoryProperty.DEDICATED_USAGE);

        Map<String, Long> result = new HashMap<>();
        if (dedicated == null) {
            return result;
        }
        for (int i = 0; i < instances.size() && i < dedicated.size(); i++) {
            Matcher m = ADAPTER_INSTANCE.matcher(instances.get(i));
            if (m.find()) {
                result.merge(m.group(1).toLowerCase(Locale.ROOT), dedicated.get(i), Long::sum);
            }
        }
        return result;
    }
}
