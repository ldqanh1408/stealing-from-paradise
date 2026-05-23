package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByCartIdAndVariantIdAndDeletedAtIsNull(UUID cartId, UUID variantId);

    List<CartItem> findByCartIdAndDeletedAtIsNull(UUID cartId);
}
