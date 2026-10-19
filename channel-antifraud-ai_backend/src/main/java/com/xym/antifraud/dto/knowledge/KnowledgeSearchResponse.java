package com.xym.antifraud.dto.knowledge;

import java.util.List;

/** 知识管理页检索响应：保留原始 RRF 分数，并提供当前配置下的理论最高分。 */
public record KnowledgeSearchResponse(
        List<KnowledgeSearchHit> hits,
        double theoreticalMaxRrfScore
) {
    public KnowledgeSearchResponse {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
