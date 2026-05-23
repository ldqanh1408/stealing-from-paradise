package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByCartIdAndVariantIdAndDeletedAtIsNull(UUID cartId, UUID variantId);

    List<CartItem> findByCartIdAndDeletedAtIsNull(UUID cartId);

    @Query("SELECT ci FROM CartItem ci WHERE ci.id IN :ids AND ci.cartId = :cartId AND ci.deletedAt IS NULL")
    List<CartItem> findByIdsAndCartIdAndNotDeleted(@Param("ids") List<UUID> ids, @Param("cartId") UUID cartId);
}
