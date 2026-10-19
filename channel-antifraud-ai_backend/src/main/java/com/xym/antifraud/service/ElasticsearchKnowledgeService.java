package com.xym.antifraud.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xym.antifraud.config.KnowledgeProperties;
import com.xym.antifraud.dto.knowledge.KnowledgeDocumentRequest;
import com.xym.antifraud.dto.knowledge.KnowledgeDocumentResponse;
import com.xym.antifraud.dto.knowledge.KnowledgeSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Elasticsearch 7.6 compatible BM25 + dense_vector hybrid retriever. */
@Service
public class ElasticsearchKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKnowledgeService.class);
    private static final String PUBLISHED = "PUBLISHED";
    private final RestClient restClient;
    private final KnowledgeProperties properties;
    private final KnowledgeEmbeddingService embeddingService;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    @Autowired
    public ElasticsearchKnowledgeService(@Qualifier("knowledgeRestClient") RestClient restClient,
                                         KnowledgeProperties properties,
                                         KnowledgeEmbeddingService embeddingService) {
        this.restClient = restClient;
        this.properties = properties;
        this.embeddingService = embeddingService;
    }

    ElasticsearchKnowledgeService(RestClient restClient, KnowledgeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.embeddingService = null;
    }

    public KnowledgeDocumentResponse save(KnowledgeDocumentRequest request) {
        requireEnabled();
        ensureIndex();
        String id = normalizeId(request.id());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", request.title().trim());
        document.put("content", request.content().trim());
        document.put("knowledgeType", normalizeUpper(request.knowledgeType(), "GENERAL"));
        document.put("database", normalizeLower(request.database(), "all"));
        document.put("tableNames", normalizeList(request.tableNames(), true));
        document.put("keywords", normalizeList(request.keywords(), false));
        document.put("source", blankToDefault(request.source(), "manual"));
        document.put("status", PUBLISHED);
        document.put("updatedAt", Instant.now().toString());
        embed(embeddingText(request.title(), request.content(), request.keywords()))
                .ifPresent(value -> document.put("embedding", value));
        JsonNode response = restClient.put().uri("/{index}/_doc/{id}?refresh=wait_for", properties.indexName(), id)
                .body(document).retrieve().body(JsonNode.class);
        return new KnowledgeDocumentResponse(id,
                response == null ? "unknown" : response.path("result").asText("unknown"));
    }

    /** Recalls 30-100 candidates and fuses BM25/vector ranks with weighted RRF. */
    public List<KnowledgeSearchHit> search(String question, String database) {
        requireEnabled();
        ensureIndex();
        String normalizedDatabase = normalizeLower(database, "all");
        Map<String, Object> bm25Query = Map.of("size", properties.candidateK(),
                "_source", Map.of("excludes", "embedding"), "query", Map.of("bool", Map.of(
                "filter", publishedDatabaseFilter(normalizedDatabase),
                "must", List.of(Map.of("multi_match", Map.of("query", question,
                        "fields", List.of("title^4", "keywords^3", "tableNames^2", "content"),
                        "type", "best_fields"))))));
        JsonNode bm25Response = restClient.post().uri("/{index}/_search", properties.indexName())
                .body(bm25Query).retrieve().body(JsonNode.class);
        List<KnowledgeSearchHit> bm25Hits = parseHits(bm25Response);

        Optional<List<Double>> queryVector;
        try {
            queryVector = embed(question);
        } catch (RuntimeException exception) {
            log.warn("[RAG vector fallback] embedding failed; BM25 only: {}", exception.getMessage());
            queryVector = Optional.empty();
        }
        if (queryVector.isEmpty()) return reciprocalRankFusion(bm25Hits, List.of());

        List<Map<String, Object>> vectorFilters = new ArrayList<>(publishedDatabaseFilter(normalizedDatabase));
        vectorFilters.add(Map.of("exists", Map.of("field", "embedding")));
        Map<String, Object> vectorQuery = Map.of("size", properties.candidateK(),
                "_source", Map.of("excludes", "embedding"), "query", Map.of("script_score", Map.of(
                "query", Map.of("bool", Map.of("filter", vectorFilters)),
                "script", Map.of("source", "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
                        "params", Map.of("query_vector", queryVector.get())))));
        JsonNode vectorResponse = restClient.post().uri("/{index}/_search", properties.indexName())
                .body(vectorQuery).retrieve().body(JsonNode.class);
        return reciprocalRankFusion(bm25Hits, parseHits(vectorResponse));
    }

    /** 两路结果都排第一时的理论最高 RRF 分数，供管理页面归一化展示。 */
    public double theoreticalMaxRrfScore() {
        return (properties.bm25Weight() + properties.vectorWeight())
                / (properties.rrfK() + 1.0);
    }

    public void delete(String id) {
        requireEnabled();
        ensureIndex();
        restClient.delete().uri("/{index}/_doc/{id}?refresh=wait_for", properties.indexName(), normalizeId(id))
                .retrieve().toBodilessEntity();
    }

    private List<KnowledgeSearchHit> parseHits(JsonNode response) {
        if (response == null) return List.of();
        List<KnowledgeSearchHit> results = new ArrayList<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            JsonNode source = hit.path("_source");
            results.add(new KnowledgeSearchHit(hit.path("_id").asText(), hit.path("_score").asDouble(),
                    source.path("title").asText(), source.path("content").asText(),
                    source.path("knowledgeType").asText(), source.path("database").asText(),
                    textList(source.path("tableNames")), textList(source.path("keywords")),
                    source.path("source").asText()));
        }
        return List.copyOf(results);
    }

    void ensureIndex() {
        if (indexReady.get() || !properties.initializeIndex()) return;
        synchronized (indexReady) {
            if (indexReady.get()) return;
            boolean exists = restClient.head().uri("/{index}", properties.indexName())
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
            if (!exists) {
                Map<String, Object> textField = Map.of("type", "text", "analyzer", "standard");
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("title", textField); fields.put("content", textField);
                fields.put("knowledgeType", Map.of("type", "keyword"));
                fields.put("database", Map.of("type", "keyword"));
                fields.put("tableNames", Map.of("type", "keyword")); fields.put("keywords", textField);
                fields.put("source", Map.of("type", "keyword")); fields.put("status", Map.of("type", "keyword"));
                fields.put("updatedAt", Map.of("type", "date"));
                fields.put("embedding", Map.of("type", "dense_vector", "dims", properties.embeddingDimensions()));
                fields.put("fileId", Map.of("type", "keyword"));
                fields.put("fileName", Map.of("type", "keyword"));
                fields.put("chunkIndex", Map.of("type", "integer"));
                fields.put("chunkCount", Map.of("type", "integer"));
                restClient.put().uri("/{index}", properties.indexName())
                        .body(Map.of("mappings", Map.of("properties", fields))).retrieve()
                        .onStatus(HttpStatusCode::isError, (request, response) -> {
                            throw new IllegalStateException("创建知识库索引失败，HTTP " + response.getStatusCode().value());
                        }).toBodilessEntity();
                log.info("[Knowledge index created] index={}", properties.indexName());
            } else if (properties.embeddingEnabled()) {
                // An index created by an older BM25-only version has no vector mapping yet.
                Map<String, Object> additions = new LinkedHashMap<>();
                additions.put("embedding", Map.of("type", "dense_vector", "dims", properties.embeddingDimensions()));
                additions.put("fileId", Map.of("type", "keyword"));
                additions.put("fileName", Map.of("type", "keyword"));
                additions.put("chunkIndex", Map.of("type", "integer"));
                additions.put("chunkCount", Map.of("type", "integer"));
                restClient.put().uri("/{index}/_mapping", properties.indexName())
                        .body(Map.of("properties", additions))
                        .retrieve().toBodilessEntity();
            }
            indexReady.set(true);
        }
    }

    private List<KnowledgeSearchHit> reciprocalRankFusion(List<KnowledgeSearchHit> bm25,
                                                          List<KnowledgeSearchHit> vector) {
        Map<String, KnowledgeSearchHit> documents = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        addRanks(bm25, properties.bm25Weight(), documents, scores);
        addRanks(vector, properties.vectorWeight(), documents, scores);
        return documents.values().stream().map(hit -> withScore(hit, scores.getOrDefault(hit.id(), 0.0)))
                .sorted(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed())
                .limit(properties.candidateK()).toList();
    }

    private void addRanks(List<KnowledgeSearchHit> hits, double weight,
                          Map<String, KnowledgeSearchHit> documents, Map<String, Double> scores) {
        for (int rank = 0; rank < hits.size(); rank++) {
            KnowledgeSearchHit hit = hits.get(rank);
            documents.putIfAbsent(hit.id(), hit);
            scores.merge(hit.id(), weight / (properties.rrfK() + rank + 1.0), Double::sum);
        }
    }

    private static KnowledgeSearchHit withScore(KnowledgeSearchHit hit, double score) {
        return new KnowledgeSearchHit(hit.id(), score, hit.title(), hit.content(), hit.knowledgeType(),
                hit.database(), hit.tableNames(), hit.keywords(), hit.source());
    }

    private List<Map<String, Object>> publishedDatabaseFilter(String database) {
        if ("all".equals(database)) {
            return List.of(Map.of("term", Map.of("status", PUBLISHED)));
        }
        return List.of(Map.of("term", Map.of("status", PUBLISHED)),
                Map.of("terms", Map.of("database", List.of(database, "all"))));
    }

    private Optional<List<Double>> embed(String text) {
        return embeddingService == null ? Optional.empty() : embeddingService.embed(text);
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new IllegalStateException("知识库未启用，请设置 app.knowledge.enabled=true");
    }

    private static String embeddingText(String title, String content, List<String> keywords) {
        return title + "\n" + (keywords == null ? "" : String.join(" ", keywords)) + "\n" + content;
    }

    private static String normalizeId(String id) {
        String result = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        if (!result.matches("[A-Za-z0-9._-]{1,200}"))
            throw new IllegalArgumentException("知识ID只能包含字母、数字、点、下划线和横线");
        return result;
    }
    private static String normalizeUpper(String value, String defaultValue) {
        return blankToDefault(value, defaultValue).toUpperCase(Locale.ROOT);
    }
    private static String normalizeLower(String value, String defaultValue) {
        return blankToDefault(value, defaultValue).toLowerCase(Locale.ROOT);
    }
    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
    private static List<String> normalizeList(List<String> values, boolean lowerCase) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim)
                .map(value -> lowerCase ? value.toLowerCase(Locale.ROOT) : value).distinct().toList();
    }
    private static List<String> textList(JsonNode array) {
        if (!array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }
}
