package com.flashsale.productdomain.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.productdomain.domain.model.Product;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    Optional<Product> findByIdAndSellerId(String id, Long sellerId);
    // Add custom queries as needed
}

