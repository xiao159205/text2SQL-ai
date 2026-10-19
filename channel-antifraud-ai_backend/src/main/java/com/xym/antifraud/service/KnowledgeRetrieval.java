package com.xym.antifraud.service;

import com.xym.antifraud.dto.knowledge.KnowledgeSearchHit;

import java.util.List;

/** 一次知识库检索的完整结果，可在意图路由、RAG 回答和 Text2SQL 之间复用。 */
public record KnowledgeRetrieval(
        Status status,
        String rewrittenQuery,
        List<KnowledgeSearchHit> hits,
        String context
) {
    private static final int MAX_ROUTING_HITS = 5;
    private static final int MAX_ROUTING_EXCERPT_CHARS = 240;

    public KnowledgeRetrieval {
        status = status == null ? Status.UNAVAILABLE : status;
        rewrittenQuery = rewrittenQuery == null ? "" : rewrittenQuery;
        hits = hits == null ? List.of() : List.copyOf(hits);
        context = context == null ? "" : context;
    }

    public static KnowledgeRetrieval disabled() {
        return new KnowledgeRetrieval(Status.DISABLED, "", List.of(), "未启用业务知识库。");
    }

    public static KnowledgeRetrieval unavailable(String question) {
        return new KnowledgeRetrieval(Status.UNAVAILABLE, question, List.of(),
                "知识库当前不可用，本次仅依据实时数据库 Schema 处理。");
    }

    public boolean hasHits() {
        return !hits.isEmpty();
    }

    /**
     * 给意图路由器的轻量证据。只提供少量命中信息，避免把完整文档再次发送给模型。
     */
    public String routingEvidence() {
        StringBuilder evidence = new StringBuilder()
                .append("检索状态: ").append(status).append('\n')
                .append("改写后的查询: ").append(singleLine(rewrittenQuery)).append('\n')
                .append("命中数量: ").append(hits.size()).append('\n');
        for (int index = 0; index < Math.min(hits.size(), MAX_ROUTING_HITS); index++) {
            KnowledgeSearchHit hit = hits.get(index);
            evidence.append('[').append(index + 1).append("] 标题=")
                    .append(singleLine(hit.title()))
                    .append("; 类型=").append(singleLine(hit.knowledgeType()))
                    .append("; 来源=").append(singleLine(hit.source()))
                    .append("; 分数=").append(hit.score())
                    .append("; 摘要=").append(excerpt(hit.content()))
                    .append('\n');
        }
        if (hits.isEmpty() && status == Status.READY) {
            evidence.append("结论: 本次没有检索到相关的已发布知识。\n");
        }
        return evidence.toString();
    }

    private static String excerpt(String value) {
        String normalized = singleLine(value);
        return normalized.length() <= MAX_ROUTING_EXCERPT_CHARS
                ? normalized : normalized.substring(0, MAX_ROUTING_EXCERPT_CHARS) + "…";
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    public enum Status {
        READY,
        DISABLED,
        UNAVAILABLE
    }
}
