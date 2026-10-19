package com.xym.antifraud.dto.knowledge;

import java.time.Instant;

public record KnowledgeFileResponse(
        String id, String fileName, String fileType, long fileSize,
        String status, int chunkCount, String database, String knowledgeType,
        String source, String errorMessage, Instant createdAt, Instant updatedAt
) {
}
