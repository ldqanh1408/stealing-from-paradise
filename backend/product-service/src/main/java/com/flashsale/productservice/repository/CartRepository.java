package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerIdAndDeletedAtIsNull(Long customerId);

    Optional<Cart> findByIdAndDeletedAtIsNull(UUID id);
}
