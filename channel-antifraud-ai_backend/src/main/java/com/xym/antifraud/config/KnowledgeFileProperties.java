package com.xym.antifraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.knowledge-files")
public record KnowledgeFileProperties(
        String storageDirectory,
        long maxFileBytes,
        int maxExtractedChars,
        int chunkChars,
        int chunkOverlapChars,
        int maxChunks
) {
    public KnowledgeFileProperties {
        storageDirectory = storageDirectory == null || storageDirectory.isBlank()
                ? "./data/knowledge-files" : storageDirectory;
        maxFileBytes = maxFileBytes <= 0 ? 20L * 1024 * 1024 : Math.min(maxFileBytes, 100L * 1024 * 1024);
        maxExtractedChars = maxExtractedChars <= 0 ? 2_000_000 : Math.min(maxExtractedChars, 10_000_000);
        chunkChars = chunkChars <= 0 ? 1200 : Math.min(Math.max(chunkChars, 300), 4000);
        chunkOverlapChars = chunkOverlapChars < 0 ? 150 : Math.min(chunkOverlapChars, chunkChars / 3);
        maxChunks = maxChunks <= 0 ? 500 : Math.min(maxChunks, 5000);
    }
}
