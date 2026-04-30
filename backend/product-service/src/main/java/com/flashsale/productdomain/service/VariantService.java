package com.flashsale.productdomain.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productdomain.domain.model.Inventory;
import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.ProductVariant;
import com.flashsale.productdomain.domain.repository.InventoryRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.ProductVariantRepository;
import com.flashsale.productdomain.dto.request.CreateVariantRequest;
import com.flashsale.productdomain.dto.request.UpdateVariantRequest;
import com.flashsale.productdomain.dto.response.VariantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public List<VariantResponse> listVariants(String productId, Long sellerId) {
        validateProductOwnership(productId, sellerId);

        // Batch-load inventory by productId (1 query instead of N)
        Map<String, Integer> stockBySku = inventoryRepository.findByProductId(productId).stream()
                .collect(HashMap::new, (m, inv) -> m.put(inv.getSkuCode(), inv.getStockAvailable()), HashMap::putAll);

        return variantRepository.findByProductId(productId)
                .stream().map(v -> VariantResponse.from(v, stockBySku.getOrDefault(v.getSkuCode(), 0)))
                .toList();
    }

    public VariantResponse createVariant(String productId, Long sellerId, CreateVariantRequest req) {
        validateProductOwnership(productId, sellerId);

        if (variantRepository.existsBySkuCode(req.getSkuCode())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "SKU code đã tồn tại: " + req.getSkuCode());
        }

        ProductVariant variant = ProductVariant.builder()
                .productId(productId)
                .skuCode(req.getSkuCode())
                .tierName(req.getTierName())
                .price(req.getPrice())
                .build();

        variant = variantRepository.save(variant);

        // Auto-create inventory entry with zero stock
        if (!inventoryRepository.existsBySkuCode(req.getSkuCode())) {
            Inventory inventory = Inventory.builder()
                    .skuCode(req.getSkuCode())
                    .productId(productId)
                    .stockTotal(0)
                    .stockLocked(0)
                    .stockAvailable(0)
                    .stockFlashReserved(0)
                    .build();
            inventoryRepository.save(inventory);
        }

        return VariantResponse.from(variant, 0);
    }

    public VariantResponse updateVariant(String variantId, Long sellerId, UpdateVariantRequest req) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant không tồn tại"));

        // Verify seller owns the parent product
        validateProductOwnership(variant.getProductId(), sellerId);

        if (req.getTierName() != null) variant.setTierName(req.getTierName());
        if (req.getPrice() != null)    variant.setPrice(req.getPrice());

        Integer stock = inventoryRepository.findBySkuCode(variant.getSkuCode())
                .map(Inventory::getStockAvailable).orElse(0);
        return VariantResponse.from(variantRepository.save(variant), stock);
    }

    public void deleteVariant(String variantId, Long sellerId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant không tồn tại"));

        validateProductOwnership(variant.getProductId(), sellerId);

        // Block deletion if inventory has locked stock
        inventoryRepository.findBySkuCode(variant.getSkuCode()).ifPresent(inv -> {
            if (inv.getStockLocked() != null && inv.getStockLocked() > 0) {
                throw new AppException(ErrorCode.ALREADY_EXISTS,
                        "Variant đang có stock_locked > 0, không thể xóa");
            }
        });

        variantRepository.deleteById(variantId);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Product validateProductOwnership(String productId, Long sellerId) {
        return productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Sản phẩm không tồn tại hoặc không thuộc seller"));
    }
}
