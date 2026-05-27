package com.flashsale.searchservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.searchservice.domain.model.SearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${search.product-service.base-url:http://localhost:8080}")
    private String productServiceUrl;

    public List<SearchDocument> fetchSkuDocuments(String productId) {
        try {
            Object response = webClient.get()
                    .uri(productServiceUrl + "/v1/internal/products/{productId}/sku-documents", productId)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();

            return parseSkuDocuments(response, productId);
        } catch (Exception e) {
            log.error("Failed to fetch SKU documents for product {}: {}", productId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<SearchDocument> fetchAllActiveProducts() {
        try {
            List<SearchDocument> allDocuments = new ArrayList<>();
            int page = 0;
            int size = 100;
            boolean hasMore = true;

            while (hasMore) {
                int currentPage = page;
                Object response = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(productServiceUrl + "/v1/internal/products/active")
                                .queryParam("page", currentPage)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .bodyToMono(Object.class)
                        .block();

                List<SearchDocument> docs = parseActiveProductsResponse(response);
                if (docs == null || docs.isEmpty()) {
                    hasMore = false;
                } else {
                    allDocuments.addAll(docs);
                    page++;
                    if (docs.size() < size) {
                        hasMore = false;
                    }
                }
            }

            log.info("Fetched {} total SKU documents from Product Service", allDocuments.size());
            return allDocuments;
        } catch (Exception e) {
            log.error("Failed to fetch all active products: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public Map<String, Object> fetchProductForUpdate(String productId) {
        try {
            Object response = webClient.get()
                    .uri(productServiceUrl + "/v1/internal/products/{productId}/search-fields", productId)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();

            return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to fetch product fields for {}: {}", productId, e.getMessage());
            return new HashMap<>();
        }
    }

    public Map<String, Object> fetchCategoryForUpdate(String categoryId) {
        try {
            Object response = webClient.get()
                    .uri(productServiceUrl + "/v1/internal/categories/{categoryId}/search-fields", categoryId)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();

            return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to fetch category fields for {}: {}", categoryId, e.getMessage());
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchDocument> parseSkuDocuments(Object response, String productId) {
        try {
            if (response == null) return new ArrayList<>();
            List<?> content = (List<?>) response;
            List<SearchDocument> docs = new ArrayList<>();
            for (Object item : content) {
                SearchDocument doc = objectMapper.convertValue(item, SearchDocument.class);
                doc.setProductId(productId);
                docs.add(doc);
            }
            return docs;
        } catch (Exception e) {
            log.error("Failed to parse SKU documents for {}: {}", productId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchDocument> parseActiveProductsResponse(Object response) {
        try {
            if (response == null) return new ArrayList<>();
            Map<String, Object> respMap = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
            Object contentObj = respMap.get("content");
            if (contentObj == null) return new ArrayList<>();

            List<?> content = (List<?>) contentObj;
            List<SearchDocument> docs = new ArrayList<>();
            for (Object item : content) {
                SearchDocument doc = objectMapper.convertValue(item, SearchDocument.class);
                docs.add(doc);
            }
            return docs;
        } catch (Exception e) {
            log.error("Failed to parse active products response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
