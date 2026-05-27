package com.flashsale.paymentservice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellerTransferRepository extends JpaRepository<SellerTransfer, Long> {
    Optional<SellerTransfer> findByOrderId(Long orderId);
    @Query("SELECT t FROM SellerTransfer t WHERE t.orderId = :parentOrderId")
    List<SellerTransfer> findAllByParentOrderId(@Param("parentOrderId") Long parentOrderId);
    List<SellerTransfer> findAllByOrderId(Long orderId);

    @Query("SELECT t FROM SellerTransfer t WHERE t.sellerId = :sellerId ORDER BY t.createdAt DESC")
    List<SellerTransfer> findAllBySellerIdOrderByCreatedAtDesc(@Param("sellerId") Long sellerId);

    @Query("""
        SELECT t FROM SellerTransfer t
        WHERE t.sellerId = :sellerId
          AND (:status IS NULL OR t.status = :status)
          AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
          AND (:toDate IS NULL OR t.createdAt <= :toDate)
        ORDER BY t.createdAt DESC
        """)
    Page<SellerTransfer> findAllBySellerIdWithFilters(
        @Param("sellerId") Long sellerId,
        @Param("status") String status,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );

    /** Find transfers whose return window has expired and are ready for payout. */
    @Query("SELECT t FROM SellerTransfer t WHERE t.status = :status AND t.payoutEligibleAt <= :cutoff ORDER BY t.payoutEligibleAt ASC")
    List<SellerTransfer> findByStatusAndPayoutEligibleAtBefore(
        @Param("status") String status,
        @Param("cutoff") LocalDateTime cutoff,
        Pageable pageable
    );
}
