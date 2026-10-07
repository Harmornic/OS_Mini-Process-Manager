package com.example.processmanager.util;

import com.example.processmanager.model.system.CpuInfo;
import com.example.processmanager.model.system.GpuInfo;
import com.example.processmanager.model.system.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatUtilsTest {

    @Test
    void bytesUsesBinaryUnits() {
        assertEquals("0 B", FormatUtils.bytes(0L));
        assertEquals("1023 B", FormatUtils.bytes(1023L));
        assertEquals("1.0 KB", FormatUtils.bytes(1024L));
        assertEquals("1.5 MB", FormatUtils.bytes(1_572_864L));
        assertEquals("47.9 GB", FormatUtils.bytes(51_439_435_776L));
        assertEquals("1.0 TB", FormatUtils.bytes(1L << 40));
    }

    @Test
    void unavailableValuesAreNA() {
        assertEquals("N/A", FormatUtils.bytes(-1L));
        assertEquals("N/A", FormatUtils.bytes((Long) null));
        assertEquals("N/A", FormatUtils.percent((Double) null));
        assertEquals("N/A", FormatUtils.percent(Double.NaN));
        assertEquals("N/A", FormatUtils.rate(-5));
        assertEquals("N/A", FormatUtils.uptime((Long) null));
        assertEquals("N/A", FormatUtils.frequency(null));
        assertEquals("N/A", FormatUtils.frequency(0L));
    }

    @Test
    void rateAppendsPerSecond() {
        assertEquals("350.0 KB/s", FormatUtils.rate(358_400));
        assertEquals("12 B/s", FormatUtils.rate(12.4));
    }

    @Test
    void percentHasOneDecimal() {
        assertEquals("12.8%", FormatUtils.percent(12.82));
        assertEquals("100.0%", FormatUtils.percent(100.0));
    }

    @Test
    void uptimeFormat() {
        assertEquals("0d 00:00:00", FormatUtils.uptime(0L));
        assertEquals("0d 00:59:40", FormatUtils.uptime(3_580L));
        assertEquals("2d 03:14:05", FormatUtils.uptime(2 * 86_400L + 3 * 3_600 + 14 * 60 + 5));
    }

    @Test
    void frequency() {
        assertEquals("3.70 GHz", FormatUtils.frequency(3_696_000_000L));
        assertEquals("800 MHz", FormatUtils.frequency(800_000_000L));
    }

    @Test
    void summaryShowsNAForMissingComponents() {
        SystemSnapshot snapshot = new SystemSnapshot(
                Instant.EPOCH,
                new CpuInfo(12.5, "Test CPU", 4, 8, null, null),
                null,
                null,
                null,
                List.of(new GpuInfo("Test GPU", null, null, 8L << 30, false)),
                null,
                3_600L,
                100,
                99);

        String text = FormatUtils.summary(snapshot);

        assertTrue(text.contains("CPU: 12.5% | Test CPU | 4 cores / 8 logical | Max N/A"), text);
        assertTrue(text.contains("RAM: N/A"), text);
        assertTrue(text.contains("Disk: N/A | Read N/A / Write N/A"), text);
        assertTrue(text.contains("Test GPU N/A | VRAM N/A / 8.0 GB"), text);
        assertTrue(text.contains("Network: N/A"), text);
        assertTrue(text.contains("Uptime: 0d 01:00:00 | Processes: 100 | Running: 99"), text);
    }
}
