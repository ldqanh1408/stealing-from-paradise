package com.flashsale.productservice.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.productservice.domain.model.Inventory;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {

    Optional<Inventory> findBySkuCode(String skuCode);

    List<Inventory> findByProductId(String productId);

    boolean existsBySkuCode(String skuCode);
}
