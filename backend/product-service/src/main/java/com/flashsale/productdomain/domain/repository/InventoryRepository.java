package com.flashsale.productdomain.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.productdomain.domain.model.Inventory;
import java.util.Optional;

@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {
    Optional<Inventory> findBySkuCode(String skuCode);
}

