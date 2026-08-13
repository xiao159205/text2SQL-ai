package com.xym.antifraud.report;

import java.util.List;

/** 大模型返回的图表选择，不包含任何真实数据。 */
public record ChartSpec(
        Type type,
        String title,
        String categoryColumn,
        List<String> valueColumns
) {
    public ChartSpec {
        type = type == null ? Type.NONE : type;
        title = title == null ? "" : title.trim();
        categoryColumn = categoryColumn == null ? "" : categoryColumn.trim();
        valueColumns = valueColumns == null ? List.of() : List.copyOf(valueColumns);
    }

    public enum Type {
        NONE,
        LINE,
        BAR,
        PIE,
        SCATTER
    }
}
