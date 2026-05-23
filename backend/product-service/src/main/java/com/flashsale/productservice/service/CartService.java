package com.flashsale.productservice.service;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.cart.AddCartItemRequest;
import com.flashsale.productservice.dto.cart.CartItemResponse;
import com.flashsale.productservice.dto.cart.CartResponse;
import com.flashsale.productservice.dto.cart.UpdateCartItemRequest;
import com.flashsale.productservice.entity.*;
import com.flashsale.productservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StockReservationRepository reservationRepository;

    @Transactional
    public ApiResponse<CartResponse> getCart(UserDetailsImpl user) {
        Cart cart = cartRepository.findByCustomerIdAndDeletedAtIsNull(user.getId())
                .orElse(null);

        if (cart == null) {
            cart = Cart.builder()
                    .customerId(user.getId())
                    .build();
            cart = cartRepository.save(cart);
        }

        return ApiResponse.success(toCartResponse(cart));
    }

    @Transactional
    public ApiResponse<CartResponse> addItem(AddCartItemRequest request, UserDetailsImpl user) {
        Cart cart = cartRepository.findByCustomerIdAndDeletedAtIsNull(user.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .customerId(user.getId())
                            .build();
                    return cartRepository.save(newCart);
                });

        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        if (variant.getStatus() != VariantStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Variant is not available for purchase");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndVariantIdAndDeletedAtIsNull(
                cart.getId(), request.getVariantId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            cartItemRepository.save(item);
        } else {
            Product product = productRepository.findById(variant.getProductId())
                    .filter(p -> p.getDeletedAt() == null)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

            CartItem newItem = CartItem.builder()
                    .cartId(cart.getId())
                    .variantId(variant.getId())
                    .quantity(request.getQuantity())
                    .priceSnapshot(variant.getPrice())
                    .variantNameSnapshot(variant.getVariantName())
                    .variantImageSnapshot(variant.getImageUrl())
                    .sellerId(product.getSellerId())
                    .build();
            cartItemRepository.save(newItem);
        }

        return ApiResponse.success(toCartResponse(cart));

    @Transactional
    public ApiResponse<CartResponse> updateItem(UUID itemId, UpdateCartItemRequest request, UserDetailsImpl user) {
        Cart cart = cartRepository.findByCustomerIdAndDeletedAtIsNull(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart not found"));

        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCartId().equals(cart.getId()))
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart item not found"));

        ProductVariant variant = variantRepository.findById(item.getVariantId())
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        if (request.getQuantity() > variant.getStockQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Requested quantity exceeds available stock: " + variant.getStockQuantity());
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        return ApiResponse.success(toCartResponse(cart));
    }

    @Transactional
    public ApiResponse<CartResponse> removeItem(UUID itemId, UserDetailsImpl user) {
        Cart cart = cartRepository.findByCustomerIdAndDeletedAtIsNull(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart not found"));

        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCartId().equals(cart.getId()))
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart item not found"));

        item.setDeletedAt(LocalDateTime.now());
        cartItemRepository.save(item);

        return ApiResponse.success(toCartResponse(cart));
    }

    @Transactional
    public ApiResponse<Void> clearCart(UserDetailsImpl user) {
        Cart cart = cartRepository.findByCustomerIdAndDeletedAtIsNull(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart not found"));

        List<CartItem> items = cartItemRepository.findByCartIdAndDeletedAtIsNull(cart.getId());
        for (CartItem item : items) {
            item.setDeletedAt(LocalDateTime.now());
            cartItemRepository.save(item);
        }

        return ApiResponse.success(null);
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdAndDeletedAtIsNull(cart.getId());
        List<CartItemResponse> itemResponses = items.stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());

        boolean hasPriceChanges = itemResponses.stream().anyMatch(CartItemResponse::getPriceChanged);
        Map<Long, List<CartItemResponse>> groupedBySeller = itemResponses.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getSellerId() != null ? item.getSellerId() : 0L
                ));

        int totalItems = itemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        BigDecimal subtotal = itemResponses.stream()
                .map(item -> item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .customerId(cart.getCustomerId())
                .items(itemResponses)
                .totalItems(totalItems)
                .subtotal(subtotal)
                .hasPriceChanges(hasPriceChanges)
                .groupedBySeller(groupedBySeller)
                .build();
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        ProductVariant variant = variantRepository.findById(item.getVariantId())
                .filter(v -> v.getDeletedAt() == null)
                .orElse(null);

        boolean priceChanged = variant != null && variant.getPrice().compareTo(item.getPriceSnapshot()) != 0;
        boolean outOfStock = variant == null || variant.getStatus() == VariantStatus.OUT_OF_STOCK;
        boolean unavailable = variant == null;
        boolean insufficientStock = variant != null && variant.getStockQuantity() < item.getQuantity();

        BigDecimal subtotal = item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));

        Long sellerId = item.getSellerId() != null ? item.getSellerId() : getSellerIdForVariant(item.getVariantId());

        return CartItemResponse.builder()
                .id(item.getId())
                .variantId(item.getVariantId())
                .variantCode(variant != null ? variant.getVariantCode() : null)
                .variantName(variant != null ? variant.getVariantName() : item.getVariantNameSnapshot())
                .priceSnapshot(item.getPriceSnapshot())
                .currentPrice(variant != null ? variant.getPrice() : null)
                .priceChanged(priceChanged)
                .quantity(item.getQuantity())
                .variantImageSnapshot(item.getVariantImageSnapshot())
                .subtotal(subtotal)
                .outOfStock(outOfStock)
                .unavailable(unavailable)
                .insufficientStock(insufficientStock)
                .sellerId(sellerId)
                .build();
    }

    private Long getSellerIdForVariant(UUID variantId) {
        return variantRepository.findById(variantId)
                .map(v -> productRepository.findById(v.getProductId())
                        .map(Product::getSellerId)
                        .orElse(0L))
                .orElse(0L);
    }

}
