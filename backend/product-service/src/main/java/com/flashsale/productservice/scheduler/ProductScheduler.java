package com.flashsale.productservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductScheduler {

    private static final int INACTIVITY_THRESHOLD_DAYS = 30;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${product.auto-hide.cron:0 0 2 * * ?}")
    @Transactional
    public void autoHideInactiveProducts() {
        log.info("Starting auto-hide inactive products job");

        LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVITY_THRESHOLD_DAYS);
        int pageNumber = 0;
        Page<Product> page;

        int processedCount = 0;
        int hiddenCount = 0;

        do {
            page = productRepository.findByStatusAndDeletedAtIsNull(ProductStatus.ACTIVE, Pageable.ofSize(100).withPage(pageNumber));

            for (Product product : page.getContent()) {
                processedCount++;

                List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(product.getId());
                boolean allOutOfStock = variants.stream()
                        .allMatch(v -> v.getStatus() == VariantStatus.OUT_OF_STOCK);

                if (allOutOfStock) {
                    boolean allOutOfStockLongEnough = variants.stream()
                            .allMatch(v -> v.getUpdatedAt() != null && v.getUpdatedAt().isBefore(threshold));

                    if (allOutOfStockLongEnough) {
                        product.setStatus(ProductStatus.OUT_OF_STOCK);
                        productRepository.save(product);

                        log.info("Auto-hiding product due to inactivity: productId={}, name={}, lastUpdate={}",
                                product.getId(), product.getName(),
                                variants.stream()
                                        .map(ProductVariant::getUpdatedAt)
                                        .max(LocalDateTime::compareTo)
                                        .orElse(null));

                        emitProductUpdatedEvent(product.getId());
                        hiddenCount++;
                    }
                }
            }

            pageNumber++;
        } while (page.hasNext());

        log.info("Auto-hide inactive products job complete: processed={}, hidden={}", processedCount, hiddenCount);
    }

    private void emitProductUpdatedEvent(UUID productId) {
        try {
            Map<String, Object> payload = Map.of("productId", productId);
            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.PRODUCT_UPDATED, productId.toString(), value);
        } catch (Exception e) {
            log.error("Failed to emit product.updated event for productId={}", productId, e);
        }
    }
}
