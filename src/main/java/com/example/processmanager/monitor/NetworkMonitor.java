package com.example.processmanager.monitor;

import com.example.processmanager.model.system.NetworkInfo;
import com.example.processmanager.util.Rates;
import oshi.hardware.NetworkIF;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class NetworkMonitor {

    // Windows exposes each adapter several times (WFP / QoS filter layers named "...-0000") with the same
    // byte counters; summing them all would count the same traffic 3-4 times.
    private static final Pattern FILTER_LAYER = Pattern.compile(".*-\\d{4}$");

    private final List<NetworkIF> interfaces;
    private final Map<String, long[]> previousCounters = new HashMap<>();
    private long previousNanos;

    NetworkMonitor(List<NetworkIF> interfaces) {
        this.interfaces = interfaces;
        for (NetworkIF nif : interfaces) {
            nif.updateAttributes();
        }
        for (NetworkIF nif : countedInterfaces()) {
            previousCounters.put(nif.getName(), new long[]{nif.getBytesRecv(), nif.getBytesSent()});
        }
        this.previousNanos = System.nanoTime();
    }

    NetworkInfo sample() {
        for (NetworkIF nif : interfaces) {
            nif.updateAttributes();
        }
        long now = System.nanoTime();
        long elapsed = now - previousNanos;

        double download = 0;
        double upload = 0;
        long totalReceived = 0;
        long totalSent = 0;
        Map<String, long[]> current = new HashMap<>();

        for (NetworkIF nif : countedInterfaces()) {
            long recv = nif.getBytesRecv();
            long sent = nif.getBytesSent();
            totalReceived += recv;
            totalSent += sent;
            current.put(nif.getName(), new long[]{recv, sent});

            long[] previous = previousCounters.get(nif.getName());
            if (previous != null) {
                download += Rates.perSecond(previous[0], recv, elapsed);
                upload += Rates.perSecond(previous[1], sent, elapsed);
            }
        }

        previousCounters.clear();
        previousCounters.putAll(current);
        previousNanos = now;

        return new NetworkInfo(download, upload, download + upload, totalReceived, totalSent);
    }

    private List<NetworkIF> countedInterfaces() {
        List<NetworkIF> up = interfaces.stream()
                .filter(nif -> nif.getIfOperStatus() == NetworkIF.IfOperStatus.UP)
                .toList();

        List<NetworkIF> physical = up.stream()
                .filter(NetworkIF::isConnectorPresent)
                .toList();
        if (!physical.isEmpty()) {
            return physical;
        }

        return up.stream()
                .filter(nif -> !FILTER_LAYER.matcher(nif.getDisplayName()).matches())
                .toList();
    }
}
