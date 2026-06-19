package com.flashsale.paymentservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentservice.domain.model.Transaction;
import java.util.Optional;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByParentOrderId(Long parentOrderId);
    Optional<Transaction> findByOrderId(Long orderId);
    Optional<Transaction> findByStripePaymentIntentId(String stripePaymentIntentId);
}

