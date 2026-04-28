package com.flashsale.productdomain.service;

import com.flashsale.productdomain.domain.model.Cart;
import com.flashsale.productdomain.domain.model.CartItem;
import com.flashsale.productdomain.domain.model.CartStatus;
import com.flashsale.productdomain.domain.model.ProductStatus;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.model.SkuStatus;
import com.flashsale.productdomain.domain.repository.CartItemRepository;
import com.flashsale.productdomain.domain.repository.CartRepository;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import com.flashsale.productdomain.dto.request.AddCartItemRequest;
import com.flashsale.productdomain.dto.request.UpdateCartItemRequest;
import com.flashsale.productdomain.dto.response.CartItemResponse;
import com.flashsale.productdomain.dto.response.CartResponse;
import com.flashsale.productdomain.exception.BusinessRuleException;
import com.flashsale.productdomain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final SkuRepository skuRepository;

    @Transactional
    public CartItemResponse addToCart(Long customerId, AddCartItemRequest request) {
        Sku sku = skuRepository.findById(request.getSkuId())
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found"));

        if (sku.getStatus() != SkuStatus.ACTIVE) {
            throw new BusinessRuleException("Sản phẩm ngừng bán");
        }
        if (sku.getStockQuantity() == 0) {
            throw new BusinessRuleException("Sản phẩm không còn hàng");
        }
        if (request.getQuantity() > sku.getStockQuantity()) {
            throw new BusinessRuleException("Chỉ còn " + sku.getStockQuantity() + " sản phẩm");
        }

        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .customerId(customerId)
                        .status(CartStatus.ACTIVE)
                        .build()));

        CartItem cartItem = cartItemRepository.findByCartIdAndSkuId(cart.getId(), sku.getId())
                .orElseGet(() -> CartItem.builder()
                        .cart(cart)
                        .sku(sku)
                        .quantity(0)
                        .build());

        cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        cartItem.setPriceSnapshot(sku.getPrice());
        cartItem.setSkuNameSnapshot(sku.getProduct().getName() + " " + sku.getVariantName());
        cartItem.setSkuImageSnapshot(sku.getImageUrl());

        cartItem = cartItemRepository.save(cartItem);

        return CartItemResponse.builder()
                .id(cartItem.getId())
                .cartId(cart.getId())
                .skuId(sku.getId())
                .quantity(cartItem.getQuantity())
                .priceSnapshot(cartItem.getPriceSnapshot())
                .skuNameSnapshot(cartItem.getSkuNameSnapshot())
                .skuImageSnapshot(cartItem.getSkuImageSnapshot())
                .build();
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

        List<UUID> skuIds = cartItems.stream().map(item -> item.getSku().getId()).toList();
        Map<UUID, Sku> skuMap = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s));

        List<CartItemResponse> itemResponses = new ArrayList<>();

        for (CartItem item : cartItems) {
            Sku sku = skuMap.get(item.getSku().getId());
            boolean hasPriceChange = false;
            boolean outOfStock = false;
            boolean isUnavailable = false;

            if (sku != null) {
                if (sku.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                    hasPriceChange = true;
                }
                if (sku.getStockQuantity() == 0) {
                    outOfStock = true;
                }
                if (sku.getStatus() != SkuStatus.ACTIVE || sku.getProduct().getStatus() != ProductStatus.ACTIVE) {
                    isUnavailable = true;
                }

                itemResponses.add(CartItemResponse.builder()
                        .id(item.getId())
                        .cartId(cart.getId())
                        .skuId(sku.getId())
                        .quantity(item.getQuantity())
                        .priceSnapshot(item.getPriceSnapshot())
                        .skuNameSnapshot(item.getSkuNameSnapshot())
                        .skuImageSnapshot(item.getSkuImageSnapshot())
                        .hasPriceChange(hasPriceChange)
                        .isUnavailable(isUnavailable)
                        .outOfStock(outOfStock)
                        .currentPrice(sku.getPrice())
                        .build());
            } else {
                itemResponses.add(CartItemResponse.builder()
                        .id(item.getId())
                        .cartId(cart.getId())
                        .skuId(item.getSku().getId())
                        .quantity(item.getQuantity())
                        .priceSnapshot(item.getPriceSnapshot())
                        .skuNameSnapshot(item.getSkuNameSnapshot())
                        .skuImageSnapshot(item.getSkuImageSnapshot())
                        .isUnavailable(true)
                        .build());
            }
        }

        return CartResponse.builder()
                .id(cart.getId())
                .customerId(cart.getCustomerId())
                .status(cart.getStatus())
                .items(itemResponses)
                .build();
    }

    @Transactional
    public CartItemResponse updateCartItem(Long customerId, UUID skuId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem cartItem = cartItemRepository.findByCartIdAndSkuId(cart.getId(), skuId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found"));

        // Validate stock availability
        if (sku.getStatus() != SkuStatus.ACTIVE) {
            throw new BusinessRuleException("Sản phẩm ngừng bán");
        }
        if (request.getQuantity() > sku.getStockQuantity()) {
            throw new BusinessRuleException("Chỉ còn " + sku.getStockQuantity() + " sản phẩm");
        }
        if (request.getQuantity() <= 0) {
            throw new BusinessRuleException("Số lượng phải lớn hơn 0");
        }

        cartItem.setQuantity(request.getQuantity());
        // Update price snapshot if price changed
        if (sku.getPrice().compareTo(cartItem.getPriceSnapshot()) != 0) {
            cartItem.setPriceSnapshot(sku.getPrice());
        }
        cartItem = cartItemRepository.save(cartItem);

        return CartItemResponse.builder()
                .id(cartItem.getId())
                .cartId(cart.getId())
                .skuId(sku.getId())
                .quantity(cartItem.getQuantity())
                .priceSnapshot(cartItem.getPriceSnapshot())
                .skuNameSnapshot(cartItem.getSkuNameSnapshot())
                .skuImageSnapshot(cartItem.getSkuImageSnapshot())
                .build();
    }

    @Transactional
    public void removeFromCart(Long customerId, UUID skuId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem cartItem = cartItemRepository.findByCartIdAndSkuId(cart.getId(), skuId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        cartItemRepository.delete(cartItem);
    }
}
