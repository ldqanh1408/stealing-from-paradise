package com.flashsale.productdomain.service;

import com.flashsale.productdomain.config.RedisKeys;
import com.flashsale.productdomain.domain.model.Cart;
import com.flashsale.productdomain.domain.model.CartItem;
import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.ProductStatus;
import com.flashsale.productdomain.domain.model.ReservationStatus;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.model.SkuStatus;
import com.flashsale.productdomain.domain.model.StockReservation;
import com.flashsale.productdomain.domain.repository.CartItemRepository;
import com.flashsale.productdomain.domain.repository.CartRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import com.flashsale.productdomain.domain.repository.StockReservationRepository;
import com.flashsale.productdomain.dto.request.CheckoutPreviewRequest;
import com.flashsale.productdomain.dto.request.PlaceOrderRequest;
import com.flashsale.productdomain.dto.response.CheckoutPreviewItemResponse;
import com.flashsale.productdomain.dto.response.CheckoutPreviewResponse;
import com.flashsale.productdomain.exception.BusinessRuleException;
import com.flashsale.productdomain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StringRedisTemplate redisTemplate;

    public CheckoutPreviewResponse checkoutPreview(Long customerId, CheckoutPreviewRequest request) {
        String previewKey = RedisKeys.checkoutPreviewKey(customerId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(previewKey))) {
            throw new BusinessRuleException("Preview session is already in use");
        }

        List<CartItem> cartItems = cartItemRepository.findAllById(request.getCartItemIds());
        if (cartItems.isEmpty() || cartItems.size() != request.getCartItemIds().size()) {
            throw new ResourceNotFoundException("Some cart items not found");
        }

        List<UUID> skuIds = cartItems.stream().map(item -> item.getSku().getId()).toList();
        Map<UUID, Sku> skuMap = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s));

        List<CheckoutPreviewItemResponse> errorItems = new ArrayList<>();

        for (CartItem item : cartItems) {
            Sku sku = skuMap.get(item.getSku().getId());
            if (sku == null) {
                errorItems.add(new CheckoutPreviewItemResponse(item.getSku().getId(), "NOT_FOUND", "SKU không tồn tại"));
                continue;
            }

            if (sku.getStatus() != SkuStatus.ACTIVE || sku.getProduct().getStatus() != ProductStatus.ACTIVE) {
                errorItems.add(new CheckoutPreviewItemResponse(sku.getId(), "UNAVAILABLE", "Sản phẩm ngừng bán"));
            } else if (sku.getStockQuantity() == 0) {
                errorItems.add(new CheckoutPreviewItemResponse(sku.getId(), "OUT_OF_STOCK", "Sản phẩm đã hết hàng"));
            } else if (sku.getStockQuantity() < item.getQuantity()) {
                errorItems.add(new CheckoutPreviewItemResponse(sku.getId(), "INSUFFICIENT_STOCK", "Chỉ còn " + sku.getStockQuantity() + " sản phẩm"));
            } else if (sku.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                errorItems.add(new CheckoutPreviewItemResponse(sku.getId(), "PRICE_CHANGED", "Giá sản phẩm đã thay đổi"));
            }
        }

        if (!errorItems.isEmpty()) {
            throw new BusinessRuleException("Đơn hàng có sản phẩm không hợp lệ, vui lòng tải lại giỏ hàng.");
        }

        String previewToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(previewKey, previewToken, 10, TimeUnit.MINUTES);

        return CheckoutPreviewResponse.builder()
                .previewToken(previewToken)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .errorItems(new ArrayList<>())
                .build();
    }

    @Transactional
    public void placeOrder(Long customerId, PlaceOrderRequest request) {
        String previewKey = RedisKeys.checkoutPreviewKey(customerId);
        String token = redisTemplate.opsForValue().get(previewKey);

        if (token == null || !token.equals(request.getPreviewToken())) {
            throw new BusinessRuleException("Preview session expired or invalid");
        }

        // Fetch cart to verify ownership
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        List<CartItem> cartItems = cartItemRepository.findByIdInAndCartId(request.getCartItemIds(), cart.getId());
        if (cartItems.isEmpty() || cartItems.size() != request.getCartItemIds().size()) {
            throw new ResourceNotFoundException("Some cart items not found");
        }

        List<UUID> skuIds = cartItems.stream().map(item -> item.getSku().getId()).toList();
        Map<UUID, Sku> skuMap = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s));

        // Re-validate all items
        for (CartItem item : cartItems) {
            Sku sku = skuMap.get(item.getSku().getId());
            if (sku == null || sku.getStatus() != SkuStatus.ACTIVE || sku.getStockQuantity() < item.getQuantity()
                    || sku.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                throw new BusinessRuleException("Thông tin sản phẩm đã thay đổi, vui lòng thử lại.");
            }
        }

        // Track items for rollback
        List<CartItem> decreasedItems = new ArrayList<>();
        Set<UUID> processedSkuIds = new HashSet<>();
        Set<UUID> affectedProductIds = new HashSet<>();

        // Layer 1: Redis DECRBY
        try {
            for (CartItem item : cartItems) {
                String stockKey = RedisKeys.stockKey(item.getSku().getId());
                Long remain = redisTemplate.opsForValue().decrement(stockKey, item.getQuantity());
                if (remain != null && remain < 0) {
                    // Rollback all Redis decrements done so far
                    for (CartItem decreased : decreasedItems) {
                        redisTemplate.opsForValue().increment(
                                RedisKeys.stockKey(decreased.getSku().getId()),
                                decreased.getQuantity());
                    }
                    throw new BusinessRuleException("Sản phẩm vừa hết hàng");
                }
                decreasedItems.add(item);
                processedSkuIds.add(item.getSku().getId());
                affectedProductIds.add(item.getSku().getProduct().getId());
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            // Rollback Redis on unexpected failure
            for (UUID skuId : processedSkuIds) {
                CartItem item = cartItems.stream()
                        .filter(ci -> ci.getSku().getId().equals(skuId))
                        .findFirst().orElse(null);
                if (item != null) {
                    redisTemplate.opsForValue().increment(RedisKeys.stockKey(skuId), item.getQuantity());
                }
            }
            throw new BusinessRuleException("Lỗi hệ thống khi xử lý đơn hàng");
        }

        // Layer 2: DB Update + Optimistic Lock
        try {
            for (CartItem item : cartItems) {
                Sku sku = skuMap.get(item.getSku().getId());
                int rowsAffected = skuRepository.decrementStock(
                        item.getSku().getId(), item.getQuantity(), sku.getVersion());
                if (rowsAffected == 0) {
                    throw new BusinessRuleException("Conflict in DB optimistic lock");
                }

                StockReservation reservation = StockReservation.builder()
                        .sku(item.getSku())
                        .orderId(request.getOrderId())
                        .quantity(item.getQuantity())
                        .status(ReservationStatus.PENDING)
                        .expiresAt(LocalDateTime.now().plusMinutes(15))
                        .build();
                stockReservationRepository.save(reservation);
            }
        } catch (BusinessRuleException e) {
            // Rollback Redis on DB failure
            for (CartItem decreased : decreasedItems) {
                redisTemplate.opsForValue().increment(
                        RedisKeys.stockKey(decreased.getSku().getId()),
                        decreased.getQuantity());
            }
            throw e;
        } catch (Exception e) {
            // Rollback Redis on unexpected failure
            for (CartItem decreased : decreasedItems) {
                redisTemplate.opsForValue().increment(
                        RedisKeys.stockKey(decreased.getSku().getId()),
                        decreased.getQuantity());
            }
            throw new BusinessRuleException("Lỗi cơ sở dữ liệu khi xử lý đơn hàng");
        }

        // Delete only the cart items that were ordered
        cartItemRepository.deleteAll(cartItems);

        // Recalculate product status for affected products
        recalculateProductStatuses(affectedProductIds);

        // Clean up preview session
        redisTemplate.delete(previewKey);

        log.info("Place order successful for customer {} with {} items", customerId, cartItems.size());
    }

    public void cancelPreview(Long customerId) {
        String previewKey = RedisKeys.checkoutPreviewKey(customerId);
        redisTemplate.delete(previewKey);
        log.info("Checkout preview cancelled for customer {}", customerId);
    }

    private void recalculateProductStatuses(Set<UUID> productIds) {
        for (UUID productId : productIds) {
            productRepository.findById(productId).ifPresent(product -> {
                if (product.getStatus() != ProductStatus.INACTIVE) {
                    List<Sku> skus = skuRepository.findByProductId(productId);

                    boolean hasActiveAndStock = skus.stream()
                            .anyMatch(s -> s.getStatus() == SkuStatus.ACTIVE && s.getStockQuantity() > 0);
                    boolean allOutOfStock = skus.stream()
                            .allMatch(s -> s.getStockQuantity() == 0);

                    if (hasActiveAndStock) {
                        product.setStatus(ProductStatus.ACTIVE);
                    } else if (allOutOfStock) {
                        product.setStatus(ProductStatus.OUT_OF_STOCK);
                    } else {
                        product.setStatus(ProductStatus.OUT_OF_STOCK);
                    }
                    productRepository.save(product);
                }
            });
        }
    }
}
