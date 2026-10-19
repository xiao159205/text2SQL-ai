package com.xym.antifraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.knowledge")
public record KnowledgeProperties(
        boolean enabled,
        String baseUrl,
        String indexName,
        boolean initializeIndex,
        int topK,
        int maxContextChars,
        int candidateK,
        double bm25Weight,
        double vectorWeight,
        int rrfK,
        boolean queryRewriteEnabled,
        boolean rerankEnabled,
        boolean embeddingEnabled,
        String embeddingBaseUrl,
        String embeddingApiKey,
        String embeddingModel,
        int embeddingDimensions
) {
    public KnowledgeProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://hadoop105:9200" : baseUrl;
        indexName = indexName == null || indexName.isBlank()
                ? "channel-antifraud-knowledge" : indexName;
        if (!indexName.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("知识库 ES 索引名称不合法: " + indexName);
        }
        topK = topK <= 0 ? 8 : Math.min(topK, 10);
        maxContextChars = maxContextChars <= 0 ? 8000 : Math.min(maxContextChars, 30000);
        candidateK = candidateK <= 0 ? 50 : Math.min(Math.max(candidateK, 30), 100);
        bm25Weight = bm25Weight < 0 ? 0.45 : bm25Weight;
        vectorWeight = vectorWeight < 0 ? 0.55 : vectorWeight;
        if (bm25Weight + vectorWeight <= 0) {
            bm25Weight = 0.45;
            vectorWeight = 0.55;
        }
        rrfK = rrfK <= 0 ? 60 : rrfK;
        embeddingBaseUrl = embeddingBaseUrl == null || embeddingBaseUrl.isBlank()
                ? "https://api.siliconflow.cn/v1" : embeddingBaseUrl;
        embeddingApiKey = embeddingApiKey == null ? "" : embeddingApiKey;
        embeddingModel = embeddingModel == null || embeddingModel.isBlank()
                ? "Qwen/Qwen3-Embedding-8B" : embeddingModel;
        embeddingDimensions = embeddingDimensions <= 0 ? 1536 : embeddingDimensions;
    }
}
