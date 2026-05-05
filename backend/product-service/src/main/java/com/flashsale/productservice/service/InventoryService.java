package com.flashsale.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import com.flashsale.productservice.domain.model.Inventory;
import com.mongodb.client.result.UpdateResult;

/**
 * ⚠️ MG_INVENTORIES không có trường version — mọi thao tác tăng/giảm stock PHẢI dùng MongoTemplate
 * với $inc atomic thay vì repository.save() để tránh Lost Update khi nhiều thread ghi đồng thời.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final MongoTemplate mongo;

    public boolean decreaseStock(String skuCode, int qty) {
        Query q = Query.query(
            Criteria.where("skuCode").is(skuCode)
                    .and("stockAvailable").gte(qty)   // guard: không âm
        );
        Update u = new Update()
            .inc("stockAvailable", -qty)
            .inc("stockLocked",    +qty);
        UpdateResult result = mongo.updateFirst(q, u, Inventory.class);
        return result.getModifiedCount() > 0;
    }
}

