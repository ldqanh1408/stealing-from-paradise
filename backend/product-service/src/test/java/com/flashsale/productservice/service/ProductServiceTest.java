package com.flashsale.productservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.productservice.domain.model.Category;
import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.Product;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.repository.CategoryRepository;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.dto.request.CreateProductRequest;
import com.flashsale.productservice.dto.response.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private KafkaProducerService kafkaProducer;
    @InjectMocks private ProductService productService;

    private Category leafCategory;
    private Product draftProduct;

    @BeforeEach
    void setUp() {
        leafCategory = Category.builder()
                .id("cat-leaf-1")
                .name("Smartphone")
                .slug("smartphone")
                .parentId("cat-electronics")
                .level(2)
                .isActive(true)
                .build();

        draftProduct = Product.builder()
                .id("prod-1")
                .sellerId(1L)
                .categoryId("cat-leaf-1")
                .name("iPhone 15")
                .status(ProductService.DRAFT)
                .description("Latest iPhone")
                .images(List.of("img1.jpg"))
                .isFlashSale(false)
                .rejectCount(0)
                .build();
    }

    // ─── createProduct tests ─────────────────────────────────────────────────

    @Test
    void createProduct_validRequest_createsDraftProduct() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("iPhone 15")
                .description("Latest iPhone")
                .categoryId("cat-leaf-1")
                .images(List.of("img1.jpg"))
                .build();

        when(categoryRepository.findById("cat-leaf-1")).thenReturn(Optional.of(leafCategory));
        when(categoryRepository.findByParentId("cat-leaf-1")).thenReturn(List.of()); // leaf
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("prod-new");
            return p;
        });
        when(variantRepository.findByProductId(anyString())).thenReturn(List.of());
        when(inventoryRepository.findByVariantCode(anyString())).thenReturn(Optional.empty());

        ProductResponse result = productService.createProduct(1L, req);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("iPhone 15");
        assertThat(result.getStatus()).isEqualTo(ProductService.DRAFT);
        verify(kafkaProducer).publish(eq("product.created"), any());
    }

    @Test
    void createProduct_nonLeafCategory_throwsException() {
        Category nonLeaf = Category.builder()
                .id("cat-electronics")
                .name("Electronics")
                .slug("electronics")
                .parentId(null)
                .level(0)
                .isActive(true)
                .build();

        CreateProductRequest req = CreateProductRequest.builder()
                .name("Product")
                .description("Desc")
                .categoryId("cat-electronics")
                .images(List.of("img1.jpg"))
                .build();

        when(categoryRepository.findById("cat-electronics")).thenReturn(Optional.of(nonLeaf));
        when(categoryRepository.findByParentId("cat-electronics")).thenReturn(List.of(leafCategory)); // has children

        assertThatThrownBy(() -> productService.createProduct(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("danh mục lá");
    }

    @Test
    void createProduct_nonexistentCategory_throwsException() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Product")
                .description("Desc")
                .categoryId("nonexistent")
                .images(List.of("img1.jpg"))
                .build();

        when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(1L, req))
                .isInstanceOf(AppException.class);
    }

    // ─── submitForReview tests ────────────────────────────────────────────────

    @Test
    void submitForReview_fromDraft_withVariantAndStock_succeeds() {
        ProductVariant variant = ProductVariant.builder()
                .id("var-1")
                .productId("prod-1")
                .variantCode("SKU-001")
                .variantName("Black 256GB")
                .price(BigDecimal.valueOf(999))
                .build();

        Inventory inventory = Inventory.builder()
                .id("inv-1")
                .skuCode("SKU-001")
                .stockAvailable(10)
                .build();

        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));
        when(variantRepository.findByProductId("prod-1")).thenReturn(List.of(variant));
        when(inventoryRepository.findByVariantCode("SKU-001")).thenReturn(Optional.of(inventory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findById(anyString())).thenReturn(Optional.of(leafCategory));

        ProductResponse result = productService.submitForReview("prod-1", 1L);

        assertThat(result.getStatus()).isEqualTo(ProductService.PENDING);
        verify(kafkaProducer).publish(eq("product.pending_review"), any());
    }

    @Test
    void submitForReview_fromApproved_throwsException() {
        draftProduct.setStatus(ProductService.APPROVED);
        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));

        assertThatThrownBy(() -> productService.submitForReview("prod-1", 1L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("DRAFT hoặc REJECTED");
    }

    @Test
    void submitForReview_noVariants_throwsException() {
        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));
        when(variantRepository.findByProductId("prod-1")).thenReturn(List.of());

        assertThatThrownBy(() -> productService.submitForReview("prod-1", 1L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("biến thể");
    }

    // ─── publishProduct tests ─────────────────────────────────────────────────

    @Test
    void publishProduct_fromApproved_succeeds() {
        draftProduct.setStatus(ProductService.APPROVED);

        ProductVariant variant = ProductVariant.builder()
                .id("var-1")
                .productId("prod-1")
                .variantCode("SKU-001")
                .price(BigDecimal.valueOf(999))
                .build();
        Inventory inventory = Inventory.builder()
                .skuCode("SKU-001")
                .stockAvailable(10)
                .build();

        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));
        when(variantRepository.findByProductId("prod-1")).thenReturn(List.of(variant));
        when(inventoryRepository.findByVariantCode("SKU-001")).thenReturn(Optional.of(inventory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findById(anyString())).thenReturn(Optional.of(leafCategory));

        ProductResponse result = productService.publishProduct("prod-1", 1L);

        assertThat(result.getStatus()).isEqualTo(ProductService.ACTIVE);
    }

    @Test
    void publishProduct_fromDraft_throwsException() {
        // draft cannot be directly published
        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));

        assertThatThrownBy(() -> productService.publishProduct("prod-1", 1L))
                .isInstanceOf(AppException.class);
    }

    // ─── deleteProduct tests ─────────────────────────────────────────────────

    @Test
    void deleteProduct_draftProduct_softDeletes() {
        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() ->
            productService.deleteProduct("prod-1", 1L));

        verify(productRepository).save(argThat(p -> p.getDeletedAt() != null));
        verify(kafkaProducer).publish(eq("product.deleted"), any());
    }

    @Test
    void deleteProduct_activeProduct_throwsException() {
        draftProduct.setStatus(ProductService.ACTIVE);
        when(productRepository.findByIdAndSellerId("prod-1", 1L))
                .thenReturn(Optional.of(draftProduct));

        assertThatThrownBy(() -> productService.deleteProduct("prod-1", 1L))
                .isInstanceOf(AppException.class);
    }
}
