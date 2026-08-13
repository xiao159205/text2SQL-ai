package com.xym.antifraud.service;

import com.xym.antifraud.report.ChartSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** 接收模型的图表选择；这里只保存选择，不接收或加工查询数据。 */
final class ChartSelectionTool {
    private static final Logger log = LoggerFactory.getLogger(ChartSelectionTool.class);

    private final AtomicReference<ChartSpec> selected = new AtomicReference<>();

    @Tool(
            name = "select_result_chart",
            description = "Select the most suitable chart for database query results, or NONE when a chart adds no value.",
            returnDirect = true
    )
    public String select(
            @ToolParam(description = "One of NONE, LINE, BAR, PIE, SCATTER")
            ChartSpec.Type type,
            @ToolParam(required = false, description = "Short chart title")
            String title,
            @ToolParam(required = false, description = "Existing result column used for categories or x-axis")
            String categoryColumn,
            @ToolParam(required = false, description = "Existing numeric result columns used as chart series")
            List<String> valueColumns
    ) {
        ChartSpec selection = new ChartSpec(type, title, categoryColumn, valueColumns);
        selected.set(selection);
        log.info("[Chart-3 function call] select_result_chart invoked, selection={}", selection);
        return "Chart selection accepted";
    }

    ChartSpec selection() {
        return selected.get();
    }
}
