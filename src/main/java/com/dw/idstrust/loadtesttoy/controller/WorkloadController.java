package com.dw.idstrust.loadtesttoy.controller;

import com.dw.idstrust.loadtesttoy.entity.Order;
import com.dw.idstrust.loadtesttoy.service.DatabaseService;
import com.dw.idstrust.loadtesttoy.service.LoadGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workload")
public class WorkloadController {

    private final LoadGeneratorService loadService;
    private final DatabaseService databaseService;

    public WorkloadController(LoadGeneratorService loadService, DatabaseService databaseService) {
        this.loadService = loadService;
        this.databaseService = databaseService;
    }

    // CPU 부하 생성
    @PostMapping("/cpu")
    public ResponseEntity<?> cpuLoad(@RequestBody Map<String, Object> request) {
        int durationMs = (int) request.getOrDefault("durationMs", 1000);
        int cpuPercent = (int) request.getOrDefault("cpuPercent", 50);

        loadService.executeWorkload(durationMs, cpuPercent, 0);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "durationMs", durationMs,
                "cpuPercent", cpuPercent
        ));
    }

    // IO 부하 생성
    @PostMapping("/io")
    public ResponseEntity<?> ioLoad(@RequestBody Map<String, Object> request) {
        int durationMs = (int) request.getOrDefault("durationMs", 1000);
        int ioOps = (int) request.getOrDefault("ioOps", 10);

        loadService.executeWorkload(durationMs, 0, ioOps);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "durationMs", durationMs,
                "ioOps", ioOps
        ));
    }

    // 혼합 부하 생성
    @PostMapping("/mixed")
    public ResponseEntity<?> mixedLoad(@RequestBody Map<String, Object> request) {
        int durationMs = (int) request.getOrDefault("durationMs", 1000);
        int cpuPercent = (int) request.getOrDefault("cpuPercent", 50);
        int ioOps = (int) request.getOrDefault("ioOps", 5);

        loadService.executeWorkload(durationMs, cpuPercent, ioOps);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "durationMs", durationMs,
                "cpuPercent", cpuPercent,
                "ioOps", ioOps
        ));
    }

    // DB 조회 부하 (단순 조회)
    @GetMapping("/db/query")
    public ResponseEntity<?> dbQuery(@RequestParam(defaultValue = "10") int limit) {
        List<Order> orders = databaseService.findRandomOrders(limit);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "resultCount", orders.size()
        ));
    }

    // DB 조회 부하 (복합 쿼리)
    @PostMapping("/db/complex")
    public ResponseEntity<?> dbComplexQuery() {
        databaseService.executeComplexQuery();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "queryType", "complex"
        ));
    }

    // DB 조회 부하 (상태별 조회)
    @GetMapping("/db/status/{status}")
    public ResponseEntity<?> dbQueryByStatus(@PathVariable String status) {
        List<Order> orders = databaseService.findByStatus(status);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "queryStatus", status,
                "resultCount", orders.size()
        ));
    }

    // DB 조회 부하 (고액 주문)
    @GetMapping("/db/high-value")
    public ResponseEntity<?> dbHighValueOrders(@RequestParam(defaultValue = "500000") long minPrice) {
        List<Order> orders = databaseService.findHighValueOrders(BigDecimal.valueOf(minPrice));
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "minPrice", minPrice,
                "resultCount", orders.size()
        ));
    }

    // DB 조회 부하 (기간별 조회)
    @GetMapping("/db/date-range")
    public ResponseEntity<?> dbDateRangeQuery(@RequestParam(defaultValue = "30") int daysAgo) {
        LocalDateTime start = LocalDateTime.now().minusDays(daysAgo);
        LocalDateTime end = LocalDateTime.now();
        List<Order> orders = databaseService.findOrdersByDateRange(start, end);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "daysAgo", daysAgo,
                "resultCount", orders.size()
        ));
    }

    // DB + CPU 혼합 부하 (현실적인 시나리오)
    @PostMapping("/realistic")
    public ResponseEntity<?> realisticWorkload(@RequestBody Map<String, Object> request) {
        int durationMs = (int) request.getOrDefault("durationMs", 500);
        int cpuPercent = (int) request.getOrDefault("cpuPercent", 30);

        // DB 조회 먼저
        databaseService.executeComplexQuery();

        // CPU 작업 수행
        loadService.executeWorkload(durationMs, cpuPercent, 0);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "type", "realistic",
                "durationMs", durationMs,
                "cpuPercent", cpuPercent
        ));
    }

    // DB 상태 확인
    @GetMapping("/db/status")
    public ResponseEntity<?> dbStatus() {
        long orderCount = databaseService.getOrderCount();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "orderCount", orderCount
        ));
    }

    // 더미 데이터 추가 생성
    @PostMapping("/db/generate")
    public ResponseEntity<?> generateData(@RequestParam(defaultValue = "100") int count) {
        databaseService.createDummyOrders(count);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "generated", count,
                "totalCount", databaseService.getOrderCount()
        ));
    }

    // --- DB Pool Control ---
    @GetMapping("/db/pool-size")
    public ResponseEntity<?> getPoolSize() {
        return ResponseEntity.ok(Map.of("maxPoolSize", databaseService.getMaxPoolSize()));
    }

    @PostMapping("/db/pool-size")
    public ResponseEntity<?> setPoolSize(@RequestBody Map<String, Integer> request) {
        Integer maxPoolSize = request.get("maxPoolSize");
        if (maxPoolSize == null || maxPoolSize < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid maxPoolSize"));
        }
        databaseService.updateMaxPoolSize(maxPoolSize);
        return ResponseEntity.ok(Map.of("status", "updated", "maxPoolSize", maxPoolSize));
    }
}
