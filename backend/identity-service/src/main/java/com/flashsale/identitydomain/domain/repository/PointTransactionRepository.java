package com.flashsale.identitydomain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.identitydomain.domain.model.PointTransaction;
import java.util.List;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByUserId(Long userId);
    List<PointTransaction> findByOrderId(Long orderId);
}

