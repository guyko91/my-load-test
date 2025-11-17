package com.dw.idstrust.loadtesttoy.controller;

import com.dw.idstrust.loadtesttoy.config.LoadScenarioProperties;
import com.dw.idstrust.loadtesttoy.service.LoadGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/load")
public class LoadController {
    private final LoadGeneratorService service;
    private final LoadScenarioProperties props;

    public LoadController(LoadGeneratorService service, LoadScenarioProperties props) {
        this.service = service;
        this.props = props;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().body(java.util.Map.of("running", service.isRunning()));
    }

    @GetMapping("/scenarios")
    public ResponseEntity<?> scenarios() {
        List<?> list = props.getDefinitions() == null ? List.of() : props.getDefinitions().stream().map(s -> java.util.Map.of(
                "name", s.getName(),
                "durationSeconds", s.getDuration().getSeconds(),
                "concurrency", s.getConcurrency(),
                "cpuLoadPercent", s.getCpuLoadPercent(),
                "ioOpsPerSecond", s.getIoOpsPerSecond()
        )).collect(Collectors.toList());
        return ResponseEntity.ok().body(list);
    }

    @PostMapping("/trigger/{name}")
    public ResponseEntity<?> trigger(@PathVariable String name) {
        if (props.getDefinitions() == null) return ResponseEntity.notFound().build();
        for (LoadScenarioProperties.Scenario s : props.getDefinitions()) {
            if (s.getName() != null && s.getName().equals(name)) {
                service.triggerScenario(s);
                return ResponseEntity.accepted().body(java.util.Map.of("triggered", name));
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        service.stopAll();
        return ResponseEntity.ok().body(java.util.Map.of("stopped", true));
    }
}

