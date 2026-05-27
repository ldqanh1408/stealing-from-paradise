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

    @Transactional
    public ApiResponse<CartResponse> getCart(UserDetailsImpl user) {
        Optional<Cart> cartOpt = cartRepository.findByCustomerId(user.getId());
        if (cartOpt.isEmpty()) {
            return ApiResponse.success(CartResponse.builder()
                    .customerId(user.getId())
                    .items(Collections.emptyList())
                    .totalItems(0)
                    .subtotal(BigDecimal.ZERO)
                    .hasPriceChanges(false)
                    .groupedBySeller(Collections.emptyMap())
                    .build());
        }
        return ApiResponse.success(toCartResponse(cartOpt.get()));
    }

    @Transactional
    public ApiResponse<CartResponse> addItem(AddCartItemRequest request, UserDetailsImpl user) {
        cartRepository.findByCustomerId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().customerId(user.getId()).build()));

        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        if (variant.getStatus() != VariantStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Variant is not available for purchase");
        }

        Optional<CartItem> existingOpt = cartItemRepository.findByCustomerIdAndVariantId(
                user.getId(), request.getVariantId());

        if (existingOpt.isPresent()) {
            CartItem item = existingOpt.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            cartItemRepository.save(item);
        } else {
            Product product = productRepository.findById(variant.getProductId())
                    .filter(p -> p.getDeletedAt() == null)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

            CartItem newItem = CartItem.builder()
                    .customerId(user.getId())
                    .variantId(variant.getId())
                    .quantity(request.getQuantity())
                    .priceSnapshot(variant.getPrice())
                    .variantNameSnapshot(variant.getVariantName())
                    .variantImageSnapshot(variant.getImageUrl())
                    .sellerId(product.getSellerId())
                    .build();
            cartItemRepository.save(newItem);
        }

        Cart cart = cartRepository.findByCustomerId(user.getId()).get();
        return ApiResponse.success(toCartResponse(cart));
    }

    @Transactional
    public ApiResponse<CartResponse> updateItem(UUID variantId, UpdateCartItemRequest request, UserDetailsImpl user) {
        CartItem item = cartItemRepository.findByCustomerIdAndVariantId(user.getId(), variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart item not found"));

        ProductVariant variant = variantRepository.findById(item.getVariantId())
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        if (variant.getStatus() != VariantStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Variant is no longer available for purchase: " + variant.getStatus().name());
        }

        if (variant.getStockQuantity() < request.getQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Requested quantity exceeds available stock: " + variant.getStockQuantity());
        }

        if (variant.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
            item.setPriceSnapshot(variant.getPrice());
        }
        if (!variant.getVariantName().equals(item.getVariantNameSnapshot())) {
            item.setVariantNameSnapshot(variant.getVariantName());
        }
        if (variant.getImageUrl() != null && !variant.getImageUrl().equals(item.getVariantImageSnapshot())) {
            item.setVariantImageSnapshot(variant.getImageUrl());
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        Cart cart = cartRepository.findByCustomerId(user.getId()).get();
        return ApiResponse.success(toCartResponse(cart));
    }

    @Transactional
    public ApiResponse<CartResponse> removeItem(UUID variantId, UserDetailsImpl user) {
        cartItemRepository.findByCustomerIdAndVariantId(user.getId(), variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Cart item not found"));

        cartItemRepository.deleteByCustomerIdAndVariantId(user.getId(), variantId);

        Optional<Cart> cartOpt = cartRepository.findByCustomerId(user.getId());
        if (cartOpt.isPresent()) {
            return ApiResponse.success(toCartResponse(cartOpt.get()));
        }
        return ApiResponse.success(CartResponse.builder()
                .customerId(user.getId())
                .items(Collections.emptyList())
                .totalItems(0)
                .subtotal(BigDecimal.ZERO)
                .hasPriceChanges(false)
                .groupedBySeller(Collections.emptyMap())
                .build());
    }

    @Transactional
    public ApiResponse<Void> clearCart(UserDetailsImpl user) {
        cartItemRepository.deleteAllByCustomerId(user.getId());
        return ApiResponse.success(null);
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCustomerId(cart.getCustomerId());
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

        return CartItemResponse.builder()
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
                .sellerId(item.getSellerId())
                .build();
    }
}
