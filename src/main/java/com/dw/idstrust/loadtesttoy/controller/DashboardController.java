package com.dw.idstrust.loadtesttoy.controller;

import com.dw.idstrust.loadtesttoy.service.DatabaseService;
import com.dw.idstrust.loadtesttoy.service.K6ControlService;
import com.dw.idstrust.loadtesttoy.service.LoadGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class DashboardController {
    private final LoadGeneratorService loadService;
    private final DatabaseService databaseService;
    private final K6ControlService k6Service;

    public DashboardController(LoadGeneratorService loadService,
                               DatabaseService databaseService,
                               K6ControlService k6Service) {
        this.loadService = loadService;
        this.databaseService = databaseService;
        this.k6Service = k6Service;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("orderCount", databaseService.getOrderCount());
        model.addAttribute("k6Running", k6Service.isK6Running());
        model.addAttribute("appRunning", loadService.isRunning());
        return "dashboard";
    }

    // K6 Control API
    @PostMapping("/api/dashboard/k6/start")
    @ResponseBody
    public ResponseEntity<?> startK6(@RequestBody Map<String, Object> request) {
        String scenario = (String) request.getOrDefault("scenario", "realistic");
        int rps = (int) request.getOrDefault("rps", 10);
        int duration = (int) request.getOrDefault("duration", 5);
        int vus = (int) request.getOrDefault("vus", 20);

        k6Service.startK6Test(scenario, rps, duration, vus);
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @PostMapping("/api/dashboard/k6/long-scenario")
    @ResponseBody
    public ResponseEntity<?> startLongScenario(@RequestBody Map<String, Object> request) {
        String scenario = (String) request.getOrDefault("scenario", "daily_pattern");
        k6Service.startLongScenario(scenario);
        return ResponseEntity.ok(Map.of("status", "started", "scenario", scenario));
    }

    @PostMapping("/api/dashboard/k6/stop")
    @ResponseBody
    public ResponseEntity<?> stopK6() {
        k6Service.stopK6Test();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @GetMapping("/api/dashboard/k6/status")
    @ResponseBody
    public ResponseEntity<?> k6Status() {
        return ResponseEntity.ok(k6Service.getK6Status());
    }

    // Database Status API
    @GetMapping("/api/dashboard/db/status")
    @ResponseBody
    public ResponseEntity<?> dbStatus() {
        return ResponseEntity.ok(Map.of(
                "orderCount", databaseService.getOrderCount(),
                "status", "connected"
        ));
    }

    // App Status API
    @GetMapping("/api/dashboard/app/status")
    @ResponseBody
    public ResponseEntity<?> appStatus() {
        return ResponseEntity.ok(Map.of(
                "running", loadService.isRunning()
        ));
    }
}

