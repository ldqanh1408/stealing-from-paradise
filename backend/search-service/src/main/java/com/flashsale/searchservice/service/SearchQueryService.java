package com.flashsale.searchservice.service;

import com.flashsale.searchservice.dto.SearchResponse;
import com.flashsale.searchservice.dto.SuggestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchQueryService {

    private final ElasticsearchService esService;

    public SearchResponse search(
            String q,
            String categoryId,
            Double priceMin,
            Double priceMax,
            Boolean inStock,
            Boolean isFlash,
            String sort,
            int page,
            int size
    ) {
        if (size > 40) {
            size = 40;
        }
        if (size < 1) {
            size = 20;
        }
        if (page < 0) {
            page = 0;
        }

        Boolean effectiveInStock = inStock != null ? inStock : true;

        log.info("Search: q='{}', category={}, price=[{}-{}], inStock={}, isFlash={}, sort={}, page={}, size={}",
                q, categoryId, priceMin, priceMax, effectiveInStock, isFlash, sort, page, size);

        return esService.search(q, categoryId, priceMin, priceMax, effectiveInStock, isFlash, sort, page, size);
    }

    public SuggestResponse suggest(String q, int size) {
        if (q == null || q.length() < 2) {
            return SuggestResponse.builder().suggestions(java.util.Collections.emptyList()).build();
        }
        if (size > 10) size = 10;
        if (size < 1) size = 5;

        log.debug("Suggest: q='{}', size={}", q, size);
        return esService.suggest(q, size);
    }
}
