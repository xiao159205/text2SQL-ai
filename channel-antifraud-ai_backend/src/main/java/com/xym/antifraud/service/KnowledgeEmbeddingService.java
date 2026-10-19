package com.xym.antifraud.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xym.antifraud.config.KnowledgeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KnowledgeEmbeddingService {

    private final RestClient restClient;
    private final KnowledgeProperties properties;

    public KnowledgeEmbeddingService(@Qualifier("embeddingRestClient") RestClient restClient,
                                     KnowledgeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Optional<List<Double>> embed(String text) {
        Optional<List<List<Double>>> embeddings = embedAll(List.of(text));
        return embeddings.map(values -> values.get(0));
    }

    public Optional<List<List<Double>>> embedAll(List<String> texts) {
        if (!properties.embeddingEnabled()) {
            return Optional.empty();
        }
        if (properties.embeddingApiKey().isBlank()) {
            throw new IllegalStateException("已启用向量检索，但未配置 EMBEDDING_API_KEY");
        }
        if (texts == null || texts.isEmpty()) return Optional.of(List.of());
        JsonNode response = restClient.post()
                .uri("/embeddings")
                .body(Map.of(
                        "model", properties.embeddingModel(),
                        "input", texts.size() == 1 ? texts.get(0) : texts,
                        "dimensions", properties.embeddingDimensions(),
                        "encoding_format", "float"))
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.size() != texts.size()) {
            throw new IllegalStateException("Embedding 服务返回的向量数量无效");
        }
        List<List<Double>> result = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) result.add(null);
        for (JsonNode item : data) {
            JsonNode values = item.path("embedding");
            int index = item.path("index").asInt(result.indexOf(null));
            if (!values.isArray() || values.size() != properties.embeddingDimensions()
                    || index < 0 || index >= result.size()) {
                throw new IllegalStateException("Embedding 服务返回了无效向量");
            }
            List<Double> embedding = new ArrayList<>(values.size());
            values.forEach(value -> embedding.add(value.asDouble()));
            result.set(index, List.copyOf(embedding));
        }
        if (result.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalStateException("Embedding 服务返回的向量索引不完整");
        }
        return Optional.of(List.copyOf(result));
    }
}
