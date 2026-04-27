package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    Optional<CartItem> findByCartIdAndSkuId(UUID cartId, UUID skuId);

    List<CartItem> findByCartId(UUID cartId);

    List<CartItem> findByIdInAndCartId(Collection<UUID> ids, UUID cartId);

    void deleteByCartId(UUID cartId);
}
