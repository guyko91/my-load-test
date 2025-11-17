package com.dw.idstrust.loadtesttoy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class K6ControlService {
    private static final Logger log = LoggerFactory.getLogger(K6ControlService.class);

    private Process currentK6Process;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Map<String, String> lastTestResult = new ConcurrentHashMap<>();

    public void startK6Test(String scenario, int rps, int durationMinutes, int vus) {
        if (isRunning.get()) {
            log.warn("K6 test already running, skipping new request");
            return;
        }

        try {
            isRunning.set(true);
            lastTestResult.clear();
            lastTestResult.put("status", "running");
            lastTestResult.put("scenario", scenario);
            lastTestResult.put("startTime", String.valueOf(System.currentTimeMillis()));

            List<String> command = new ArrayList<>();
            command.add("k6");
            command.add("run");
            command.add("--out");
            command.add("json=/tmp/k6-results.json");
            command.add("-e");
            command.add("SCENARIO=" + scenario);
            command.add("-e");
            command.add("RPS=" + rps);
            command.add("-e");
            command.add("DURATION=" + durationMinutes + "m");
            command.add("-e");
            command.add("VUS=" + vus);
            command.add("-e");
            command.add("BASE_URL=http://localhost:28080");
            command.add("/scripts/dynamic.js");

            log.info("Starting K6 test: scenario={}, rps={}, duration={}m, vus={}",
                    scenario, rps, durationMinutes, vus);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            currentK6Process = pb.start();

            // Read output in background thread
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentK6Process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("K6: {}", line);
                    }
                } catch (Exception e) {
                    log.error("Error reading K6 output", e);
                }
            }).start();

            // Wait for process completion in background
            new Thread(() -> {
                try {
                    int exitCode = currentK6Process.waitFor();
                    lastTestResult.put("status", exitCode == 0 ? "completed" : "failed");
                    lastTestResult.put("exitCode", String.valueOf(exitCode));
                    lastTestResult.put("endTime", String.valueOf(System.currentTimeMillis()));
                    log.info("K6 test finished with exit code: {}", exitCode);
                } catch (InterruptedException e) {
                    lastTestResult.put("status", "interrupted");
                    Thread.currentThread().interrupt();
                } finally {
                    isRunning.set(false);
                }
            }).start();

        } catch (Exception e) {
            log.error("Failed to start K6 test", e);
            isRunning.set(false);
            lastTestResult.put("status", "error");
            lastTestResult.put("error", e.getMessage());
        }
    }

    public void stopK6Test() {
        if (currentK6Process != null && currentK6Process.isAlive()) {
            log.info("Stopping K6 test");
            currentK6Process.destroy();
            try {
                currentK6Process.waitFor();
            } catch (InterruptedException e) {
                currentK6Process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            lastTestResult.put("status", "stopped");
            isRunning.set(false);
        }
    }

    public boolean isK6Running() {
        return isRunning.get();
    }

    public Map<String, String> getLastTestResult() {
        return Map.copyOf(lastTestResult);
    }

    public Map<String, Object> getK6Status() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("running", isRunning.get());
        status.put("lastTest", lastTestResult.isEmpty() ? null : Map.copyOf(lastTestResult));
        return status;
    }
}
