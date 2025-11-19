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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * K6 Control Service 구현체.
 * 여러 개의 동시 K6 테스트 실행을 지원하도록 리팩토링됨.
 * 'docker run'을 사용하여 K6 컨테이너를 직접 실행합니다.
 */
@Service
public class K6ControlServiceImpl implements K6ControlService {
    private static final Logger log = LoggerFactory.getLogger(K6ControlServiceImpl.class);

    private final Map<String, K6TestInstance> runningTests = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> lastFinishedTests = new ConcurrentHashMap<>();

    private final String dockerCommand;
    private final String baseUrl;
    private final String networkName;
    private final String k6ScriptsPath;

    /**
     * K6 테스트 인스턴스의 상태를 저장하는 내부 클래스.
     */
    private static class K6TestInstance {
        final String id;
        final String type;
        final Process process;
        final Map<String, String> result = new ConcurrentHashMap<>();

        K6TestInstance(String id, String type, String scenario, Process process) {
            this.id = id;
            this.type = type;
            this.process = process;
            result.put("id", id);
            result.put("type", type);
            result.put("scenario", scenario);
            result.put("status", "running");
            result.put("startTime", String.valueOf(System.currentTimeMillis()));
        }
    }

    public K6ControlServiceImpl(@Value("${k6.base-url:http://app:28080}") String baseUrl,
                                @Value("${k6.docker.network:load-test-net}") String networkName,
                                @Value("${k6.scripts.path.on.host:}") String scriptsPathOnHost,
                                @Value("${k6.scripts.path:}") String scriptsPath) {
        this.baseUrl = baseUrl;
        this.networkName = networkName;
        this.dockerCommand = findDockerCommand();

        if (scriptsPathOnHost != null && !scriptsPathOnHost.isEmpty()) {
            this.k6ScriptsPath = scriptsPathOnHost;
        } else {
            this.k6ScriptsPath = determineScriptsPath(scriptsPath);
        }

        log.info("K6ControlService initialized. Docker: {}, Base URL: {}, Network: {}, Scripts: {}",
                dockerCommand, baseUrl, networkName, k6ScriptsPath);
    }

    @Override
    public String startTest(String testType, String scenario, int rps, int durationMinutes, int vus, String scriptName) {
        // 동일한 타입의 테스트가 이미 실행 중인지 확인
        if (runningTests.values().stream().anyMatch(t -> t.type.equals(testType))) {
            log.warn("A K6 test of type '{}' is already running. Skipping new request.", testType);
            return null;
        }

        String testId = testType + "-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            List<String> command = buildDockerCommand(testId, scenario, rps, durationMinutes, vus, scriptName);
            log.info("Starting K6 test [ID: {}] with command: {}", testId, String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // 표준 에러를 표준 출력으로 리다이렉트
            Process process = pb.start();

            K6TestInstance instance = new K6TestInstance(testId, testType, scenario, process);
            runningTests.put(testId, instance);

            executeK6Process(instance);

            return testId;

        } catch (Exception e) {
            log.error("Failed to start K6 test [ID: {}]", testId, e);
            return null;
        }
    }

    private List<String> buildDockerCommand(String testId, String scenario, int rps, int durationMinutes, int vus, String scriptName) {
        List<String> command = new ArrayList<>();
        command.add(dockerCommand);
        command.add("run");
        command.add("--rm");
        command.add("--name"); // 컨테이너에 예측 가능한 이름 부여
        command.add("k6-" + testId);
        command.add("--user");
        command.add("root");
        command.add("--network");
        command.add(networkName);
        command.add("--add-host");
        command.add("host.docker.internal:host-gateway");
        command.add("-v");
        command.add(k6ScriptsPath + ":/scripts:ro");
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
        command.add("/scripts/" + scriptName);
        return command;
    }

    private void executeK6Process(K6TestInstance instance) {
        // Asynchronously read process output
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(instance.process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("K6 [{}]: {}", instance.id, line);
                }
            } catch (Exception e) {
                if (instance.process.isAlive() && !e.getMessage().contains("Stream closed")) {
                    log.error("Error reading K6 output for test {}", instance.id, e);
                }
            }
        }).start();

        // Asynchronously wait for process completion
        new Thread(() -> {
            try {
                int exitCode = instance.process.waitFor();
                instance.result.put("status", exitCode == 0 ? "completed" : "failed");
                instance.result.put("exitCode", String.valueOf(exitCode));
                log.info("K6 test [{}] finished with exit code: {}", instance.id, exitCode);
            } catch (InterruptedException e) {
                instance.result.put("status", "interrupted");
                Thread.currentThread().interrupt();
                log.warn("K6 test [{}] was interrupted.", instance.id);
            } finally {
                runningTests.remove(instance.id);
                lastFinishedTests.put(instance.type, instance.result); // 타입별로 마지막 결과 저장
            }
        }).start();
    }

    @Override
    public void stopTest(String testId) {
        K6TestInstance instance = runningTests.get(testId);
        if (instance == null) {
            log.warn("Attempted to stop a test that is not running or does not exist: {}", testId);
            return;
        }

        log.info("Attempting to stop K6 test [ID: {}]...", testId);
        String containerName = "k6-" + testId;

        // 1. Stop the docker container by name
        try {
            log.info("Stopping K6 container with name: {}", containerName);
            new ProcessBuilder(dockerCommand, "stop", containerName).start().waitFor();
            log.info("K6 container {} stopped.", containerName);
        } catch (Exception e) {
            log.error("Error while stopping K6 Docker container {}", containerName, e);
        }

        // 2. Destroy the process handle
        if (instance.process.isAlive()) {
            log.info("Destroying K6 process handle for test [{}].", testId);
            instance.process.destroy();
        }

        // 3. Update status
        instance.result.put("status", "stopped");
        instance.result.put("endTime", String.valueOf(System.currentTimeMillis()));
        runningTests.remove(testId);
        lastFinishedTests.put(instance.type, instance.result);
    }


    @Override
    public void stopAllTests() {
        log.info("Attempting to stop all running K6 tests...");

        // 1. Stop running docker containers
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
            log.error("Error while stopping K6 Docker containers", e);
        }

        // 2. Destroy process handles and update status
        runningTests.forEach((id, instance) -> {
            if (instance.process.isAlive()) {
                log.info("Destroying K6 process handle for test [{}].", id);
                instance.process.destroy();
            }
            instance.result.put("status", "stopped");
            instance.result.put("endTime", String.valueOf(System.currentTimeMillis()));
            lastFinishedTests.put(instance.type, instance.result);
        });
        runningTests.clear();
    }

    @Override
    public Map<String, Object> getStatus() {
        List<Map<String, String>> running = runningTests.values().stream()
                .map(instance -> Map.copyOf(instance.result))
                .collect(Collectors.toList());

        return Map.of(
                "runningTests", running,
                "lastFinishedTests", Map.copyOf(lastFinishedTests)
        );
    }

    // --- Helper Methods (unchanged) ---

    private String determineScriptsPath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isEmpty()) {
            return configuredPath;
        }
        String userDir = System.getProperty("user.dir");
        File projectK6 = new File(userDir, "k6");
        if (projectK6.exists() && projectK6.isDirectory()) {
            return projectK6.getAbsolutePath();
        }
        File dockerPath = new File("/host-k6");
        if (dockerPath.exists() && dockerPath.isDirectory()) {
            return "/host-k6";
        }
        log.warn("Could not find k6 scripts directory, defaulting to /host-k6");
        return "/host-k6";
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
        return "docker";
    }
}