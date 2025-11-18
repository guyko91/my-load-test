package com.dw.idstrust.loadtesttoy.service;

import com.dw.idstrust.loadtesttoy.entity.Order;
import com.dw.idstrust.loadtesttoy.repository.OrderRepository;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class DatabaseService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    private final OrderRepository orderRepository;
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final Random random = new Random();

    private static final String[] CUSTOMER_NAMES = {
            "김철수", "이영희", "박민수", "최지원", "정현우",
            "강서연", "윤태영", "임수진", "한지훈", "오민지",
            "서준호", "권나영", "송재현", "안혜진", "장동민"
    };

    private static final String[] PRODUCT_NAMES = {
            "노트북", "스마트폰", "태블릿", "모니터", "키보드",
            "마우스", "헤드셋", "웹캠", "SSD", "RAM",
            "그래픽카드", "파워서플라이", "케이스", "쿨러", "메인보드"
    };

    private static final String[] STATUSES = {
            "PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"
    };

    private static final String[] ADDRESSES = {
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 송파구 올림픽로 456",
            "경기도 성남시 분당구 정자일로 789",
            "서울특별시 마포구 월드컵북로 321",
            "인천광역시 연수구 센트럴로 654"
    };

    public DatabaseService(OrderRepository orderRepository, DataSource dataSource, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    @Transactional
    public void initDummyData() {
        long count = orderRepository.count();
        if (count == 0) {
            log.info("Initializing dummy data...");
            createDummyOrders(100000);
            log.info("Dummy data initialization completed: {} orders created", 100000);
        } else {
            log.info("Database already contains {} orders, skipping initialization", count);
        }
    }

    @Transactional
    public void createDummyOrders(int count) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(generateRandomOrder());
            if (orders.size() >= 100) {
                orderRepository.saveAll(orders);
                orders.clear();
            }
        }
        if (!orders.isEmpty()) {
            orderRepository.saveAll(orders);
        }
    }

    private Order generateRandomOrder() {
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String customerName = CUSTOMER_NAMES[random.nextInt(CUSTOMER_NAMES.length)];
        String productName = PRODUCT_NAMES[random.nextInt(PRODUCT_NAMES.length)];
        Integer quantity = random.nextInt(10) + 1;
        BigDecimal unitPrice = BigDecimal.valueOf(10000 + random.nextInt(990000));
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        LocalDateTime orderDate = LocalDateTime.now().minusDays(random.nextInt(365));
        String status = STATUSES[random.nextInt(STATUSES.length)];
        String address = ADDRESSES[random.nextInt(ADDRESSES.length)];

        return new Order(orderNumber, customerName, productName, quantity,
                unitPrice, totalPrice, orderDate, status, address);
    }

    // 다양한 조회 쿼리 (부하 생성용)
    @Transactional(readOnly = true, timeout = 10)
    public List<Order> findRandomOrders(int limit) {
        return orderRepository.findRandomOrders(limit);
    }

    @Transactional(readOnly = true, timeout = 10)
    public Page<Order> findOrdersWithPagination(int page, int size) {
        return orderRepository.findAll(PageRequest.of(page, size, Sort.by("orderDate").descending()));
    }

    @Transactional(readOnly = true, timeout = 10)
    public List<Order> findByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    @Transactional(readOnly = true, timeout = 10)
    public List<Order> findHighValueOrders(BigDecimal minPrice) {
        return orderRepository.findHighValueOrders(minPrice);
    }

    @Transactional(readOnly = true, timeout = 10)
    public List<Order> findOrdersByDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findOrdersByDateRange(start, end);
    }

    // 복합 쿼리 부하 생성
    @Transactional(readOnly = true, timeout = 10)
    public void executeComplexQuery() {
        // 여러 쿼리를 조합하여 DB 부하 생성
        findOrdersWithPagination(0, 50);
        findByStatus("CONFIRMED");
        findHighValueOrders(BigDecimal.valueOf(500000));
        findOrdersByDateRange(LocalDateTime.now().minusMonths(1), LocalDateTime.now());
    }

    public long getOrderCount() {
        return orderRepository.count();
    }

    // --- Hikari Pool Control ---
    public int updateMaxPoolSize(int size) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            int minIdle = hikariDataSource.getMinimumIdle();
            if (size < minIdle) {
                log.warn("Requested max pool size ({}) is less than minimum idle ({}). Adjusting to {}.", size, minIdle, minIdle);
                size = minIdle;
            }
            log.info("Updating Hikari max pool size to: {}", size);
            hikariDataSource.setMaximumPoolSize(size);
            return size;
        } else {
            log.warn("Datasource is not a HikariDataSource, cannot update max pool size.");
            return -1;
        }
    }

    public int getMaxPoolSize() {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getMaximumPoolSize();
        }
        return -1; // Not a Hikari pool
    }

    public Map<String, Number> getPoolStatus() {
        return Map.of(
                "active", meterRegistry.get("hikaricp.connections.active").gauge().value(),
                "idle", meterRegistry.get("hikaricp.connections.idle").gauge().value(),
                "pending", meterRegistry.get("hikaricp.connections.pending").gauge().value(),
                "max", meterRegistry.get("hikaricp.connections.max").gauge().value()
        );
    }

    @Transactional(timeout = 10)
    public boolean processRecentOrder(String customerName) {
        Optional<Order> recentOrder = orderRepository.findTopByCustomerNameAndStatusOrderByOrderDateDesc(customerName, "PENDING");
        if (recentOrder.isPresent()) {
            Order order = recentOrder.get();
            order.setStatus("CONFIRMED");
            orderRepository.save(order);
            return true;
        }
        return false;
    }
}
