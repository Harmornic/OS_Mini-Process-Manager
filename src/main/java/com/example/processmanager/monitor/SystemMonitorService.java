package com.example.processmanager.monitor;

import com.example.processmanager.model.system.CpuInfo;
import com.example.processmanager.model.system.DiskInfo;
import com.example.processmanager.model.system.DiskIoInfo;
import com.example.processmanager.model.system.GpuInfo;
import com.example.processmanager.model.system.MemoryInfo;
import com.example.processmanager.model.system.NetworkInfo;
import com.example.processmanager.model.system.SystemSnapshot;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Samples system-wide metrics every second on a background daemon thread and publishes an immutable
 * {@link SystemSnapshot} on the JavaFX Application Thread.
 *
 * <pre>{@code
 * SystemMonitorService monitor = new SystemMonitorService();
 * monitor.addListener(snapshot -> label.setText(...)); // called on the FX thread
 * monitor.start();
 * ...
 * monitor.stop(); // from Application.stop()
 * }</pre>
 */
public class SystemMonitorService {

    public static final long SAMPLE_INTERVAL_MILLIS = 1_000;

    private static final System.Logger LOG = System.getLogger(SystemMonitorService.class.getName());

    private final List<Consumer<SystemSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final ReadOnlyObjectWrapper<SystemSnapshot> snapshot = new ReadOnlyObjectWrapper<>();

    private ScheduledExecutorService executor;

    // Everything below is only touched on the monitor thread.
    private SystemInfo systemInfo;
    private OperatingSystem operatingSystem;
    private CpuMonitor cpuMonitor;
    private MemoryMonitor memoryMonitor;
    private DiskMonitor diskMonitor;
    private NetworkMonitor networkMonitor;
    private GpuMonitor gpuMonitor;
    private final Set<String> failingComponents = new HashSet<>();

    public void addListener(Consumer<SystemSnapshot> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SystemSnapshot> listener) {
        listeners.remove(listener);
    }

    /**
     * Latest snapshot; {@code null} until the first sample (about one second after {@link #start()}).
     */
    public ReadOnlyObjectProperty<SystemSnapshot> snapshotProperty() {
        return snapshot.getReadOnlyProperty();
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "system-monitor");
            thread.setDaemon(true);
            return thread;
        });
        // Rates (CPU %, disk, network, GPU) need a previous sample, so the first snapshot is published one interval
        // after the baseline is taken during initialization.
        executor.execute(this::initialize);
        // Fixed delay (not rate) so a slow sample or a resume from sleep never causes a burst of catch-up samples.
        executor.scheduleWithFixedDelay(
                this::sampleSafely,
                SAMPLE_INTERVAL_MILLIS,
                SAMPLE_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        ExecutorService stopping = executor;
        executor = null;
        stopping.shutdownNow();
        try {
            if (!stopping.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.log(System.Logger.Level.WARNING, "System monitor thread did not stop within 2 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (gpuMonitor != null) {
            gpuMonitor.close();
            gpuMonitor = null;
        }
    }

    private void initialize() {
        if (systemInfo == null) {
            systemInfo = new SystemInfo();
        }
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        operatingSystem = systemInfo.getOperatingSystem();

        cpuMonitor = create("CPU", () -> new CpuMonitor(hardware.getProcessor()));
        memoryMonitor = create("Memory", () -> new MemoryMonitor(hardware.getMemory()));
        diskMonitor = create("Disk", () -> new DiskMonitor(operatingSystem.getFileSystem(), hardware.getDiskStores()));
        networkMonitor = create("Network", () -> new NetworkMonitor(hardware.getNetworkIFs(false)));
        gpuMonitor = create("GPU", () -> new GpuMonitor(hardware));
    }

    private void sampleSafely() {
        try {
            SystemSnapshot next = sample();
            Platform.runLater(() -> publish(next));
        } catch (Throwable t) {
            // An exception escaping a scheduled task would silently cancel all future samples.
            LOG.log(System.Logger.Level.ERROR, "System monitor sample failed", t);
        }
    }

    private SystemSnapshot sample() {
        CpuInfo cpu = sample("CPU", cpuMonitor, CpuMonitor::sample);
        MemoryInfo memory = sample("Memory", memoryMonitor, MemoryMonitor::sample);
        List<DiskInfo> disks = sample("Disk space", diskMonitor, DiskMonitor::sampleSpace);
        DiskIoInfo diskIo = sample("Disk I/O", diskMonitor, DiskMonitor::sampleIo);
        List<GpuInfo> gpus = sample("GPU", gpuMonitor, GpuMonitor::sample);
        NetworkInfo network = sample("Network", networkMonitor, NetworkMonitor::sample);
        Long uptime = sample("Uptime", operatingSystem, OperatingSystem::getSystemUptime);
        int[] processCounts = sample("Process count", operatingSystem, SystemMonitorService::countProcesses);

        return new SystemSnapshot(
                Instant.now(),
                cpu,
                memory,
                disks,
                diskIo,
                gpus,
                network,
                uptime,
                processCounts == null ? null : processCounts[0],
                processCounts == null ? null : processCounts[1]
        );
    }

    private static int[] countProcesses(OperatingSystem os) {
        List<OSProcess> processes = os.getProcesses();
        int running = (int) processes.stream()
                .filter(p -> p.getState() == OSProcess.State.RUNNING)
                .count();
        return new int[]{processes.size(), running};
    }

    private void publish(SystemSnapshot next) {
        snapshot.set(next);
        for (Consumer<SystemSnapshot> listener : listeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "System snapshot listener failed", e);
            }
        }
    }

    private <T> T create(String component, Supplier<T> factory) {
        try {
            return factory.get();
        } catch (RuntimeException | LinkageError e) {
            LOG.log(System.Logger.Level.WARNING, component + " monitoring is unavailable", e);
            return null;
        }
    }

    private <M, R> R sample(String component, M monitor, Function<M, R> sampler) {
        if (monitor == null) {
            return null;
        }
        try {
            R result = sampler.apply(monitor);
            if (failingComponents.remove(component)) {
                LOG.log(System.Logger.Level.INFO, component + " monitoring recovered");
            }
            return result;
        } catch (RuntimeException e) {
            // Log once per failure streak instead of every second.
            if (failingComponents.add(component)) {
                LOG.log(System.Logger.Level.WARNING, component + " sampling failed", e);
            }
            return null;
        }
    }
}
