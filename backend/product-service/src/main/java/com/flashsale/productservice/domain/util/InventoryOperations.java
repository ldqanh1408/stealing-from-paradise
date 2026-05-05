package com.flashsale.productservice.domain.util;

import com.flashsale.productservice.domain.model.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Atomic Inventory Operations
 *
 * Sử dụng MongoDB $inc operator để đảm bảo atomic updates
 * và tránh Lost Update problem khi nhiều thread cùng modify stock
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryOperations {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION = "inventories";

    /**
     * Decrement available stock khi user add to cart
     * Increment locked stock
     */
    public boolean lockStock(String skuCode, int quantity) {
        try {
            Query query = Query.query(
                Criteria.where("sku_code").is(skuCode)
                    .and("stock_available").gte(quantity)
            );

            Update update = new Update()
                .inc("stock_available", -quantity)
                .inc("stock_locked", quantity);

            var result = mongoTemplate.updateFirst(query, update, COLLECTION);

            if (result.getModifiedCount() == 0) {
                log.warn("Failed to lock stock for SKU: {} quantity: {}", skuCode, quantity);
                return false;
            }

            log.debug("Locked stock for SKU: {} quantity: {}", skuCode, quantity);
            return true;
        } catch (Exception e) {
            log.error("Error locking stock for SKU: {}", skuCode, e);
            return false;
        }
    }

    /**
     * Unlock stock khi user remove from cart
     */
    public boolean unlockStock(String skuCode, int quantity) {
        try {
            Query query = Query.query(Criteria.where("sku_code").is(skuCode));

            Update update = new Update()
                .inc("stock_available", quantity)
                .inc("stock_locked", -quantity);

            mongoTemplate.updateFirst(query, update, COLLECTION);

            log.debug("Unlocked stock for SKU: {} quantity: {}", skuCode, quantity);
            return true;
        } catch (Exception e) {
            log.error("Error unlocking stock for SKU: {}", skuCode, e);
            return false;
        }
    }

    /**
     * Reserve stock cho Flash Sale
     * Gọi khi Admin APPROVE FS_ITEM
     */
    public boolean reserveFlashStock(String skuCode, int quantity) {
        try {
            Query query = Query.query(
                Criteria.where("sku_code").is(skuCode)
                    .and("stock_available").gte(quantity)
            );

            Update update = new Update()
                .inc("stock_available", -quantity)
                .inc("stock_flash_reserved", quantity);

            var result = mongoTemplate.updateFirst(query, update, COLLECTION);

            if (result.getModifiedCount() == 0) {
                log.warn("Failed to reserve flash stock for SKU: {} quantity: {}", skuCode, quantity);
                return false;
            }

            log.debug("Reserved flash stock for SKU: {} quantity: {}", skuCode, quantity);
            return true;
        } catch (Exception e) {
            log.error("Error reserving flash stock for SKU: {}", skuCode, e);
            return false;
        }
    }

    /**
     * Release unused flash sale stock
     * Gọi khi flash sale session ENDED
     */
    public boolean releaseFlashStock(String skuCode, int unsoldQuantity) {
        try {
            Query query = Query.query(Criteria.where("sku_code").is(skuCode));

            Update update = new Update()
                .inc("stock_available", unsoldQuantity)
                .inc("stock_flash_reserved", -unsoldQuantity);

            mongoTemplate.updateFirst(query, update, COLLECTION);

            log.debug("Released unused flash stock for SKU: {} quantity: {}", skuCode, unsoldQuantity);
            return true;
        } catch (Exception e) {
            log.error("Error releasing flash stock for SKU: {}", skuCode, e);
            return false;
        }
    }

    /**
     * Consume locked stock sau khi order SUCCESS
     * Move từ locked → consumed (decrease both)
     */
    public boolean consumeLockedStock(String skuCode, int quantity) {
        try {
            Query query = Query.query(
                Criteria.where("sku_code").is(skuCode)
                    .and("stock_locked").gte(quantity)
            );

            Update update = new Update()
                .inc("stock_locked", -quantity)
                .inc("stock_total", -quantity);  // Reduce total

            var result = mongoTemplate.updateFirst(query, update, COLLECTION);

            if (result.getModifiedCount() == 0) {
                log.warn("Failed to consume locked stock for SKU: {} quantity: {}", skuCode, quantity);
                return false;
            }

            log.debug("Consumed locked stock for SKU: {} quantity: {}", skuCode, quantity);
            return true;
        } catch (Exception e) {
            log.error("Error consuming locked stock for SKU: {}", skuCode, e);
            return false;
        }
    }

    /**
     * Check stock availability trước khi lock
     */
    public boolean isStockAvailable(String skuCode, int quantity) {
        try {
            Query query = Query.query(
                Criteria.where("sku_code").is(skuCode)
                    .and("stock_available").gte(quantity)
            );

            var inventory = mongoTemplate.findOne(query, Inventory.class, COLLECTION);
            return inventory != null;
        } catch (Exception e) {
            log.error("Error checking stock availability for SKU: {}", skuCode, e);
            return false;
        }
    }
}

