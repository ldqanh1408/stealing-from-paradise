package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Cart;
import com.flashsale.productservice.domain.model.CartItem;
import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.Product;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.model.ProductVariant.VariantStatus;
import com.flashsale.productservice.domain.repository.CartItemRepository;
import com.flashsale.productservice.domain.repository.CartRepository;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.dto.response.CartResponse;
import com.flashsale.productservice.dto.response.CartResponse.CartItemResponse;
import com.flashsale.productservice.dto.response.CartResponse.CartSellerGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cart Service — consolidated from standalone cart-service
 * SINGLE source of truth for all cart operations.
 * Handles shopping cart operations and Kafka event consumers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════
    // PUBLIC CART OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Get cart for a user with real-time variant data.
     * Sets priceChanged=true if unitPrice != currentPrice.
     * Sets stockStatus: "available" | "out_of_stock" | "insufficient".
     */
    public CartResponse getCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return emptyCartResponse(userId);
        }

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        return buildCartResponse(userId, items);
    }

    /**
     * Add item to cart (UPSERT logic).
     * - If variant already in cart, increment quantity
     * - Snapshot: price, variantName, variantImage
     * - Emit cart.item_added Kafka event
     */
    public CartResponse addItemToCart(Long userId, String skuCode, Integer quantity, Long fsItemId) {
        validateAddItem(skuCode, quantity);

        Cart cart = getOrCreateCart(userId);

        CartItem existing = cartItemRepository.findByCartIdAndSkuCode(cart.getId(), skuCode).orElse(null);
        if (existing != null) {
            int newQty = existing.getQuantity() + quantity;
            validateStock(skuCode, newQty);
            existing.setQuantity(newQty);
            updatePriceSnapshotIfChanged(existing);
            cartItemRepository.save(existing);
            log.info("Incremented cart item: userId={}, skuCode={}, newQty={}", userId, skuCode, newQty);
        } else {
            CartItem item = buildCartItem(userId, cart.getId(), skuCode, quantity, fsItemId);
            cartItemRepository.save(item);
            log.info("Added new cart item: userId={}, skuCode={}, qty={}", userId, skuCode, quantity);
        }

        updateCartTotalItems(cart.getId());
        emitCartItemAddedEvent(userId, skuCode, quantity);

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        return buildCartResponse(userId, items);
    }

    /**
     * Update quantity of a cart item.
     * - If quantity=0: remove item
     * - Validate ownership
     * - Validate stock
     * - Update priceSnapshot if price changed
     */
    public CartResponse updateCartItem(Long userId, String cartItemId, Integer quantity) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item không tồn tại trong giỏ hàng"));

        validateCartItemOwnership(userId, item);

        if (quantity == null || quantity <= 0) {
            cartItemRepository.deleteById(cartItemId);
            updateCartTotalItems(item.getCartId());
            log.info("Removed cart item: itemId={}", cartItemId);
            Cart cart = cartRepository.findByUserId(userId).orElse(null);
            return cart != null
                    ? buildCartResponse(userId, cartItemRepository.findByCartId(cart.getId()))
                    : emptyCartResponse(userId);
        }

        validateStock(item.getSkuCode(), quantity);
        item.setQuantity(quantity);
        updatePriceSnapshotIfChanged(item);
        cartItemRepository.save(item);
        updateCartTotalItems(item.getCartId());

        log.info("Updated cart item: itemId={}, newQty={}", cartItemId, quantity);
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        return cart != null
                ? buildCartResponse(userId, cartItemRepository.findByCartId(cart.getId()))
                : emptyCartResponse(userId);
    }

    /**
     * Remove a single item from cart.
     * - Validate ownership
     * - Delete item
     * - Return updated CartResponse
     */
    public CartResponse removeCartItem(Long userId, String cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item không tồn tại trong giỏ hàng"));

        validateCartItemOwnership(userId, item);

        String cartId = item.getCartId();
        cartItemRepository.deleteById(cartItemId);
        updateCartTotalItems(cartId);

        log.info("Removed cart item: itemId={}", cartItemId);
        return buildCartResponse(userId, cartItemRepository.findByCartId(cartId));
    }

    /**
     * Clear all items in cart (keep cart).
     * - Delete all items for user
     * - Return empty CartResponse
     */
    public CartResponse clearCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return emptyCartResponse(userId);
        }

        cartItemRepository.deleteByCartId(cart.getId());
        cart.setTotalItems(0);
        cartRepository.save(cart);

        log.info("Cleared cart for user: {}", userId);
        return emptyCartResponse(userId);
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Get existing cart or create a new one for user.
     */
    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .totalItems(0)
                    .build();
            return cartRepository.save(newCart);
        });
    }

    /**
     * Build a CartItem entity from request params.
     * Fetches variant data for snapshots.
     */
    private CartItem buildCartItem(Long userId, String cartId, String skuCode, Integer quantity, Long fsItemId) {
        ProductVariant variant = productVariantRepository.findByVariantCode(skuCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Variant không tồn tại: " + skuCode));

        return CartItem.builder()
                .cartId(cartId)
                .userId(userId)
                .variantId(variant.getId())
                .skuCode(skuCode)
                .fsItemId(fsItemId)
                .priceSnapshot(variant.getPrice())
                .variantNameSnapshot(variant.getVariantName())
                .variantImageSnapshot(variant.getImageUrl())
                .quantity(quantity)
                .addedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build full CartResponse with:
     * - Grouped by sellerId
     * - Price changed detection
     * - Stock status
     * - Subtotals
     * - Warnings
     */
    private CartResponse buildCartResponse(Long userId, List<CartItem> items) {
        if (items.isEmpty()) {
            return emptyCartResponse(userId);
        }

        List<String> variantCodes = items.stream()
                .map(CartItem::getSkuCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, ProductVariant> variantByCode = variantCodes.isEmpty()
                ? Map.of()
                : productVariantRepository.findByVariantCodeIn(variantCodes).stream()
                        .collect(Collectors.toMap(ProductVariant::getVariantCode, v -> v));

        List<String> productIds = variantByCode.values().stream()
                .map(ProductVariant::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, Product> productById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        Map<String, Inventory> inventoryBySku = variantCodes.isEmpty()
                ? Map.of()
                : inventoryRepository.findAll().stream()
                        .filter(inv -> variantCodes.contains(inv.getSkuCode()))
                        .collect(Collectors.toMap(Inventory::getSkuCode, inv -> inv));

        Map<Long, List<CartItem>> itemsBySeller = items.stream()
                .collect(Collectors.groupingBy(item -> {
                    ProductVariant v = variantByCode.get(item.getSkuCode());
                    if (v != null) {
                        Product p = productById.get(v.getProductId());
                        if (p != null) return p.getSellerId();
                    }
                    return 0L;
                }));

        List<CartSellerGroup> sellerGroups = new ArrayList<>();
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        boolean hasWarning = false;
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<Long, List<CartItem>> entry : itemsBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<CartItem> sellerItems = entry.getValue();

            List<CartItemResponse> itemResponses = new ArrayList<>();
            BigDecimal sellerSubtotal = BigDecimal.ZERO;

            for (CartItem item : sellerItems) {
                ProductVariant variant = variantByCode.get(item.getSkuCode());
                Product product = variant != null ? productById.get(variant.getProductId()) : null;
                Inventory inventory = inventoryBySku.get(item.getSkuCode());

                BigDecimal currentPrice = variant != null ? variant.getPrice() : item.getPriceSnapshot();
                boolean priceChanged = variant != null
                        && variant.getPrice().compareTo(item.getPriceSnapshot()) != 0;

                int stockAvailable = inventory != null && inventory.getStockAvailable() != null
                        ? inventory.getStockAvailable() : 0;
                String stockStatus = computeStockStatus(item.getQuantity(), stockAvailable, variant);

                BigDecimal unitPrice = item.getPriceSnapshot();
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

                CartItemResponse itemResp = CartItemResponse.builder()
                        .cartItemId(item.getId())
                        .skuCode(item.getSkuCode())
                        .productId(variant != null ? variant.getProductId() : null)
                        .productName(product != null ? product.getName() : null)
                        .variantId(item.getVariantId())
                        .variantName(item.getVariantNameSnapshot())
                        .variantImage(item.getVariantImageSnapshot())
                        .unitPrice(unitPrice)
                        .currentPrice(currentPrice)
                        .priceChanged(priceChanged)
                        .quantity(item.getQuantity())
                        .stockAvailable(stockAvailable)
                        .stockStatus(stockStatus)
                        .isFlashSale(product != null ? Boolean.TRUE.equals(product.getIsFlashSale()) : false)
                        .fsItemId(item.getFsItemId())
                        .subtotal(subtotal)
                        .addedAt(item.getAddedAt())
                        .build();

                itemResponses.add(itemResp);
                sellerSubtotal = sellerSubtotal.add(subtotal);
                totalSubtotal = totalSubtotal.add(subtotal);

                if (priceChanged) {
                    hasWarning = true;
                    warnings.add("Giá của '" + item.getVariantNameSnapshot() + "' đã thay đổi");
                }
                if ("out_of_stock".equals(stockStatus) || "insufficient".equals(stockStatus)) {
                    hasWarning = true;
                    warnings.add("'" + item.getVariantNameSnapshot() + "' " +
                            ("out_of_stock".equals(stockStatus) ? "đã hết hàng" : "không đủ số lượng"));
                }
            }

            Product sellerProduct = sellerItems.stream()
                    .map(item -> variantByCode.get(item.getSkuCode()))
                    .filter(Objects::nonNull)
                    .map(v -> productById.get(v.getProductId()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            CartSellerGroup group = CartSellerGroup.builder()
                    .sellerId(sellerId)
                    .sellerName(sellerProduct != null ? "Seller " + sellerId : null)
                    .items(itemResponses)
                    .sellerSubtotal(sellerSubtotal)
                    .build();

            sellerGroups.add(group);
        }

        int totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();

        return CartResponse.builder()
                .cartId(items.get(0).getCartId())
                .sellers(sellerGroups)
                .totalItems(totalItems)
                .subtotal(totalSubtotal)
                .hasWarning(hasWarning)
                .warnings(warnings.isEmpty() ? null : warnings)
                .build();
    }

    /**
     * Compute stock status for a cart item.
     */
    private String computeStockStatus(Integer requestedQty, int stockAvailable, ProductVariant variant) {
        if (variant == null) {
            return "out_of_stock";
        }
        String status = variant.getStatus();
        if (VariantStatus.INACTIVE.name().equals(status)) {
            return "out_of_stock";
        }
        if (stockAvailable <= 0) {
            return "out_of_stock";
        }
        if (requestedQty > stockAvailable) {
            return "insufficient";
        }
        return "available";
    }

    /**
     * Update price snapshot if current price differs from snapshot.
     */
    private void updatePriceSnapshotIfChanged(CartItem item) {
        productVariantRepository.findByVariantCode(item.getSkuCode()).ifPresent(variant -> {
            if (variant.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                item.setPriceSnapshot(variant.getPrice());
                item.setVariantNameSnapshot(variant.getVariantName());
                item.setVariantImageSnapshot(variant.getImageUrl());
            }
        });
    }

    /**
     * Validate item can be added to cart.
     */
    private void validateAddItem(String skuCode, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng phải lớn hơn 0");
        }
    }

    /**
     * Validate stock availability for a SKU and quantity.
     */
    private void validateStock(String skuCode, Integer quantity) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode).orElse(null);
        if (inventory == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy inventory cho SKU: " + skuCode);
        }

        if (inventory.getStockAvailable() == null || inventory.getStockAvailable() < quantity) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không đủ hàng: chỉ còn " + inventory.getStockAvailable() + " sản phẩm");
        }

        ProductVariant variant = productVariantRepository.findByVariantCode(skuCode).orElse(null);
        if (variant != null && VariantStatus.INACTIVE.name().equals(variant.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Sản phẩm đã ngừng bán");
        }
    }

    /**
     * Validate cart item belongs to the user.
     */
    private void validateCartItemOwnership(Long userId, CartItem item) {
        if (!userId.equals(item.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Item không thuộc giỏ hàng của bạn");
        }
    }

    /**
     * Update denormalized totalItems on cart.
     */
    private void updateCartTotalItems(String cartId) {
        Cart cart = cartRepository.findById(cartId).orElse(null);
        if (cart != null) {
            long count = cartItemRepository.countByCartId(cartId);
            cart.setTotalItems((int) count);
            cartRepository.save(cart);
        }
    }

    /**
     * Build an empty cart response.
     */
    private CartResponse emptyCartResponse(Long userId) {
        return CartResponse.builder()
                .cartId(null)
                .sellers(List.of())
                .totalItems(0)
                .subtotal(BigDecimal.ZERO)
                .hasWarning(false)
                .warnings(null)
                .build();
    }

    /**
     * Emit cart.item_added Kafka event.
     */
    private void emitCartItemAddedEvent(Long userId, String skuCode, Integer quantity) {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("skuCode", skuCode);
        event.put("quantity", quantity);
        event.put("timestamp", java.time.Instant.now().toString());

        kafkaTemplate.send(KafkaTopics.CART_PRODUCT_INFO_REQUEST, toJson(event));
        log.debug("Emitted cart.item_added event: userId={}, skuCode={}", userId, skuCode);
    }

    // ══════════════════════════════════════════════════════════════
    // KAFKA LISTENERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Respond to ORDER_CART_ITEMS_REQUEST from order-service.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CART_ITEMS_REQUEST,
            groupId = "product-service-cart-reply"
    )
    public void onCartItemsRequest(String message) {
        try {
            Map<String, Object> request = objectMapper.readValue(message, new TypeReference<>() {});
            Object correlationIdObj = request.get("correlation_id");
            Object userIdObj = request.get("user_id");
            Object itemIdsObj = request.get("item_ids");

            if (correlationIdObj == null || userIdObj == null || itemIdsObj == null) {
                log.warn("Invalid cart items request: missing required fields");
                if (correlationIdObj != null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("correlation_id", correlationIdObj.toString());
                    errorResponse.put("error", true);
                    kafkaTemplate.send(KafkaTopics.ORDER_CART_ITEMS_RESPONSE,
                            correlationIdObj.toString(), toJson(errorResponse));
                }
                return;
            }

            String correlationId = correlationIdObj.toString();
            Long userId = ((Number) userIdObj).longValue();
            List<String> itemIds = objectMapper.convertValue(itemIdsObj, new TypeReference<>() {});

            List<CartItem> items = cartItemRepository.findByUserId(userId).stream()
                    .filter(item -> itemIds.contains(item.getId()))
                    .toList();

            List<String> variantCodes = items.stream()
                    .map(CartItem::getSkuCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, ProductVariant> variantByCode = variantCodes.isEmpty()
                    ? Map.of()
                    : productVariantRepository.findByVariantCodeIn(variantCodes).stream()
                            .collect(Collectors.toMap(ProductVariant::getVariantCode, v -> v));

            List<String> productIds = variantByCode.values().stream()
                    .map(ProductVariant::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, Product> productById = productIds.isEmpty()
                    ? Map.of()
                    : productRepository.findAllById(productIds).stream()
                            .collect(Collectors.toMap(Product::getId, p -> p));

            List<Map<String, Object>> enrichedItems = new ArrayList<>();

            for (CartItem item : items) {
                Map<String, Object> enriched = new HashMap<>();
                enriched.put("cartItemId", item.getId());
                enriched.put("skuCode", item.getSkuCode());
                enriched.put("variantId", item.getVariantId());
                enriched.put("priceSnapshot", item.getPriceSnapshot());
                enriched.put("quantity", item.getQuantity());
                enriched.put("fsItemId", item.getFsItemId());

                ProductVariant variant = variantByCode.get(item.getSkuCode());
                if (variant != null) {
                    enriched.put("variantName", variant.getVariantName());
                    enriched.put("productId", variant.getProductId());

                    Product product = productById.get(variant.getProductId());
                    if (product != null) {
                        enriched.put("productName", product.getName());
                        enriched.put("sellerId", product.getSellerId());
                        if (product.getImages() != null && !product.getImages().isEmpty()) {
                            enriched.put("imageUrl", product.getImages().get(0));
                        }
                    }
                }

                enrichedItems.add(enriched);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id", correlationId);
            response.put("items", enrichedItems);
            response.put("error", false);

            kafkaTemplate.send(KafkaTopics.ORDER_CART_ITEMS_RESPONSE, correlationId, toJson(response));
            log.debug("Cart items response sent: correlationId={}, itemCount={}", correlationId, enrichedItems.size());

        } catch (Exception e) {
            log.error("Failed to process cart items request: {}", e.getMessage(), e);
            try {
                Map<String, Object> request = objectMapper.readValue(message, new TypeReference<>() {});
                Object correlationIdObj = request.get("correlation_id");
                if (correlationIdObj != null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("correlation_id", correlationIdObj.toString());
                    errorResponse.put("error", true);
                    kafkaTemplate.send(KafkaTopics.ORDER_CART_ITEMS_RESPONSE,
                            correlationIdObj.toString(), toJson(errorResponse));
                }
            } catch (Exception ex) {
                log.error("Failed to send error response for cart items request", ex);
            }
        }
    }

    /**
     * Remove multiple cart items by IDs (used by KafkaConsumerService after checkout).
     */
    public void removeCartItemsByIds(Long userId, List<String> itemIds) {
        List<CartItem> itemsToDelete = cartItemRepository.findAllById(itemIds).stream()
                .filter(item -> userId.equals(item.getUserId()))
                .toList();
        
        cartItemRepository.deleteAllById(itemsToDelete.stream().map(CartItem::getId).toList());

        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart != null) {
            updateCartTotalItems(cart.getId());
        }

        log.info("Removed cart items by IDs: userId={}, count={}", userId, itemsToDelete.size());
    }

    /**
     * Handle INVENTORY_ADJUSTED event.
     * Mark items as needing revalidation (price/stock may have changed).
     * Items will be revalidated on next getCart call.
     */
    @KafkaListener(
            topics = KafkaTopics.INVENTORY_ADJUSTED,
            groupId = "product-service-cart-inventory"
    )
    public void onInventoryAdjusted(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Object skuCodeObj = payload.get("skuCode");

            if (skuCodeObj == null) {
                log.warn("Invalid inventory adjusted event: missing skuCode");
                return;
            }

            String skuCode = skuCodeObj.toString();

            List<CartItem> affectedItems = cartItemRepository.findAll().stream()
                    .filter(item -> skuCode.equals(item.getSkuCode()))
                    .toList();

            log.info("Inventory adjusted for SKU: {}, affected cart items: {}",
                    skuCode, affectedItems.size());

        } catch (Exception e) {
            log.error("Failed to handle inventory adjusted event: {}", e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload: {}", e.getMessage());
            return "{}";
        }
    }
}
