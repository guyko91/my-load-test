package com.dw.idstrust.loadtesttoy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "load.scenarios")
public class LoadScenarioProperties {
    private boolean schedulerEnabled = false;
    private List<Scenario> definitions;

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public List<Scenario> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<Scenario> definitions) {
        this.definitions = definitions;
    }

    public static class Scenario {
        private String name;
        private Duration duration = Duration.ofMinutes(5);
        private int concurrency = 10;
        private int cpuLoadPercent = 50; // 0-100
        private int ioOpsPerSecond = 0; // 0 means none

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getCpuLoadPercent() {
            return cpuLoadPercent;
        }

        public void setCpuLoadPercent(int cpuLoadPercent) {
            this.cpuLoadPercent = cpuLoadPercent;
        }

        public int getIoOpsPerSecond() {
            return ioOpsPerSecond;
        }

        public void setIoOpsPerSecond(int ioOpsPerSecond) {
            this.ioOpsPerSecond = ioOpsPerSecond;
        }
    }
}
