package com.xym.antifraud.dto.chat;

import com.xym.antifraud.query.ast.SqlQueryPlan;

import java.util.List;
import java.util.Map;

/** 一次问答的完整返回，包括 SQL、表格和可选图表。 */
public record ChatResponse(
        String summary,
        String sql,
        TableResult table,
        ChartResult chart,
        SqlQueryPlan queryPlan
) {
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
