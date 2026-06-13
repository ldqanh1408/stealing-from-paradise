package com.flashsale.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.InnerHits;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.searchservice.domain.model.SearchDocument;
import com.flashsale.searchservice.dto.ProductCard;
import com.flashsale.searchservice.dto.SearchResponse;
import com.flashsale.searchservice.dto.SuggestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsSearcher {

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    @Value("${search.elasticsearch.index-name:skus}")
    private String indexName;

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
        try {
            List<Query> filterQueries = new ArrayList<>();
            filterQueries.add(TermQuery.of(t -> t.field("isActive").value(true))._toQuery());

            if (categoryId != null && !categoryId.isBlank()) {
                Query categoryById = TermQuery.of(t -> t.field("categoryId").value(categoryId))._toQuery();
                Query categoryBySlug = TermQuery.of(t -> t.field("categorySlugPath").value(categoryId))._toQuery();
                filterQueries.add(BoolQuery.of(b -> b.should(categoryById).should(categoryBySlug).minimumShouldMatch("1"))._toQuery());
            }
            if (priceMin != null) {
                filterQueries.add(RangeQuery.of(r -> r.number(n -> n.field("price").gte(priceMin)))._toQuery());
            }
            if (priceMax != null) {
                filterQueries.add(RangeQuery.of(r -> r.number(n -> n.field("price").lte(priceMax)))._toQuery());
            }
            if (inStock != null && inStock) {
                filterQueries.add(TermQuery.of(t -> t.field("stockStatus").value("in_stock"))._toQuery());
            }
            if (isFlash != null && isFlash) {
                filterQueries.add(ExistsQuery.of(e -> e.field("flashSessionId"))._toQuery());
            }

            Query rootQuery;
            if (q != null && !q.isBlank()) {
                rootQuery = MultiMatchQuery.of(mm -> mm
                        .query(q)
                        .fields("productName^3", "productDescription", "productAttributes.*")
                        .fuzziness("AUTO")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                )._toQuery();
            } else {
                rootQuery = MatchAllQuery.of(m -> m)._toQuery();
            }

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder().must(rootQuery);
            for (Query fq : filterQueries) {
                boolBuilder.filter(fq);
            }

            int from = page * size;

            InnerHits cheapestSkuInnerHits = InnerHits.of(ih -> ih
                    .name("cheapest_sku")
                    .size(1)
                    .sort(s2 -> s2.field(f2 -> f2.field("price").order(SortOrder.Asc)))
            );

            SearchRequest.Builder reqBuilder = new SearchRequest.Builder()
                    .index(indexName)
                    .query(boolBuilder.build()._toQuery())
                    .from(from)
                    .size(size)
                    .collapse(FieldCollapse.of(c -> c
                            .field("productId")
                            .innerHits(Collections.singletonList(cheapestSkuInnerHits))
                    ))
                    .trackTotalHits(th -> th.count(10000));

            if ("price_asc".equals(sort)) {
                reqBuilder
                        .sort(s2 -> s2.field(f2 -> f2.field("price").order(SortOrder.Asc)))
                        .sort(s2 -> s2.field(f2 -> f2.field("sortId").order(SortOrder.Asc)));
            } else if ("price_desc".equals(sort)) {
                reqBuilder
                        .sort(s2 -> s2.field(f2 -> f2.field("price").order(SortOrder.Desc)))
                        .sort(s2 -> s2.field(f2 -> f2.field("sortId").order(SortOrder.Asc)));
            } else {
                reqBuilder
                        .sort(s2 -> s2.score(sc -> sc.order(SortOrder.Desc)))
                        .sort(s2 -> s2.field(f2 -> f2.field("sortId").order(SortOrder.Asc)));
            }

            var resp = esClient.search(reqBuilder.build(), SearchDocument.class);

            List<ProductCard> cards = new ArrayList<>();
            for (Hit<SearchDocument> hit : resp.hits().hits()) {
                SearchDocument root = hit.source();
                if (root == null) continue;

                List<String> images = new ArrayList<>();
                if (root.getThumbnailUrl() != null) {
                    images.add(root.getThumbnailUrl());
                }

                Double priceMinDoc = root.getPrice();
                Double priceMaxDoc = root.getPrice();
                List<String> finalImages = images;

                if (hit.innerHits() != null && hit.innerHits().containsKey("cheapest_sku")) {
                    var cheapestHits = hit.innerHits().get("cheapest_sku");
                    if (cheapestHits != null && !cheapestHits.hits().hits().isEmpty()) {
                        Hit<?> cheapestHit = cheapestHits.hits().hits().get(0);
                        if (cheapestHit.source() != null) {
                            try {
                                SearchDocument cheapestSku;
                                if (cheapestHit.source() instanceof JsonData jsonData) {
                                    cheapestSku = jsonData.to(SearchDocument.class);
                                } else {
                                    cheapestSku = objectMapper.readValue(
                                            objectMapper.writeValueAsString(cheapestHit.source()), SearchDocument.class);
                                }
                                priceMinDoc = cheapestSku.getPrice();
                                if (cheapestSku.getThumbnailUrl() != null) {
                                    List<String> newImages = new ArrayList<>();
                                    newImages.add(cheapestSku.getThumbnailUrl());
                                    finalImages = newImages;
                                }
                            } catch (Exception ignored) {
                                priceMinDoc = root.getPrice();
                            }
                        }
                    }
                }

                ProductCard card = ProductCard.builder()
                        .productId(root.getProductId())
                        .name(root.getProductName())
                        .sellerId(root.getSellerId())
                        .categoryId(root.getCategoryId())
                        .categoryName(root.getCategoryName())
                        .sellerName(root.getSellerName())
                        .priceMin(priceMinDoc)
                        .priceMax(priceMaxDoc)
                        .images(finalImages)
                        .isFlash(root.getFlashSessionId() != null)
                        .thumbnailUrl(root.getThumbnailUrl())
                        .build();
                cards.add(card);
            }

            long total = resp.hits().total() != null ? resp.hits().total().value() : 0;
            int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;

            return SearchResponse.builder()
                    .totalResults((int) total)
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .products(cards)
                    .build();

        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage(), e);
            return SearchResponse.builder()
                    .totalResults(0)
                    .page(page)
                    .size(size)
                    .totalPages(0)
                    .products(Collections.emptyList())
                    .build();
        }
    }

    public SuggestResponse suggest(String q, int size) {
        try {
            var req = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(MultiMatchQuery.of(mm -> mm
                            .query(q)
                            .fields("productName^3")
                            .fuzziness("AUTO")
                    )._toQuery())
                    .size(0)
                    .aggregations("product_names", a -> a
                            .terms(t -> t.field("productName.keyword").size(size))
                    )
            );

            var resp = esClient.search(req, SearchDocument.class);

            List<String> suggestions = new ArrayList<>();
            if (resp.aggregations() != null && resp.aggregations().containsKey("product_names")) {
                var termsAgg = resp.aggregations().get("product_names").sterms();
                for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                    suggestions.add(bucket.key().stringValue());
                }
            }

            return SuggestResponse.builder().suggestions(suggestions).build();

        } catch (Exception e) {
            log.error("ES suggest failed: {}", e.getMessage(), e);
            return SuggestResponse.builder().suggestions(Collections.emptyList()).build();
        }
    }
}
