package com.xym.antifraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Text2SQL 的范围限制、缓存和日志开关。 */
@ConfigurationProperties(prefix = "text2sql")
public record AntifraudProperties(
        List<String> allowedTables,
        int schemaCacheSeconds,
        int maxSchemaColumns,
        int maxResultRows,
        int maxConditions,
        int maxJoins,
        boolean logFullPrompt,
        boolean logModelResponse,
        boolean logQueryResults,
        int maxLoggedResultRows
) {
    public AntifraudProperties {
        allowedTables = allowedTables == null ? List.of() : List.copyOf(allowedTables);
    }
}
