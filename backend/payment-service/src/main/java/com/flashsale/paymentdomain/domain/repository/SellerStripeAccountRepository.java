package com.flashsale.paymentdomain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentdomain.domain.model.SellerStripeAccount;
import java.util.Optional;

@Repository
public interface SellerStripeAccountRepository extends JpaRepository<SellerStripeAccount, Long> {
    Optional<SellerStripeAccount> findBySellerId(Long sellerId);
    Optional<SellerStripeAccount> findByStripeAccountId(String stripeAccountId);
}

