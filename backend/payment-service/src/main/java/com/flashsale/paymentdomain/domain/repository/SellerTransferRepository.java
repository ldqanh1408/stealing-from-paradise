package com.flashsale.paymentdomain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentdomain.domain.model.SellerTransfer;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellerTransferRepository extends JpaRepository<SellerTransfer, Long> {
    Optional<SellerTransfer> findByOrderId(Long orderId);
    List<SellerTransfer> findAllByParentOrderId(Long parentOrderId);
    List<SellerTransfer> findAllByOrderId(Long orderId);

    @Query("SELECT t FROM SellerTransfer t WHERE t.sellerId = :sellerId ORDER BY t.createdAt DESC")
    List<SellerTransfer> findAllBySellerIdOrderByCreatedAtDesc(@Param("sellerId") Long sellerId);
}
