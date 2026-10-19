package com.xym.antifraud.dto.chat;

import com.xym.antifraud.query.ast.SqlQueryPlan;

import java.util.List;
import java.util.Map;

/** 一次问答的完整返回，包括 SQL、表格和可选图表。 */
public record ChatResponse(
        ResponseType responseType,
        String summary,
        String sql,
        TableResult table,
        ChartResult chart,
        SqlQueryPlan queryPlan,
        List<Source> sources
) {
    public ChatResponse(String summary, String sql, TableResult table,
                        ChartResult chart, SqlQueryPlan queryPlan) {
        this(ResponseType.DATA_QUERY, summary, sql, table, chart, queryPlan, List.of());
    }

    public static ChatResponse conversational(ResponseType type, String answer, List<Source> sources) {
        return new ChatResponse(type, answer, "", new TableResult(List.of(), List.of()),
                null, null, sources == null ? List.of() : List.copyOf(sources));
    }

    public enum ResponseType {
        DATA_QUERY,
        KNOWLEDGE_QA,
        GENERAL_CHAT,
        CLARIFICATION
    }

    public record Source(String id, String title, String source, double score) { }

    public record TableResult(
            List<Column> columns,
            List<Map<String, Object>> rows
    ) {
    }

    public record Column(
            String title,
            String dataIndex
    ) {
    }

    public record ChartResult(
            String type,
            String title,
            Map<String, Object> option
    ) {
    }
}
