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
        model.addAttribute("appRunning", loadService.isRunning());
        // k6Running status is now fetched dynamically by the frontend
        return "dashboard";
    }

    // K6 Control API
    @PostMapping("/api/dashboard/k6/start")
    @ResponseBody
    public ResponseEntity<?> startK6(@RequestBody Map<String, Object> request) {
        String testType = (String) request.getOrDefault("testType", "scenario"); // "baseline" or "scenario"
        String scenario = (String) request.getOrDefault("scenario", "realistic");
        int rps = (int) request.getOrDefault("rps", 10);
        int duration = (int) request.getOrDefault("duration", 5);
        int vus = (int) request.getOrDefault("vus", 20);
        String scriptName = (String) request.getOrDefault("script", "dynamic.js");

        String testId = k6Service.startTest(testType, scenario, rps, duration, vus, scriptName);

        if (testId != null) {
            return ResponseEntity.ok(Map.of("status", "started", "testId", testId));
        } else {
            return ResponseEntity.status(409).body(Map.of("status", "conflict", "message", "A test of type '" + testType + "' is already running."));
        }
    }

    @PostMapping("/api/dashboard/k6/stop/{testId}")
    @ResponseBody
    public ResponseEntity<?> stopK6Test(@PathVariable String testId) {
        k6Service.stopTest(testId);
        return ResponseEntity.ok(Map.of("status", "stopped", "testId", testId));
    }

    @PostMapping("/api/dashboard/k6/stop-all")
    @ResponseBody
    public ResponseEntity<?> stopAllK6() {
        k6Service.stopAllTests();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @GetMapping("/api/dashboard/k6/status")
    @ResponseBody
    public ResponseEntity<?> k6Status() {
        return ResponseEntity.ok(k6Service.getStatus());
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

    @GetMapping("/api/dashboard/db/pool-status")
    @ResponseBody
    public ResponseEntity<?> dbPoolStatus() {
        return ResponseEntity.ok(databaseService.getPoolStatus());
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