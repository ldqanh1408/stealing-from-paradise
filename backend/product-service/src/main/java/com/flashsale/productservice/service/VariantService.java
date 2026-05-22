package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.Product;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.model.ProductVariant.VariantStatus;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.dto.request.CreateVariantRequest;
import com.flashsale.productservice.dto.request.UpdateVariantRequest;
import com.flashsale.productservice.dto.response.VariantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final KafkaProducerService kafkaProducerService;

    /**
     * List all variants for a product, active variants first, with stock info.
     */
    public List<VariantResponse> listVariants(String productId, Long sellerId) {
        validateProductOwnership(productId, sellerId);

        Map<String, Integer> stockByCode = inventoryRepository.findByProductId(productId).stream()
                .collect(HashMap::new,
                        (m, inv) -> m.put(inv.getVariantCode() != null ? inv.getVariantCode() : inv.getSkuCode(), inv.getStockAvailable()),
                        HashMap::putAll);

        List<ProductVariant> variants = variantRepository.findByProductId(productId);

        return variants.stream()
                .sorted((a, b) -> {
                    boolean aActive = VariantStatus.ACTIVE.name().equals(a.getStatus());
                    boolean bActive = VariantStatus.ACTIVE.name().equals(b.getStatus());
                    if (aActive && !bActive) return -1;
                    if (!aActive && bActive) return 1;
                    return 0;
                })
                .map(v -> VariantResponse.from(v, stockByCode.getOrDefault(v.getVariantCode(), 0)))
                .toList();
    }

    /**
     * Create a new variant for a product.
     * - Auto-creates inventory entry with zero stock
     * - Default status = ACTIVE, version = 1
     * - variantAttributes from request
     */
    public VariantResponse createVariant(String productId, Long sellerId, CreateVariantRequest req) {
        validateProductOwnership(productId, sellerId);

        if (variantRepository.existsByVariantCode(req.getVariantCode())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Variant code đã tồn tại: " + req.getVariantCode());
        }

        ProductVariant variant = ProductVariant.builder()
                .productId(productId)
                .variantCode(req.getVariantCode())
                .variantName(req.getVariantName())
                .price(req.getPrice())
                .originalPrice(req.getOriginalPrice())
                .variantAttributes(req.getVariantAttributes())
                .imageUrl(req.getImageUrl())
                .stockQuantity(0)
                .status(VariantStatus.ACTIVE.name())
                .version(1)
                .build();

        variant = variantRepository.save(variant);

        if (!inventoryRepository.existsByVariantCode(req.getVariantCode()) && !inventoryRepository.existsBySkuCode(req.getVariantCode())) {
            Inventory inventory = Inventory.builder()
                    .skuCode(req.getVariantCode())
                    .productId(productId)
                    .stockTotal(0)
                    .stockLocked(0)
                    .stockAvailable(0)
                    .stockFlashReserved(0)
                    .build();
            inventoryRepository.save(inventory);
        }

        log.info("Created variant: productId={}, variantCode={}", productId, req.getVariantCode());
        return VariantResponse.from(variant, 0);
    }

    /**
     * Update an existing variant.
     * - Supports variantName, price, originalPrice, variantAttributes, imageUrl
     * - Emits variant.price_updated Kafka event if price changed
     * - Returns VariantResponse with stockQuantity from inventory
     */
    public VariantResponse updateVariant(String variantId, Long sellerId, UpdateVariantRequest req) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant không tồn tại"));

        validateProductOwnership(variant.getProductId(), sellerId);

        BigDecimal oldPrice = variant.getPrice();
        boolean priceChanged = false;

        if (req.getVariantName() != null)          variant.setVariantName(req.getVariantName());
        if (req.getPrice() != null) {
            variant.setPrice(req.getPrice());
            priceChanged = true;
        }
        if (req.getOriginalPrice() != null)        variant.setOriginalPrice(req.getOriginalPrice());
        if (req.getVariantAttributes() != null)   variant.setVariantAttributes(req.getVariantAttributes());
        if (req.getImageUrl() != null)             variant.setImageUrl(req.getImageUrl());

        variant = variantRepository.save(variant);

        if (priceChanged && oldPrice != null && oldPrice.compareTo(variant.getPrice()) != 0) {
            emitPriceUpdatedEvent(variant);
        }

        final String variantCode = variant.getVariantCode();
        Integer stock = inventoryRepository.findByVariantCode(variantCode)
                .or(() -> inventoryRepository.findBySkuCode(variantCode))
                .map(Inventory::getStockAvailable).orElse(0);

        log.info("Updated variant: variantId={}", variantId);
        return VariantResponse.from(variant, stock);
    }

    /**
     * Delete a variant.
     * - Blocks if stockLocked > 0
     * - Blocks if variant status != INACTIVE
     */
    public void deleteVariant(String variantId, Long sellerId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant không tồn tại"));

        validateProductOwnership(variant.getProductId(), sellerId);

        if (!VariantStatus.INACTIVE.name().equals(variant.getStatus())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Chỉ có thể xóa variant ở trạng thái INACTIVE");
        }

        inventoryRepository.findByVariantCode(variant.getVariantCode())
                .or(() -> inventoryRepository.findBySkuCode(variant.getVariantCode()))
                .ifPresent(inv -> {
            if (inv.getStockLocked() != null && inv.getStockLocked() > 0) {
                throw new AppException(ErrorCode.ALREADY_EXISTS,
                        "Variant đang có stock_locked > 0, không thể xóa");
            }
        });

        variantRepository.deleteById(variantId);
        log.info("Deleted variant: variantId={}", variantId);
    }

    /**
     * Update stock quantity for a variant and auto-set status.
     * - If newStock <= 0: set status to OUT_OF_STOCK
     * - Otherwise: set status to ACTIVE
     * - Emits variant.stock_updated Kafka event
     */
    public VariantResponse updateVariantStock(String variantCode, int newStock) {
        ProductVariant variant = variantRepository.findByVariantCode(variantCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Variant không tồn tại: " + variantCode));

        variant.setStockQuantity(newStock);

        if (newStock <= 0) {
            variant.setStatus(VariantStatus.OUT_OF_STOCK.name());
        } else {
            variant.setStatus(VariantStatus.ACTIVE.name());
        }

        variant = variantRepository.save(variant);

        emitStockUpdatedEvent(variant, newStock);

        Integer stockAvailable = inventoryRepository.findByVariantCode(variantCode)
                .or(() -> inventoryRepository.findBySkuCode(variantCode))
                .map(Inventory::getStockAvailable).orElse(0);

        log.info("Updated stock for variant: variantCode={}, newStock={}", variantCode, newStock);
        return VariantResponse.from(variant, stockAvailable);
    }

    /**
     * Set variant status explicitly (admin/seller action).
     * Valid transitions depend on business rules.
     */
    public VariantResponse setVariantStatus(String variantId, String status) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant không tồn tại"));

        try {
            VariantStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Trạng thái không hợp lệ: " + status);
        }

        variant.setStatus(status);
        variant = variantRepository.save(variant);

        final String vc = variant.getVariantCode();
        Integer stock = inventoryRepository.findByVariantCode(vc)
                .or(() -> inventoryRepository.findBySkuCode(vc))
                .map(Inventory::getStockAvailable).orElse(0);

        log.info("Set status for variant: variantId={}, status={}", variantId, status);
        return VariantResponse.from(variant, stock);
    }

    // ─── Kafka Event Emitters ─────────────────────────────────────────────────

    private void emitPriceUpdatedEvent(ProductVariant variant) {
        Map<String, Object> event = new HashMap<>();
        event.put("variantId", variant.getId());
        event.put("variantCode", variant.getVariantCode());
        event.put("productId", variant.getProductId());
        event.put("oldPrice", null);
        event.put("newPrice", variant.getPrice());
        event.put("timestamp", java.time.Instant.now().toString());

        kafkaProducerService.publish(KafkaTopics.PRODUCT_UPDATED, event);
        log.debug("Emitted variant.price_updated event: variantId={}", variant.getId());
    }

    private void emitStockUpdatedEvent(ProductVariant variant, int newStock) {
        Map<String, Object> event = new HashMap<>();
        event.put("variantId", variant.getId());
        event.put("variantCode", variant.getVariantCode());
        event.put("productId", variant.getProductId());
        event.put("stockQuantity", newStock);
        event.put("status", variant.getStatus());
        event.put("timestamp", java.time.Instant.now().toString());

        kafkaProducerService.publish(KafkaTopics.INVENTORY_ADJUSTED, event);
        log.debug("Emitted variant.stock_updated event: variantId={}, stock={}", variant.getId(), newStock);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Product validateProductOwnership(String productId, Long sellerId) {
        return productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Sản phẩm không tồn tại hoặc không thuộc seller"));
    }
}
