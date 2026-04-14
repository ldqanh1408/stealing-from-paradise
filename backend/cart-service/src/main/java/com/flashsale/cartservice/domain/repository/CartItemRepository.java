package com.flashsale.cartservice.domain.repository;

import com.flashsale.cartservice.domain.model.CartItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends MongoRepository<CartItem, String> {
    List<CartItem> findByCartId(String cartId);

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByCartIdAndSkuCode(String cartId, String skuCode);

    void deleteByCartId(String cartId);

    long countByCartId(String cartId);
}

