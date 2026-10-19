package com.xym.antifraud.dto.knowledge;

import java.util.List;

public record KnowledgeSearchHit(
        String id,
        double score,
        String title,
        String content,
        String knowledgeType,
        String database,
        List<String> tableNames,
        List<String> keywords,
        String source
) {
}
