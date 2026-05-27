package com.flashsale.refundservice.domain.repository;

import com.flashsale.refundservice.domain.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByParentOrderId(Long parentOrderId);

    @Query("SELECT t FROM Transaction t WHERE t.rawResponse LIKE %:chargeId%")
    Optional<Transaction> findByRawResponseContaining(@Param("chargeId") String chargeId);
}
