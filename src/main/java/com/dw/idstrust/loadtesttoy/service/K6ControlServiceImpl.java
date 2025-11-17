package com.dw.idstrust.loadtesttoy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * K6 Control Service 구현체
 * docker compose run으로 K6 컨테이너 실행
 */
@Service
public class K6ControlServiceImpl implements K6ControlService {
    private static final Logger log = LoggerFactory.getLogger(K6ControlServiceImpl.class);

    private Process currentK6Process;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Map<String, String> lastTestResult = new ConcurrentHashMap<>();
    private final String dockerCommand;
    private final String baseUrl;

    public K6ControlServiceImpl(@Value("${k6.base-url:http://host.docker.internal:28080}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.dockerCommand = findDockerCommand();
        log.info("K6ControlService initialized. Docker: {}, Base URL: {}", dockerCommand, baseUrl);
    }

    private String findDockerCommand() {
        // 일반적인 Docker 설치 경로들
        String[] possiblePaths = {
            "/usr/local/bin/docker",
            "/usr/bin/docker",
            "/opt/homebrew/bin/docker",
            "docker" // PATH에서 찾기
        };

        for (String path : possiblePaths) {
            try {
                Process p = new ProcessBuilder(path, "--version").start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    return path;
                }
            } catch (Exception e) {
                // 다음 경로 시도
            }
        }

        // 기본값
        return "docker";
    }

    @Override
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

            // Docker Compose 환경에서 K6 컨테이너 실행
            List<String> command = new ArrayList<>();
            command.add(dockerCommand);
            command.add("compose");
            command.add("run");
            command.add("--rm");
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
            command.add("k6");
            command.add("run");
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

    @Override
    public void startLongScenario(String scenarioName) {
        if (isRunning.get()) {
            log.warn("K6 test already running, skipping long scenario request");
            return;
        }

        try {
            isRunning.set(true);
            lastTestResult.clear();
            lastTestResult.put("status", "running");
            lastTestResult.put("scenario", scenarioName);
            lastTestResult.put("type", "long");
            lastTestResult.put("startTime", String.valueOf(System.currentTimeMillis()));

            // Docker Compose 환경에서 K6 컨테이너 실행
            List<String> command = new ArrayList<>();
            command.add(dockerCommand);
            command.add("compose");
            command.add("run");
            command.add("--rm");
            command.add("-e");
            command.add("SCENARIO=" + scenarioName);
            command.add("-e");
            command.add("BASE_URL=" + baseUrl);
            command.add("k6");
            command.add("run");
            command.add("/scripts/long-scenarios.js");

            log.info("Starting K6 long scenario: {}", scenarioName);

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
                    log.info("K6 long scenario finished with exit code: {}", exitCode);
                } catch (InterruptedException e) {
                    lastTestResult.put("status", "interrupted");
                    Thread.currentThread().interrupt();
                } finally {
                    isRunning.set(false);
                }
            }).start();

        } catch (Exception e) {
            log.error("Failed to start K6 long scenario", e);
            isRunning.set(false);
            lastTestResult.put("status", "error");
            lastTestResult.put("error", e.getMessage());
        }
    }

    @Override
    public void stopK6Test() {
        log.info("Stopping K6 test");

        // 1. 실행 중인 K6 컨테이너 찾아서 중지
        try {
            List<String> findCommand = new ArrayList<>();
            findCommand.add(dockerCommand);
            findCommand.add("ps");
            findCommand.add("--filter");
            findCommand.add("ancestor=grafana/k6:latest");
            findCommand.add("--format");
            findCommand.add("{{.ID}}");

            ProcessBuilder pb = new ProcessBuilder(findCommand);
            Process p = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String containerId;
            while ((containerId = reader.readLine()) != null) {
                if (!containerId.trim().isEmpty()) {
                    log.info("Stopping K6 container: {}", containerId);
                    List<String> stopCommand = new ArrayList<>();
                    stopCommand.add(dockerCommand);
                    stopCommand.add("stop");
                    stopCommand.add(containerId);

                    ProcessBuilder stopPb = new ProcessBuilder(stopCommand);
                    Process stopProcess = stopPb.start();
                    stopProcess.waitFor();
                    log.info("K6 container {} stopped", containerId);
                }
            }
            reader.close();
            p.waitFor();

        } catch (Exception e) {
            log.error("Error stopping K6 container", e);
        }

        // 2. Java 프로세스도 종료
        if (currentK6Process != null && currentK6Process.isAlive()) {
            currentK6Process.destroy();
            try {
                currentK6Process.waitFor();
            } catch (InterruptedException e) {
                currentK6Process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        lastTestResult.put("status", "stopped");
        isRunning.set(false);
        log.info("K6 test stopped");
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
