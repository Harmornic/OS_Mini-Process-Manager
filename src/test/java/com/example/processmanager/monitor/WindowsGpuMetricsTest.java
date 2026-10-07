package com.example.processmanager.monitor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsGpuMetricsTest {

    private static final long ONE_SECOND_NANOS = 1_000_000_000L;
    // Running time is in 100 ns units: one second = 10,000,000.
    private static final long ONE_SECOND_100NS = 10_000_000L;

    private static final String NVIDIA_3D_PID1 = "pid_100_luid_0x00000000_0x000251B1_phys_0_eng_0_engtype_3D";
    private static final String NVIDIA_3D_PID2 = "pid_200_luid_0x00000000_0x000251B1_phys_0_eng_0_engtype_3D";
    private static final String NVIDIA_VIDEO = "pid_100_luid_0x00000000_0x000251B1_phys_0_eng_5_engtype_VideoDecode";
    private static final String BASIC_RENDER = "pid_100_luid_0x00000000_0x00026042_phys_0_eng_0_engtype_3D";

    @Test
    void sumsProcessesOnTheSameEngine() {
        Map<String, Double> usage = WindowsGpuMetrics.computeUsageByLuid(
                Map.of(NVIDIA_3D_PID1, 0L, NVIDIA_3D_PID2, 0L),
                Map.of(NVIDIA_3D_PID1, ONE_SECOND_100NS / 10, NVIDIA_3D_PID2, ONE_SECOND_100NS / 5),
                ONE_SECOND_NANOS);

        assertEquals(30.0, usage.get("luid_0x00000000_0x000251b1"), 1e-9);
    }

    @Test
    void adapterUsageIsBusiestEngine() {
        Map<String, Double> usage = WindowsGpuMetrics.computeUsageByLuid(
                Map.of(NVIDIA_3D_PID1, 0L, NVIDIA_VIDEO, 0L),
                Map.of(NVIDIA_3D_PID1, ONE_SECOND_100NS / 4, NVIDIA_VIDEO, ONE_SECOND_100NS * 3 / 4),
                ONE_SECOND_NANOS);

        assertEquals(75.0, usage.get("luid_0x00000000_0x000251b1"), 1e-9);
    }

    @Test
    void adaptersAreSeparatedByLuid() {
        Map<String, Double> usage = WindowsGpuMetrics.computeUsageByLuid(
                Map.of(NVIDIA_3D_PID1, 0L, BASIC_RENDER, 0L),
                Map.of(NVIDIA_3D_PID1, ONE_SECOND_100NS / 2, BASIC_RENDER, 0L),
                ONE_SECOND_NANOS);

        assertEquals(50.0, usage.get("luid_0x00000000_0x000251b1"), 1e-9);
        assertEquals(0.0, usage.get("luid_0x00000000_0x00026042"), 1e-9);
    }

    @Test
    void usesActualElapsedTimeAndClampsTo100() {
        Map<String, Double> halfSecond = WindowsGpuMetrics.computeUsageByLuid(
                Map.of(NVIDIA_3D_PID1, 0L),
                Map.of(NVIDIA_3D_PID1, ONE_SECOND_100NS / 4),
                ONE_SECOND_NANOS / 2);
        assertEquals(50.0, halfSecond.get("luid_0x00000000_0x000251b1"), 1e-9);

        Map<String, Double> overflow = WindowsGpuMetrics.computeUsageByLuid(
                Map.of(NVIDIA_3D_PID1, 0L, NVIDIA_3D_PID2, 0L),
                Map.of(NVIDIA_3D_PID1, ONE_SECOND_100NS, NVIDIA_3D_PID2, ONE_SECOND_100NS),
                ONE_SECOND_NANOS);
        assertEquals(100.0, overflow.get("luid_0x00000000_0x000251b1"), 1e-9);
    }

    @Test
    void newOrResetInstancesContributeNothing() {
        Map<String, Double> usage = WindowsGpuMetrics.computeUsageByLuid(
                Map.of(NVIDIA_3D_PID1, 5_000_000L),
                Map.of(NVIDIA_3D_PID1, 1_000L, NVIDIA_3D_PID2, 9_999_999L),
                ONE_SECOND_NANOS);

        assertEquals(0.0, usage.get("luid_0x00000000_0x000251b1"), 1e-9);
    }

    @Test
    void noElapsedTimeGivesNoData() {
        assertTrue(WindowsGpuMetrics.computeUsageByLuid(Map.of(), Map.of(NVIDIA_3D_PID1, 1L), 0).isEmpty());
    }
}
