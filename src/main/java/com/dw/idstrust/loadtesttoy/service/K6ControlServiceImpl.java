package com.dw.idstrust.loadtesttoy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * K6 Control Service 구현체
 * 'docker run'을 사용하여 K6 컨테이너를 직접 실행
 */
@Service
public class K6ControlServiceImpl implements K6ControlService {
    private static final Logger log = LoggerFactory.getLogger(K6ControlServiceImpl.class);

    private Process currentK6Process;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Map<String, String> lastTestResult = new ConcurrentHashMap<>();
    private final String dockerCommand;
    private final String baseUrl;
    private final String hostProjectPath;
    private final String networkName;

    public K6ControlServiceImpl(@Value("${k6.base-url:http://app:28080}") String baseUrl,
                                @Value("${k6.docker.network:load-test-net}") String networkName) {
        this.baseUrl = baseUrl;
        this.networkName = networkName;
        this.dockerCommand = findDockerCommand();
        
        // HOST_PROJECT_PATH는 docker-compose.yml에서 주입됩니다.
        String projectPath = System.getenv("HOST_PROJECT_PATH");
        if (projectPath == null || projectPath.isBlank()) {
            log.warn("HOST_PROJECT_PATH environment variable is not set. Using '.' as default. This may cause issues with volume mounts.");
            this.hostProjectPath = ".";
        } else {
            this.hostProjectPath = projectPath;
        }
        
        log.info("K6ControlService initialized. Docker: {}, Base URL: {}, Host Project Path: {}, Network: {}", 
                 dockerCommand, baseUrl, hostProjectPath, networkName);
    }

    private String findDockerCommand() {
        String[] possiblePaths = {"/usr/local/bin/docker", "/usr/bin/docker", "/opt/homebrew/bin/docker", "docker"};
        for (String path : possiblePaths) {
            try {
                Process p = new ProcessBuilder(path, "--version").start();
                if (p.waitFor() == 0) return path;
            } catch (Exception e) {
                // Ignore and try next path
            }
        }
        return "docker"; // Default fallback
    }

    @Override
    public void startK6Test(String scenario, int rps, int durationMinutes, int vus) {
        if (isRunning.getAndSet(true)) {
            log.warn("K6 test already running, skipping new request");
            return;
        }

        lastTestResult.clear();
        lastTestResult.put("status", "running");
        lastTestResult.put("scenario", scenario);
        lastTestResult.put("startTime", String.valueOf(System.currentTimeMillis()));

        try {
            List<String> command = new ArrayList<>();
            command.add(dockerCommand);
            command.add("run");
            command.add("--rm");
            command.add("--network");
            command.add(networkName);
            command.add("-v");
            command.add(new File(hostProjectPath, "k6").getAbsolutePath() + ":/scripts");
            command.add("-e");
            command.add("SCENARIO=" + scenario);
            command.add("-e");
            command.add("RPS=" + rps);
            command.add("-e");
            command.add("DURATION=" + durationMinutes + "m");
            command.add("-e");
            command.add("VUS=" + vus);
            command.add("-e");
            command.add("BASE_URL=" + baseUrl);
            command.add("grafana/k6:latest");
            command.add("run");
            command.add("/scripts/dynamic.js");

            log.info("Starting K6 test with command: {}", String.join(" ", command));
            executeK6Process(command);

        } catch (Exception e) {
            log.error("Failed to start K6 test", e);
            isRunning.set(false);
            lastTestResult.put("status", "error");
            lastTestResult.put("error", e.getMessage());
        }
    }

    @Override
    public void startLongScenario(String scenarioName) {
        if (isRunning.getAndSet(true)) {
            log.warn("K6 test already running, skipping long scenario request");
            return;
        }

        lastTestResult.clear();
        lastTestResult.put("status", "running");
        lastTestResult.put("scenario", scenarioName);
        lastTestResult.put("type", "long");
        lastTestResult.put("startTime", String.valueOf(System.currentTimeMillis()));

        try {
            List<String> command = new ArrayList<>();
            command.add(dockerCommand);
            command.add("run");
            command.add("--rm");
            command.add("--network");
            command.add(networkName);
            command.add("-v");
            command.add(new File(hostProjectPath, "k6").getAbsolutePath() + ":/scripts");
            command.add("-e");
            command.add("SCENARIO=" + scenarioName);
            command.add("-e");
            command.add("BASE_URL=" + baseUrl);
            command.add("grafana/k6:latest");
            command.add("run");
            command.add("/scripts/long-scenarios.js");

            log.info("Starting K6 long scenario with command: {}", String.join(" ", command));
            executeK6Process(command);

        } catch (Exception e) {
            log.error("Failed to start K6 long scenario", e);
            isRunning.set(false);
            lastTestResult.put("status", "error");
            lastTestResult.put("error", e.getMessage());
        }
    }

    private void executeK6Process(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        currentK6Process = pb.start();

        // Asynchronously read process output
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentK6Process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("K6: {}", line);
                }
            } catch (Exception e) {
                if (!e.getMessage().contains("Stream closed")) {
                    log.error("Error reading K6 output", e);
                }
            }
        }).start();

        // Asynchronously wait for process completion
        new Thread(() -> {
            try {
                int exitCode = currentK6Process.waitFor();
                lastTestResult.put("status", exitCode == 0 ? "completed" : "failed");
                lastTestResult.put("exitCode", String.valueOf(exitCode));
                log.info("K6 test finished with exit code: {}", exitCode);
            } catch (InterruptedException e) {
                lastTestResult.put("status", "interrupted");
                Thread.currentThread().interrupt();
            } finally {
                lastTestResult.put("endTime", String.valueOf(System.currentTimeMillis()));
                isRunning.set(false);
            }
        }).start();
    }

    @Override
    public void stopK6Test() {
        log.info("Attempting to stop K6 test...");
        // Find and stop the container using its ancestor image
        try {
            ProcessBuilder ps = new ProcessBuilder(dockerCommand, "ps", "-q", "--filter", "ancestor=grafana/k6:latest");
            Process p = ps.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String containerId;
                while ((containerId = reader.readLine()) != null) {
                    if (!containerId.isBlank()) {
                        log.info("Found running K6 container: {}. Stopping it...", containerId);
                        new ProcessBuilder(dockerCommand, "stop", containerId).start().waitFor();
                        log.info("K6 container {} stopped.", containerId);
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            log.error("Error while stopping K6 Docker container", e);
        }

        if (currentK6Process != null && currentK6Process.isAlive()) {
            log.info("Destroying K6 process handle.");
            currentK6Process.destroy();
        }
        
        isRunning.set(false);
        lastTestResult.put("status", "stopped");
    }

    @Override
    public boolean isK6Running() {
        return isRunning.get();
    }

    @Override
    public Map<String, String> getLastTestResult() {
        return Map.copyOf(lastTestResult);
    }


    @Override
    public Map<String, Object> getK6Status() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("running", isRunning.get());
        if (!lastTestResult.isEmpty()) {
            status.put("lastTest", Map.copyOf(lastTestResult));
        }
        return status;
    }
}
