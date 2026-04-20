package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CartRepository extends MongoRepository<Cart, String> {
    Optional<Cart> findByUserId(Long userId);
}

