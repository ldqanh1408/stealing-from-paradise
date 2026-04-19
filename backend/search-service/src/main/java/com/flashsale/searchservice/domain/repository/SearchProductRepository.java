package com.flashsale.searchservice.domain.repository;

import com.flashsale.searchservice.domain.model.SearchProduct;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchProductRepository extends ElasticsearchRepository<SearchProduct, String> {

    /**
     * Full-text search on name + description
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name\", \"description^2\", \"tags^1.5\"]}}")
    List<SearchProduct> searchByKeyword(String keyword);

    /**
     * Filter by category
     */
    List<SearchProduct> findByCategoryId(String categoryId);

    /**
     * Filter by seller
     */
    List<SearchProduct> findBySellerId(Long sellerId);

    /**
     * Only approved products
     */
    List<SearchProduct> findByStatus(String status);

    /**
     * Flash sale items
     */
    List<SearchProduct> findByIsFlashTrue();

    /**
     * Combined search with filters
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name\", \"description\"]}}], \"filter\": [{\"term\": {\"category_id\": \"?1\"}}, {\"term\": {\"status\": \"APPROVED\"}}]}}")
    List<SearchProduct> searchByCategoryAndKeyword(String keyword, String categoryId);
}

