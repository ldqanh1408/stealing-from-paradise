package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.domain.model.*;
import com.flashsale.productservice.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/cart")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ─── GET /v1/cart ────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetailsImpl user) {
        if (user == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để xem giỏ hàng");
        }
        CartResponse cart = buildCartResponse(user.getId());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    // ─── POST /v1/cart/items ────────────────────────────────────────────────

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody AddItemRequest req) {

        validateAddItem(req);

        ProductVariant variant = variantRepository.findBySkuCode(req.getSkuCode())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại: " + req.getSkuCode()));

        // Stock check
        Inventory inventory = inventoryRepository.findBySkuCode(req.getSkuCode())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy tồn kho cho SKU: " + req.getSkuCode()));

        int availableStock = inventory.getStockAvailable() != null ? inventory.getStockAvailable() : 0;
        if (availableStock < req.getQuantity()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Số lượng yêu cầu vượt quá tồn kho. Chỉ còn " + availableStock + " sản phẩm");
        }

        // Get or create cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .id(UUID.randomUUID().toString().substring(0, 16))
                            .userId(user.getId())
                            .totalItems(0)
                            .build();
                    return cartRepository.save(newCart);
                });

        // Check if item already in cart — update quantity instead
        Optional<CartItem> existingOpt = cartItemRepository.findByCartIdAndSkuCode(cart.getId(), req.getSkuCode());
        CartItem item;
        if (existingOpt.isPresent()) {
            item = existingOpt.get();
            int newQty = item.getQuantity() + req.getQuantity();
            if (newQty > availableStock) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng yêu cầu vượt quá tồn kho. Chỉ còn " + availableStock + " sản phẩm");
            }
            item.setQuantity(newQty);
            item.setAddedAt(LocalDateTime.now());
        } else {
            item = CartItem.builder()
                    .cartId(cart.getId())
                    .userId(user.getId())
                    .variantId(variant.getId())
                    .skuCode(req.getSkuCode())
                    .fsItemId(req.getFsItemId())
                    .priceSnapshot(variant.getPrice())
                    .quantity(req.getQuantity())
                    .addedAt(LocalDateTime.now())
                    .build();
        }

        item = cartItemRepository.save(item);

        // Update cart total
        long total = cartItemRepository.countByCartId(cart.getId());
        cart.setTotalItems((int) total);
        cartRepository.save(cart);

        CartItemResponse response = toCartItemResponse(item);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── PUT /v1/cart/items/{itemId} ─────────────────────────────────────────

    @PutMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateItemQuantity(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable String itemId,
            @RequestBody UpdateQuantityRequest req) {

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item không tồn tại"));

        if (!item.getUserId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền cập nhật item này");
        }

        if (req.getQuantity() <= 0) {
            cartItemRepository.deleteById(itemId);
            updateCartTotal(item.getCartId(), user.getId());
            return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa sản phẩm khỏi giỏ hàng"));
        }

        // Stock check
        Inventory inventory = inventoryRepository.findBySkuCode(item.getSkuCode())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy tồn kho"));
        int availableStock = inventory.getStockAvailable() != null ? inventory.getStockAvailable() : 0;
        if (req.getQuantity() > availableStock) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Số lượng yêu cầu vượt quá tồn kho. Chỉ còn " + availableStock + " sản phẩm");
        }

        item.setQuantity(req.getQuantity());
        item = cartItemRepository.save(item);

        CartItemResponse response = toCartItemResponse(item);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── DELETE /v1/cart/items/{itemId} ──────────────────────────────────────

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable String itemId) {

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item không tồn tại"));

        if (!item.getUserId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền xóa item này");
        }

        cartItemRepository.deleteById(itemId);
        updateCartTotal(item.getCartId(), user.getId());

        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa sản phẩm khỏi giỏ hàng"));
    }

    // ─── DELETE /v1/cart ────────────────────────────────────────────────────

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserDetailsImpl user) {

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        if (cart != null) {
            cartItemRepository.deleteByCartId(cart.getId());
            cart.setTotalItems(0);
            cartRepository.save(cart);
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa toàn bộ giỏ hàng"));
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private void validateAddItem(AddItemRequest req) {
        if (req.getSkuCode() == null || req.getSkuCode().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "sku_code là bắt buộc");
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "quantity phải > 0");
        }
    }

    private void updateCartTotal(String cartId, Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            if (cart.getId().equals(cartId)) {
                long total = cartItemRepository.countByCartId(cartId);
                cart.setTotalItems((int) total);
                cartRepository.save(cart);
            }
        });
    }

    private CartResponse buildCartResponse(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserId(userId);
        return buildCartResponseFromItems(items);
    }

    private CartResponse buildCartResponseFromItems(List<CartItem> items) {
        if (items.isEmpty()) {
            return CartResponse.builder()
                    .sellers(Collections.emptyList())
                    .totalItems(0)
                    .subtotal(BigDecimal.ZERO)
                    .build();
        }

        // Group items by sellerId (via product lookup)
        Map<Long, List<CartItem>> bySeller = new LinkedHashMap<>();
        for (CartItem item : items) {
            ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
            if (variant == null) continue;
            Product product = productRepository.findById(variant.getProductId()).orElse(null);
            if (product == null) continue;
            bySeller.computeIfAbsent(product.getSellerId(), k -> new ArrayList<>()).add(item);
        }

        BigDecimal grandTotal = BigDecimal.ZERO;
        int totalItems = 0;
        List<CartSellerResponse> sellers = new ArrayList<>();

        for (Map.Entry<Long, List<CartItem>> entry : bySeller.entrySet()) {
            BigDecimal sellerSubtotal = BigDecimal.ZERO;
            List<CartItemResponse> sellerItems = new ArrayList<>();

            for (CartItem item : entry.getValue()) {
                ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
                Product product = variant != null ? productRepository.findById(variant.getProductId()).orElse(null) : null;
                Inventory inventory = inventoryRepository.findBySkuCode(item.getSkuCode()).orElse(null);

                CartItemResponse itemResp = CartItemResponse.builder()
                        .cartItemId(item.getId())
                        .skuCode(item.getSkuCode())
                        .productId(variant != null ? variant.getProductId() : null)
                        .productName(product != null ? product.getName() : "Sản phẩm không xác định")
                        .variantName(variant != null ? variant.getTierName() : "")
                        .unitPrice(item.getPriceSnapshot())
                        .quantity(item.getQuantity())
                        .stockAvailable(inventory != null && inventory.getStockAvailable() != null ? inventory.getStockAvailable() : 0)
                        .isFlash(product != null && Boolean.TRUE.equals(product.getIsFlash()))
                        .fsItemId(item.getFsItemId())
                        .subtotal(item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .addedAt(item.getAddedAt())
                        .build();

                sellerItems.add(itemResp);
                sellerSubtotal = sellerSubtotal.add(itemResp.getSubtotal());
                totalItems += item.getQuantity();
            }

            String sellerName = "Seller " + entry.getKey();
            sellers.add(CartSellerResponse.builder()
                    .sellerId(entry.getKey())
                    .sellerName(sellerName)
                    .items(sellerItems)
                    .sellerSubtotal(sellerSubtotal)
                    .build());

            grandTotal = grandTotal.add(sellerSubtotal);
        }

        return CartResponse.builder()
                .sellers(sellers)
                .totalItems(totalItems)
                .subtotal(grandTotal)
                .build();
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
        Product product = variant != null ? productRepository.findById(variant.getProductId()).orElse(null) : null;
        Inventory inventory = inventoryRepository.findBySkuCode(item.getSkuCode()).orElse(null);

        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .skuCode(item.getSkuCode())
                .productId(variant != null ? variant.getProductId() : null)
                .productName(product != null ? product.getName() : "Sản phẩm không xác định")
                .variantName(variant != null ? variant.getTierName() : "")
                .unitPrice(item.getPriceSnapshot())
                .quantity(item.getQuantity())
                .stockAvailable(inventory != null && inventory.getStockAvailable() != null ? inventory.getStockAvailable() : 0)
                .isFlash(product != null && Boolean.TRUE.equals(product.getIsFlash()))
                .fsItemId(item.getFsItemId())
                .subtotal(item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .addedAt(item.getAddedAt())
                .build();
    }

    // ─── Request DTOs ───────────────────────────────────────────────────────

    @lombok.Data
    public static class AddItemRequest {
        private String skuCode;
        private Integer quantity;
        private Long fsItemId;
    }

    @lombok.Data
    public static class UpdateQuantityRequest {
        private Integer quantity;
    }

    // ─── Response DTOs ─────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CartItemResponse {
        private String cartItemId;
        private String skuCode;
        private String productId;
        private String productName;
        private String variantName;
        private BigDecimal unitPrice;
        private Integer quantity;
        private Integer stockAvailable;
        private boolean isFlash;
        private Long fsItemId;
        private BigDecimal subtotal;
        private LocalDateTime addedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CartSellerResponse {
        private Long sellerId;
        private String sellerName;
        private List<CartItemResponse> items;
        private BigDecimal sellerSubtotal;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CartResponse {
        private List<CartSellerResponse> sellers;
        private Integer totalItems;
        private BigDecimal subtotal;
    }
}
