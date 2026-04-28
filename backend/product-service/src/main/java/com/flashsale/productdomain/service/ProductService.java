package com.flashsale.productdomain.service;

import com.flashsale.productdomain.config.RedisKeys;
import com.flashsale.productdomain.domain.model.Category;
import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.ProductImage;
import com.flashsale.productdomain.domain.model.ProductStatus;
import com.flashsale.productdomain.domain.model.ReviewSummary;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.model.SkuStatus;
import com.flashsale.productdomain.domain.repository.CategoryRepository;
import com.flashsale.productdomain.domain.repository.ProductImageRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.ReviewSummaryRepository;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import com.flashsale.productdomain.dto.request.CreateProductRequest;
import com.flashsale.productdomain.dto.request.CreateSkuRequest;
import com.flashsale.productdomain.dto.request.UpdateProductRequest;
import com.flashsale.productdomain.dto.request.UpdateSkuRequest;
import com.flashsale.productdomain.dto.response.ProductDetailResponse;
import com.flashsale.productdomain.dto.response.ProductImageResponse;
import com.flashsale.productdomain.dto.response.ReviewSummaryResponse;
import com.flashsale.productdomain.dto.response.SkuResponse;
import com.flashsale.productdomain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public Product createProduct(Long sellerId, CreateProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Product product = Product.builder()
                .category(category)
                .sellerId(sellerId)
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .attributes(request.getAttributes())
                .status(ProductStatus.INACTIVE) // default inactive
                .build();
        product = productRepository.save(product);

        List<Sku> skus = new ArrayList<>();
        for (CreateSkuRequest skuReq : request.getSkus()) {
            Sku sku = Sku.builder()
                    .product(product)
                    .skuCode(skuReq.getSkuCode())
                    .variantName(skuReq.getVariantName())
                    .variantAttributes(skuReq.getVariantAttributes())
                    .price(skuReq.getPrice())
                    .originalPrice(skuReq.getOriginalPrice())
                    .stockQuantity(skuReq.getStockQuantity())
                    .status(SkuStatus.ACTIVE)
                    .imageUrl(skuReq.getImageUrl())
                    .priceUpdatedAt(LocalDateTime.now())
                    .build();
            skus.add(sku);
        }
        skuRepository.saveAll(skus);

        for (Sku sku : skus) {
            redisTemplate.opsForValue().set(RedisKeys.stockKey(sku.getId()), String.valueOf(sku.getStockQuantity()));
        }

        // Active after creating
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        // Async event
        kafkaTemplate.send("product.created", product.getId().toString(), "Product Created");

        return product;
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(String slug) {
        // Fetch product with status ACTIVE or OUT_OF_STOCK (still visible to customers)
        Product product = productRepository.findBySlugAndStatusIn(
                        slug, Arrays.asList(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Fetch all SKUs for this product (including out_of_stock for display)
        List<Sku> skus = skuRepository.findByProductId(product.getId());

        // Fetch product images (both general and SKU-specific)
        List<ProductImage> productImages = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId());

        // Fetch review summary
        ReviewSummaryResponse reviewSummary = reviewSummaryRepository.findByProductId(product.getId())
                .map(rs -> ReviewSummaryResponse.builder()
                        .avgRating(rs.getAvgRating())
                        .totalCount(rs.getTotalCount() != null ? rs.getTotalCount().intValue() : 0)
                        .count5star(rs.getCount5Star() != null ? rs.getCount5Star().intValue() : 0)
                        .count4star(rs.getCount4Star() != null ? rs.getCount4Star().intValue() : 0)
                        .count3star(rs.getCount3Star() != null ? rs.getCount3Star().intValue() : 0)
                        .count2star(rs.getCount2Star() != null ? rs.getCount2Star().intValue() : 0)
                        .count1star(rs.getCount1Star() != null ? rs.getCount1Star().intValue() : 0)
                        .countWithMedia(rs.getCountWithMedia() != null ? rs.getCountWithMedia().intValue() : 0)
                        .build())
                .orElse(null);

        // Map SKUs to response (use Redis for stock if available)
        List<SkuResponse> skuResponses = skus.stream()
                .map(sku -> {
                    String stockKey = RedisKeys.stockKey(sku.getId());
                    Integer stockQuantity = sku.getStockQuantity();
                    // Try to get from Redis for more real-time stock
                    String redisStock = redisTemplate.opsForValue().get(stockKey);
                    if (redisStock != null) {
                        try {
                            stockQuantity = Integer.parseInt(redisStock);
                        } catch (NumberFormatException ignored) {
                            // Use DB value if Redis parse fails
                        }
                    }
                    return SkuResponse.builder()
                            .id(sku.getId())
                            .price(sku.getPrice())
                            .originalPrice(sku.getOriginalPrice())
                            .stockQuantity(stockQuantity)
                            .status(sku.getStatus())
                            .variantAttributes(sku.getVariantAttributes())
                            .imageUrl(sku.getImageUrl())
                            .priceUpdatedAt(sku.getPriceUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        // Map product images to response
        List<ProductImageResponse> imageResponses = productImages.stream()
                .map(img -> ProductImageResponse.builder()
                        .id(img.getId())
                        .skuId(img.getSku() != null ? img.getSku().getId() : null)
                        .url(img.getUrl())
                        .sortOrder(img.getSortOrder())
                        .build())
                .collect(Collectors.toList());

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .attributes(product.getAttributes())
                .status(product.getStatus())
                .skus(skuResponses)
                .images(imageResponses)
                .summary(reviewSummary)
                .build();
    }

    @Transactional
    public Sku updateSku(UUID skuId, UpdateSkuRequest request) {
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found"));

        boolean priceChanged = false;
        boolean stockChanged = false;

        if (request.getPrice() != null && request.getPrice().compareTo(sku.getPrice()) != 0) {
            sku.setPrice(request.getPrice());
            sku.setPriceUpdatedAt(LocalDateTime.now());
            priceChanged = true;
        }

        if (request.getOriginalPrice() != null) {
            sku.setOriginalPrice(request.getOriginalPrice());
        }

        if (request.getStockQuantity() != null) {
            sku.setStockQuantity(request.getStockQuantity());
            stockChanged = true;
        }

        if (request.getStatus() != null) {
            sku.setStatus(SkuStatus.valueOf(request.getStatus().name()));
        }

        sku = skuRepository.save(sku);

        if (stockChanged || request.getStatus() != null) {
            redisTemplate.opsForValue().set(RedisKeys.stockKey(sku.getId()), String.valueOf(sku.getStockQuantity()));
        }

        recalculateProductStatus(sku.getProduct());

        if (priceChanged) {
            kafkaTemplate.send("sku.price_updated", sku.getId().toString(), "SKU Price Updated");
        }
        if (stockChanged) {
            kafkaTemplate.send("sku.stock_updated", sku.getId().toString(), "SKU Stock Updated");
        }

        return sku;
    }

    @Transactional
    public Product updateProduct(UUID productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (request.getName() != null)
            product.setName(request.getName());
        if (request.getDescription() != null)
            product.setDescription(request.getDescription());
        if (request.getAttributes() != null)
            product.setAttributes(request.getAttributes());
        if (request.getStatus() != null) {
            product.setStatus(ProductStatus.valueOf(request.getStatus().name()));
        }

        product = productRepository.save(product);

        if (product.getStatus() == ProductStatus.INACTIVE) {
            kafkaTemplate.send("product.inactive", product.getId().toString(), "Product Inactive");
        } else {
            kafkaTemplate.send("product.updated", product.getId().toString(), "Product Updated");
        }

        return product;
    }

    private void recalculateProductStatus(Product product) {
        if (product.getStatus() == ProductStatus.INACTIVE) {
            // If seller explicitly marked it as inactive, don't auto-calculate to
            // active/out_of_stock
            return;
        }

        List<Sku> skus = skuRepository.findByProductId(product.getId());

        boolean hasActiveAndStock = false;
        boolean allOutOfStock = true;

        for (Sku sku : skus) {
            if (sku.getStatus() == SkuStatus.ACTIVE && sku.getStockQuantity() > 0) {
                hasActiveAndStock = true;
            }
            if (sku.getStockQuantity() > 0) {
                allOutOfStock = false;
            }
        }

        if (hasActiveAndStock) {
            product.setStatus(ProductStatus.ACTIVE);
        } else if (allOutOfStock) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else {
            // Some are active but no stock, or something else. Default to out_of_stock if
            // no active+stock
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        }

        productRepository.save(product);
    }
}
