package com.flashsale.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.PrefixQuery;
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

import java.text.Normalizer;
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

    /**
     * Category slug prefixes whose products are hidden from public search results
     * (E2E/frontend fixtures seeded under fe- / e2e- categories). Blank entries are
     * ignored; an empty list disables hiding (e.g. for fixture-driven test envs).
     */
    @Value("${search.hidden-category-prefixes:}")
    private List<String> hiddenCategoryPrefixes;

    /**
     * Strips combining diacritical marks from Vietnamese characters so that typed
     * queries match regardless of whether the user includes diacritics.
     * <p>
     * Examples: "tài nghe" → "tai nghe", "điện thoại" → "dien thoai", "áo" → "ao".
     * Works by NFKD-decomposing the string then removing all combining marks (\p{M}).
     * The Vietnamese đ/Đ (U+0111 / U+0110) is not decomposed by NFKD so it is
     * handled explicitly.
     */
    static String foldDiacritics(String s) {
        if (s == null || s.isEmpty()) return s;
        // NFKD decomposes "à" into "a" + combining-grave; then we strip the combining marks.
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')  // đ → d
                .replace('Đ', 'D'); // Đ → D
        return normalized;
    }

    /**
     * Builds {@code must_not} prefix queries that exclude fixture documents whose
     * category lineage ({@code categorySlugPath}) OR product slug ({@code productSlug})
     * starts with a hidden prefix. Both fields are checked because reindexes can land
     * fixture products under real category slugs (e.g. {@code electronics}) while the
     * product slug itself still carries the {@code fe-}/{@code e2e-} marker.
     */
    private List<Query> hiddenCategoryExclusions() {
        if (hiddenCategoryPrefixes == null || hiddenCategoryPrefixes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Query> exclusions = new ArrayList<>();
        for (String prefix : hiddenCategoryPrefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            String value = prefix.trim();
            exclusions.add(PrefixQuery.of(p -> p.field("categorySlugPath").value(value))._toQuery());
            exclusions.add(PrefixQuery.of(p -> p.field("productSlug").value(value))._toQuery());
        }
        return exclusions;
    }

    public SearchResponse search(
            String q,
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
                // isFlash = true: include products with any discount (seed data with
                // price<originalPrice AND active flash-sale items both set hasDiscount=true).
                // This covers both intrinsically discounted seed products and flash-sale-
                // activated products where the scheduler has set hasDiscount + flashSessionId.
                filterQueries.add(TermQuery.of(t -> t.field("hasDiscount").value(true))._toQuery());
            }

            Query rootQuery;
            if (q != null && !q.isBlank()) {
                // Fold diacritics so "tài nghe" matches products indexed as "tai nghe" and vice versa.
                String normalizedQ = foldDiacritics(q);
                rootQuery = MultiMatchQuery.of(mm -> mm
                        .query(normalizedQ)
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
            for (Query exclusion : hiddenCategoryExclusions()) {
                boolBuilder.mustNot(exclusion);
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
                Double priceMaxDoc = root.getOriginalPrice() != null && root.getOriginalPrice() > root.getPrice()
                    ? root.getOriginalPrice()
                    : root.getPrice();
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
                                if (cheapestSku.getOriginalPrice() != null && cheapestSku.getOriginalPrice() > cheapestSku.getPrice()) {
                                    priceMaxDoc = cheapestSku.getOriginalPrice();
                                }
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
                        .stockAvailable("in_stock".equals(root.getStockStatus()) ? 1 : 0)
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
        if (q == null || q.isBlank()) {
            return SuggestResponse.builder().suggestions(Collections.emptyList()).build();
        }
        try {
            // Normalize diacritics so "tài nghe" → "tai nghe", matching the
            // folded / asciifolded terms in the index.
            String normalizedQ = foldDiacritics(q);

            // Build a bool query that matches the folded term against the text-analysed
            // productName field.  We also add a should clause against the keyword sub-field
            // for exact prefix matches at query-time (handy for very short input).
            Query foldedQuery = MatchQuery.of(m -> m
                    .field("productName")
                    .query(normalizedQ)
                    .fuzziness("AUTO")
            )._toQuery();

            Query prefixQuery = PrefixQuery.of(p -> p
                    .field("productName.keyword")
                    .value(normalizedQ)
            )._toQuery();

            BoolQuery.Builder suggestBool = new BoolQuery.Builder()
                    .should(foldedQuery)
                    .should(prefixQuery)
                    .minimumShouldMatch("1");
            for (Query exclusion : hiddenCategoryExclusions()) {
                suggestBool.mustNot(exclusion);
            }

            var req = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(suggestBool.build()._toQuery())
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
