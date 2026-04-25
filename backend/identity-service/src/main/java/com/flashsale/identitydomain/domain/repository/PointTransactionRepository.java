package com.flashsale.identitydomain.domain.repository;

import com.flashsale.identitydomain.domain.model.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PointTransaction> findByOrderId(Long orderId);

    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT pt FROM PointTransaction pt WHERE pt.userId = :userId " +
           "AND (:type IS NULL OR pt.type = :type) " +
           "AND (:status IS NULL OR pt.status = :status) " +
           "ORDER BY pt.createdAt DESC")
    Page<PointTransaction> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(pt.remainingDelta), 0) FROM PointTransaction pt " +
           "WHERE pt.userId = :userId AND pt.type = 'EARNED' AND pt.status = 'PENDING' " +
           "AND pt.expiresAt IS NOT NULL AND pt.expiresAt < CURRENT_TIMESTAMP")
    int sumExpiredPendingPoints(@Param("userId") Long userId);

    Optional<PointTransaction> findByOrderIdAndType(Long orderId, String type);
}

