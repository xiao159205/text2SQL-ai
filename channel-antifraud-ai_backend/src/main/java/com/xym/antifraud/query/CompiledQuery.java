package com.xym.antifraud.query;

import java.util.Map;

/** 编译结果：参数化 SQL 用于执行，展开后的 SQL 只用于展示和排查。 */
public record CompiledQuery(
        String sql,
        Map<String, Object> parameters,
        String executableSql
) {
}
