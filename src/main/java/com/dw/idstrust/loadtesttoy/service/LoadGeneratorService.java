package com.dw.idstrust.loadtesttoy.service;

import com.dw.idstrust.loadtesttoy.config.LoadScenarioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LoadGeneratorService {
    private static final Logger log = LoggerFactory.getLogger(LoadGeneratorService.class);

    private final LoadScenarioProperties props;

    private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "load-worker");
        t.setDaemon(true);
        return t;
    });

    private final List<Future<?>> activeTasks = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, AtomicBoolean> runningMap = new ConcurrentHashMap<>();

    public LoadGeneratorService(LoadScenarioProperties props) {
        this.props = props;
        log.info("LoadGeneratorService initialized (scheduler disabled, HTTP request mode)");
    }

    // Execute single workload synchronously (for HTTP request)
    public void executeWorkload(int durationMs, int cpuPercent, int ioOps) {
        log.info("Executing workload: duration={}ms, cpu={}%, io={} ops/s", durationMs, cpuPercent, ioOps);
        Instant end = Instant.now().plusMillis(durationMs);

        while (Instant.now().isBefore(end)) {
            int cpu = Math.max(0, Math.min(100, cpuPercent));
            if (cpu > 0) {
                busyWork(cpu);
            }
            if (ioOps > 0) {
                try {
                    Thread.sleep(1000L / Math.max(1, ioOps));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void triggerScenario(LoadScenarioProperties.Scenario scenario) {
        String name = scenario.getName() == null ? "unnamed" : scenario.getName();
        AtomicBoolean running = runningMap.computeIfAbsent(name, k -> new AtomicBoolean(false));
        if (running.get()) {
            log.info("Scenario {} already running, skipping", name);
            return;
        }
        running.set(true);
        log.info("Triggering scenario: {} for {}s with concurrency={} cpu%={} ioOps/s={}", name, scenario.getDuration().getSeconds(), scenario.getConcurrency(), scenario.getCpuLoadPercent(), scenario.getIoOpsPerSecond());

        CountDownLatch latch = new CountDownLatch(scenario.getConcurrency());
        Instant end = Instant.now().plus(scenario.getDuration());

        for (int i = 0; i < scenario.getConcurrency(); i++) {
            Future<?> f = workerPool.submit(() -> {
                try {
                    executeWorkload((int) scenario.getDuration().toMillis(),
                                  scenario.getCpuLoadPercent(),
                                  scenario.getIoOpsPerSecond());
                } finally {
                    latch.countDown();
                }
            });
            activeTasks.add(f);
        }

        // Clear running flag when done
        workerPool.submit(() -> {
            try {
                latch.await();
                log.info("Scenario {} completed", name);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.set(false);
                runningMap.remove(name, running);
                activeTasks.removeIf(Future::isDone);
            }
        });
    }

    private void busyWork(int percent) {
        long sliceMs = 100L;
        long busyMs = sliceMs * percent / 100L;
        long idleMs = sliceMs - busyMs;
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(busyMs);
        while (System.nanoTime() < end) {
            Math.sqrt(Math.random());
        }
        if (idleMs > 0) {
            try {
                Thread.sleep(idleMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return runningMap.values().stream().anyMatch(AtomicBoolean::get) || activeTasks.stream().anyMatch(t -> !t.isDone());
    }

    public void stopAll() {
        for (Future<?> f : activeTasks) {
            f.cancel(true);
        }
        activeTasks.clear();
        runningMap.clear();
    }

    public void executeMemoryWorkload(int sizeInMb, int durationInMs) {
        log.info("Triggering memory workload: {} MB for {} ms", sizeInMb, durationInMs);
        AtomicBoolean running = new AtomicBoolean(true);
        runningMap.put("memory-workload", running);

        Future<?> future = workerPool.submit(() -> {
            try {
                List<byte[]> memoryHog = new CopyOnWriteArrayList<>();
                long sizeInBytes = (long) sizeInMb * 1024 * 1024;
                int chunkSize = 1 * 1024 * 1024; // 1MB chunks
                long allocatedBytes = 0;

                log.info("Allocating ~{} MB of memory...", sizeInMb);
                while (allocatedBytes < sizeInBytes) {
                    memoryHog.add(new byte[chunkSize]);
                    allocatedBytes += chunkSize;
                }
                log.info("Successfully allocated ~{} MB of memory.", sizeInMb);

                // Hold the memory for the specified duration
                Thread.sleep(durationInMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Memory workload thread interrupted.");
            } catch (OutOfMemoryError e) {
                log.error("OutOfMemoryError during memory workload! Releasing allocated memory.", e);
            } finally {
                // The memory will be released automatically when the thread finishes
                // and 'memoryHog' goes out of scope, allowing the GC to reclaim it.
                log.info("Releasing ~{} MB of memory.", sizeInMb);
                running.set(false);
                runningMap.remove("memory-workload");
            }
        });
        activeTasks.add(future);
    }

    public void executeCpuAndMemoryWorkload(int cpuPercent, int sizeInMb, int durationInMs) {
        log.info("Triggering CPU+Memory workload: {}% CPU, {} MB for {} ms", cpuPercent, sizeInMb, durationInMs);
        AtomicBoolean running = new AtomicBoolean(true);
        runningMap.put("cpu-memory-workload", running);

        Future<?> future = workerPool.submit(() -> {
            List<byte[]> memoryHog = new CopyOnWriteArrayList<>();
            try {
                // 1. Allocate Memory
                long sizeInBytes = (long) sizeInMb * 1024 * 1024;
                int chunkSize = 1 * 1024 * 1024; // 1MB chunks
                long allocatedBytes = 0;
                log.info("Allocating ~{} MB of memory for combined workload...", sizeInMb);
                while (allocatedBytes < sizeInBytes) {
                    memoryHog.add(new byte[chunkSize]);
                    allocatedBytes += chunkSize;
                }
                log.info("Successfully allocated ~{} MB of memory.", sizeInMb);

                // 2. Generate CPU load while holding memory
                Instant end = Instant.now().plusMillis(durationInMs);
                while (Instant.now().isBefore(end)) {
                    busyWork(cpuPercent);
                }

            } catch (OutOfMemoryError e) {
                log.error("OutOfMemoryError during combined workload!", e);
            } finally {
                // 3. Release memory
                log.info("Releasing ~{} MB of memory from combined workload.", sizeInMb);
                memoryHog.clear();
                running.set(false);
                runningMap.remove("cpu-memory-workload");
            }
        });
        activeTasks.add(future);
    }
}
