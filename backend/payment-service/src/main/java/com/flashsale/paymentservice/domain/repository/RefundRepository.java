package com.flashsale.paymentservice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentservice.domain.model.Refund;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByOrderId(Long orderId);
    List<Refund> findAllByOrderId(Long orderId);

    @Query("""
        SELECT r FROM Refund r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:type IS NULL OR r.type = :type)
          AND (:fromDate IS NULL OR r.createdAt >= :fromDate)
          AND (:toDate IS NULL OR r.createdAt <= :toDate)
        ORDER BY r.createdAt DESC
        """)
    Page<Refund> findAllWithFilters(
        @Param("status") String status,
        @Param("type") String type,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );

    @Query("""
        SELECT r FROM Refund r
        WHERE r.userId = :userId
          AND (:status IS NULL OR r.status = :status)
          AND (:type IS NULL OR r.type = :type)
          AND (:fromDate IS NULL OR r.createdAt >= :fromDate)
          AND (:toDate IS NULL OR r.createdAt <= :toDate)
        ORDER BY r.createdAt DESC
        """)
    Page<Refund> findAllByUserIdWithFilters(
        @Param("userId") Long userId,
        @Param("status") String status,
        @Param("type") String type,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );

    boolean existsByOrderIdAndStatus(Long orderId, String status);

    boolean existsByOrderIdAndStatusIn(Long orderId, List<String> statuses);

    Optional<Refund> findByRefundRef(String refundRef);
}
