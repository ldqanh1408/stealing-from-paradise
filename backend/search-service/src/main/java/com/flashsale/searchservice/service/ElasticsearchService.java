package com.flashsale.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptSource;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.InnerHits;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.searchservice.domain.model.SearchDocument;
import com.flashsale.searchservice.dto.ProductCard;
import com.flashsale.searchservice.dto.SuggestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchService {

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    @Value("${search.elasticsearch.index-name:skus}")
    private String indexName;

    @Value("${search.elasticsearch.max-result-window:10000}")
    private int maxResultWindow;

    public void createIndexIfNotExists() {
        try {
            ensureAlias(indexName);
        } catch (IOException e) {
            log.warn("Cannot check/create ES index: {}", e.getMessage());
        }
    }

    public void createIndex() throws IOException {
        createIndexAs(indexName);
    }

    public void createIndexAs(String targetIndex) throws IOException {
        esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(targetIndex)
                .settings(s -> s
                        .numberOfShards("3")
                        .numberOfReplicas("1")
                        .maxResultWindow(maxResultWindow)
                        // NOTE: ES 9.x forbids synonyms_path filters at index time
                        // (they're always treated as search-time only because the file
                        // can be reloaded). The original config tried to use vi_synonyms
                        // in the index analyzer — illegal. Simplest fix: drop the filter
                        // so the index can be created. Vietnamese synonym matching is
                        // disabled; lowercase + asciifolding still work for normalization.
                        .analysis(a -> a
                                .analyzer("vietnamese_analyzer", an -> an
                                        .custom(cu -> cu
                                                .tokenizer("standard")
                                                .filter("lowercase", "asciifolding")
                                        )
                                )
                        )
                )
                .mappings(m -> m
                        .properties("skuId", p -> p.keyword(k -> k))
                        .properties("productId", p -> p.keyword(k -> k))
                        .properties("sellerId", p -> p.long_(l -> l))
                        .properties("productName", p -> p
                                .text(t -> t
                                        .analyzer("vietnamese_analyzer")
                                        .fields("keyword", f -> f.keyword(k -> k))
                                )
                        )
                        .properties("productSlug", p -> p.keyword(k -> k))
                        .properties("productDescription", p -> p.text(t -> t.analyzer("vietnamese_analyzer")))
                        .properties("productAttributes", p -> p.object(o -> o.enabled(true)))
                        .properties("categoryId", p -> p.keyword(k -> k))
                        .properties("categorySlug", p -> p.keyword(k -> k))
                        .properties("categoryPath", p -> p.keyword(k -> k))
                        .properties("categorySlugPath", p -> p.keyword(k -> k))
                        .properties("categoryName", p -> p.keyword(k -> k))
                        .properties("variantAttributes", p -> p.object(o -> o.enabled(true)))
                        .properties("skuCode", p -> p.keyword(k -> k))
                        .properties("price", p -> p.double_(d -> d))
                        .properties("originalPrice", p -> p.double_(d -> d))
                        .properties("hasDiscount", p -> p.boolean_(b -> b))
                        .properties("flashSessionId", p -> p.keyword(k -> k))
                        .properties("stockStatus", p -> p.keyword(k -> k))
                        .properties("productStatus", p -> p.keyword(k -> k))
                        .properties("skuStatus", p -> p.keyword(k -> k))
                        .properties("isActive", p -> p.boolean_(b -> b))
                        .properties("thumbnailUrl", p -> p.keyword(k -> k.index(false)))
                        .properties("skuImageUrl", p -> p.keyword(k -> k.index(false)))
                        .properties("sellerName", p -> p
                                .text(t -> t
                                        .fields("keyword", f -> f.keyword(k -> k))
                                )
                        )
                        .properties("sortId", p -> p.integer(i -> i))
                )
        ));
        log.info("ES index '{}' created successfully", targetIndex);
    }

    public void ensureAlias(String aliasName) throws IOException {
        if (aliasExists(aliasName)) {
            return;
        }

        if (indexExists(aliasName)) {
            log.info("ES concrete index '{}' exists without alias; next reindex will migrate it to an alias-backed index",
                    aliasName);
            return;
        }

        String initialIndex = aliasName + "_v" + Instant.now().toEpochMilli();
        createIndexAs(initialIndex);
        esClient.indices().putAlias(r -> r.index(initialIndex).name(aliasName));
        log.info("Created alias '{}' -> '{}'", aliasName, initialIndex);
    }

    public com.flashsale.searchservice.dto.SearchResponse search(
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

            // ES 9.x requires inner_hits inside collapse to carry a name; the
            // response key at line ~246 ("cheapest_sku") must match this name.
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
                                SearchDocument cheapestSku = objectMapper.readValue(
                                        objectMapper.writeValueAsString(cheapestHit.source()), SearchDocument.class);
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

            return com.flashsale.searchservice.dto.SearchResponse.builder()
                    .totalResults((int) total)
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .products(cards)
                    .build();

        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage(), e);
            return com.flashsale.searchservice.dto.SearchResponse.builder()
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

    public void indexDocument(SearchDocument doc) throws IOException {
        esClient.index(i -> i
                .index(indexName)
                .id(doc.getSkuId())
                .document(doc)
        );
    }

    public void bulkIndex(List<SearchDocument> documents) throws IOException {
        bulkIndexInto(documents, indexName);
    }

    public void bulkIndexInto(List<SearchDocument> documents, String targetIndex) throws IOException {
        if (documents == null || documents.isEmpty()) return;

        List<BulkOperation> ops = documents.stream()
                .map(doc -> BulkOperation.of(op -> op
                        .index(IndexOperation.of(idx -> idx
                                .index(targetIndex)
                                .id(doc.getSkuId())
                                .document(doc)
                        ))
                ))
                .toList();

        BulkRequest bulkReq = BulkRequest.of(b -> b.operations(ops));
        BulkResponse bulkResp = esClient.bulk(bulkReq);

        if (bulkResp.errors()) {
            bulkResp.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("Bulk index error for id {}: {}",
                            item.id(), item.error().reason()));
        }
    }

    public void deleteByProductId(String productId) throws IOException {
        esClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                .index(indexName)
                .query(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
        ));
    }

    public void setActiveByProductId(String productId, boolean active) throws IOException {
        Script script = buildSimpleScript("ctx._source.isActive = " + active);
        esClient.updateByQuery(u -> u
                .index(indexName)
                .query(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
                .script(script)
        );
    }

    public void updateByProductId(String productId, Map<String, Object> fields) throws IOException {
        Script script = buildUpdateScript(fields);
        esClient.updateByQuery(u -> u
                .index(indexName)
                .query(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
                .script(script)
        );
    }

    public void updateByCategoryId(String categoryId, Map<String, Object> fields) throws IOException {
        Script script = buildUpdateScript(fields);
        esClient.updateByQuery(u -> u
                .index(indexName)
                .query(TermQuery.of(t -> t.field("categoryId").value(categoryId))._toQuery())
                .script(script)
        );
    }

    public void partialUpdate(String skuId, Map<String, Object> fields) throws IOException {
        UpdateRequest<SearchDocument, Map<String, Object>> req = UpdateRequest.of(r -> r
                .index(indexName)
                .id(skuId)
                .doc(fields)
        );
        esClient.update(req, SearchDocument.class);
    }

    public void bulkPartialUpdateFlashSaleActivate(List<Map<String, Object>> items) throws IOException {
        List<BulkOperation> ops = items.stream()
                .map(item -> {
                    String scriptSource = "ctx._source.price = params.flashPrice; ctx._source.originalPrice = params.originalPrice; ctx._source.hasDiscount = params.hasDiscount; ctx._source.flashSessionId = params.sessionId";
                    Script script = Script.of(s -> s
                            .lang("painless")
                            .source(ScriptSource.of(ss -> ss.scriptString(scriptSource)))
                            .params(toJsonParams(item))
                    );
                    return BulkOperation.of(op -> op
                            .update(UpdateOperation.of(u -> u
                                    .index(indexName)
                                    .id((String) item.get("skuId"))
                                    .action(a -> a.script(script))
                            ))
                    );
                })
                .toList();

        if (!ops.isEmpty()) {
            BulkResponse resp = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (resp.errors()) {
                log.error("Bulk flash sale update errors: {}", resp.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.joining("; ")));
            }
        }
    }

    public void bulkPartialUpdateFlashSaleDeactivate(List<String> skuIds, Integer sessionId) throws IOException {
        List<BulkOperation> ops = skuIds.stream()
                .map(skuId -> {
                    Script script = Script.of(s -> s
                            .lang("painless")
                            .source(ScriptSource.of(ss -> ss.scriptString(
                                    "ctx._source.price = ctx._source.originalPrice; ctx._source.hasDiscount = false; ctx._source.flashSessionId = null"
                            )))
                    );
                    return BulkOperation.of(op -> op
                            .update(UpdateOperation.of(u -> u
                                    .index(indexName)
                                    .id(skuId)
                                    .action(a -> a.script(script))
                            ))
                    );
                })
                .toList();

        if (!ops.isEmpty()) {
            BulkResponse resp = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (resp.errors()) {
                log.error("Bulk flash sale deactivate errors: {}", resp.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.joining("; ")));
            }
        }
    }

    public String getCurrentIndexForAlias(String aliasName) throws IOException {
        if (!aliasExists(aliasName)) {
            return null;
        }
        var aliasResp = esClient.indices().getAlias(GetAliasRequest.of(g -> g.name(aliasName)));
        if (aliasResp.aliases().isEmpty()) {
            return null;
        }
        return aliasResp.aliases().keySet().iterator().next();
    }

    public String getCurrentIndexForAliasOrConcreteIndex(String aliasName) throws IOException {
        String currentAliasTarget = getCurrentIndexForAlias(aliasName);
        if (currentAliasTarget != null) {
            return currentAliasTarget;
        }
        return indexExists(aliasName) ? aliasName : null;
    }

    public void swapAlias(String aliasName, String oldIndex, String newIndex) throws IOException {
        boolean concreteIndexBlocksAlias = oldIndex != null
                && oldIndex.equals(aliasName)
                && indexExists(aliasName)
                && !aliasExists(aliasName);
        if (concreteIndexBlocksAlias) {
            esClient.indices().delete(d -> d.index(aliasName));
            oldIndex = null;
            log.info("Deleted legacy concrete index '{}' before creating alias", aliasName);
        }

        String previousIndex = oldIndex;
        esClient.indices().updateAliases(r -> {
            if (previousIndex != null && !previousIndex.isBlank()) {
                r.actions(a -> a.remove(rem -> rem.index(previousIndex).alias(aliasName)));
            }
            return r.actions(a -> a.add(add -> add.index(newIndex).alias(aliasName)));
        });
        log.info("Alias swap: '{}' -> '{}'", aliasName, newIndex);
    }

    private boolean indexExists(String index) throws IOException {
        return esClient.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
    }

    private boolean aliasExists(String aliasName) throws IOException {
        return esClient.indices().existsAlias(e -> e.name(aliasName)).value();
    }

    private Script buildSimpleScript(String sourceCode) {
        return Script.of(s -> s
                .lang("painless")
                .source(ScriptSource.of(ss -> ss.scriptString(sourceCode)))
        );
    }

    private Script buildUpdateScript(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        for (String key : fields.keySet()) {
            sb.append("ctx._source['").append(key).append("'] = params.'").append(key).append("'; ");
        }
        return Script.of(s -> s
                .lang("painless")
                .source(ScriptSource.of(ss -> ss.scriptString(sb.toString())))
                .params(toJsonParams(fields))
        );
    }

    private Map<String, JsonData> toJsonParams(Map<String, Object> map) {
        Map<String, JsonData> result = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() != null) {
                result.put(e.getKey(), JsonData.of(e.getValue()));
            } else {
                result.put(e.getKey(), JsonData.of((String) null));
            }
        }
        return result;
    }
}
