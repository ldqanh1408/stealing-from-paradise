package com.flashsale.paymentdomain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentdomain.domain.model.SellerTransfer;
import java.util.Optional;

@Repository
public interface SellerTransferRepository extends JpaRepository<SellerTransfer, Long> {
    Optional<SellerTransfer> findByOrderId(Long orderId);
}

