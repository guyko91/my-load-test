package com.dw.idstrust.loadtesttoy.repository;

import com.dw.idstrust.loadtesttoy.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findAll(Pageable pageable);

    List<Order> findByStatus(String status);

    List<Order> findByCustomerName(String customerName);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :startDate AND :endDate")
    List<Order> findOrdersByDateRange(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE o.totalPrice > :minPrice ORDER BY o.totalPrice DESC")
    List<Order> findHighValueOrders(@Param("minPrice") java.math.BigDecimal minPrice);

    @Query(value = "SELECT * FROM ORDERS WHERE ROWNUM <= :limit ORDER BY DBMS_RANDOM.VALUE", nativeQuery = true)
    List<Order> findRandomOrders(@Param("limit") int limit);
}
