package com.flashsale.searchservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.searchservice.domain.model.SearchProduct;
import com.flashsale.searchservice.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final ElasticsearchOperations elasticsearchOperations;

    @GetMapping
    public ApiResponse<SearchResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Criteria> mustCriteria = new ArrayList<>();
        mustCriteria.add(new Criteria("status").is("APPROVED"));

        if (q != null && !q.isBlank()) {
            mustCriteria.add(new Criteria("name").contains(q)
                    .or(new Criteria("description").contains(q)));
        }

        if (category != null && !category.isBlank()) {
            mustCriteria.add(new Criteria("categoryId").is(category));
        }

        Criteria composite = new Criteria();
        for (Criteria c : mustCriteria) {
            composite = composite.and(c);
        }

        CriteriaQuery query = new CriteriaQuery(composite, PageRequest.of(page, size));

        SearchHits<SearchProduct> hits = elasticsearchOperations.search(query, SearchProduct.class);

        List<SearchProduct> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        SearchResponse response = SearchResponse.builder()
                .content(content)
                .totalHits(hits.getTotalHits())
                .page(page)
                .size(size)
                .build();

        return ApiResponse.success(response);
    }
}
