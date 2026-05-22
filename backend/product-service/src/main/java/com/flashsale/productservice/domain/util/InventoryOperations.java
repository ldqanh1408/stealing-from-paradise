package com.flashsale.productservice.domain.util;

import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Decrement available stock khi user add to cart
     * Increment locked stock
     */
    public boolean lockStock(String skuCode, int quantity) {
        try {
            Query query = Query.query(
                Criteria.where("skuCode").is(skuCode)
                    .and("stockAvailable").gte(quantity)
            );

            Update update = new Update()
                .inc("stockAvailable", -quantity)
                .inc("stockLocked", quantity);

            var result = mongoTemplate.updateFirst(query, update, Inventory.class);

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
            Query query = Query.query(Criteria.where("skuCode").is(skuCode));

            Update update = new Update()
                .inc("stockAvailable", quantity)
                .inc("stockLocked", -quantity);

            mongoTemplate.updateFirst(query, update, Inventory.class);

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
                Criteria.where("skuCode").is(skuCode)
                    .and("stockAvailable").gte(quantity)
            );

            Update update = new Update()
                .inc("stockAvailable", -quantity)
                .inc("stockFlashReserved", quantity);

            var result = mongoTemplate.updateFirst(query, update, Inventory.class);

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
            Query query = Query.query(Criteria.where("skuCode").is(skuCode));

            Update update = new Update()
                .inc("stockAvailable", unsoldQuantity)
                .inc("stockFlashReserved", -unsoldQuantity);

            mongoTemplate.updateFirst(query, update, Inventory.class);

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
                Criteria.where("skuCode").is(skuCode)
                    .and("stockLocked").gte(quantity)
            );

            Update update = new Update()
                .inc("stockLocked", -quantity)
                .inc("stockTotal", -quantity);  // Reduce total

            var result = mongoTemplate.updateFirst(query, update, Inventory.class);

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
                Criteria.where("skuCode").is(skuCode)
                    .and("stockAvailable").gte(quantity)
            );

            var inventory = mongoTemplate.findOne(query, Inventory.class);
            return inventory != null;
        } catch (Exception e) {
            log.error("Error checking stock availability for SKU: {}", skuCode, e);
            return false;
        }
    }

    /**
     * Release all expired reservations.
     * Finds pending reservations where expiresAt < NOW() and releases them
     * by restoring stock and marking the reservation as RELEASED.
     * Used by scheduled cleanup job.
     */
    public int releaseExpiredReservations() {
        try {
            LocalDateTime now = LocalDateTime.now();
            Query query = Query.query(
                Criteria.where("status").is(StockReservation.ReservationStatus.PENDING.name())
                    .and("expiresAt").lt(now)
            );

            List<StockReservation> expired = mongoTemplate.find(query, StockReservation.class);
            int releasedCount = 0;

            for (StockReservation reservation : expired) {
                int qty = reservation.getQuantity() != null ? reservation.getQuantity() : 0;
                if (qty > 0) {
                    boolean unlocked = unlockStock(reservation.getSkuCode(), qty);
                    if (unlocked) {
                        Query releaseQuery = Query.query(Criteria.where("id").is(reservation.getId()));
                        Update releaseUpdate = new Update()
                            .set("status", StockReservation.ReservationStatus.RELEASED.name())
                            .set("updatedAt", LocalDateTime.now());
                        mongoTemplate.updateFirst(releaseQuery, releaseUpdate, StockReservation.class);

                        releasedCount++;
                        log.info("Released expired reservation for SKU: {} quantity: {}",
                            reservation.getSkuCode(), qty);
                    }
                }
            }

            log.info("Released {} expired reservations", releasedCount);
            return releasedCount;
        } catch (Exception e) {
            log.error("Error releasing expired reservations", e);
            return 0;
        }
    }
}
