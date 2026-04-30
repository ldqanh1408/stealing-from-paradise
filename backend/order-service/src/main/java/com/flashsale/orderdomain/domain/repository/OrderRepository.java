package com.flashsale.orderdomain.domain.repository;

import com.flashsale.orderdomain.domain.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findByIdAndSellerId(Long id, Long sellerId);

    // Buyer: lấy danh sách đơn hàng, lọc theo status và ngày
    @Query("""
        SELECT o FROM Order o
        WHERE o.userId = :userId
          AND (:status IS NULL OR o.status = :status)
          AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
          AND (:toDate   IS NULL OR o.createdAt <= :toDate)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findByUserIdWithFilters(
            @Param("userId")   Long userId,
            @Param("status")   String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate")   LocalDateTime toDate,
            Pageable pageable);

    // Payment service: cập nhật trạng thái theo parent_order_id
    List<Order> findAllByParentOrderIdAndStatus(Long parentOrderId, String status);

    /**
     * Pessimistic lock on sub-orders during payment confirmation.
     * Prevents concurrent saga instances from reading stale data while
     * another transaction is modifying the same parent_order's sub-orders.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.parentOrderId = :parentOrderId AND o.status = :status")
    List<Order> findAllByParentOrderIdAndStatusWithLock(
            @Param("parentOrderId") Long parentOrderId,
            @Param("status") String status);

    List<Order> findAllByParentOrderId(Long parentOrderId);

    // Seller: lấy danh sách đơn hàng
    @Query("""
        SELECT o FROM Order o
        WHERE o.sellerId = :sellerId
          AND (:status IS NULL OR o.status = :status)
          AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
          AND (:toDate   IS NULL OR o.createdAt <= :toDate)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findBySellerIdWithFilters(
            @Param("sellerId") Long sellerId,
            @Param("status")   String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate")   LocalDateTime toDate,
            Pageable pageable);

    // ─── Dashboard stats ───────────────────────────────────────────────────────

    long countBySellerIdAndCreatedAtAfter(Long sellerId, LocalDateTime after);

    long countBySellerIdAndStatus(Long sellerId, String status);

    @Query("""
        SELECT COALESCE(SUM(o.finalAmt), 0) FROM Order o
        WHERE o.sellerId = :sellerId
          AND o.status <> 'CANCELLED'
          AND o.createdAt >= :since
        """)
    BigDecimal sumRevenueForSellerSince(@Param("sellerId") Long sellerId, @Param("since") LocalDateTime since);
}
