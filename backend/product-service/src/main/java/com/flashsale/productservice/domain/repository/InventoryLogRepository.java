package com.flashsale.productservice.domain.repository;

import com.flashsale.productservice.domain.model.InventoryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryLogRepository extends MongoRepository<InventoryLog, String> {

    Page<InventoryLog> findBySkuCodeOrderByTimestampDesc(String skuCode, Pageable pageable);
}
