package com.xym.antifraud.query;

import com.xym.antifraud.config.AntifraudProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 使用命名参数执行只读查询，并统一限制返回行数。 */
@Component
public class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AntifraudProperties properties;

    public QueryExecutor(NamedParameterJdbcTemplate jdbcTemplate, AntifraudProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<Map<String, Object>> execute(CompiledQuery query) {
        log.info("[MySQL执行开始-最终SQL] {}", query.executableSql());
        log.info("[MySQL参数化执行明细] parameterizedSql={}, parameters={}",
                query.sql(), query.parameters());
        long startedAt = System.nanoTime();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(query.sql(), query.parameters());
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (properties.logQueryResults()) {
                int previewSize = Math.min(rows.size(), properties.maxLoggedResultRows());
                log.info("[MySQL执行完成] elapsedMs={}, rowCount={}, resultPreview({})={}",
                        elapsedMs, rows.size(), previewSize, rows.subList(0, previewSize));
            } else {
                log.info("[MySQL执行完成] elapsedMs={}, rowCount={}", elapsedMs, rows.size());
            }
            return rows;
        } catch (RuntimeException exception) {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("[MySQL执行失败] elapsedMs={}, executableSql={}, "
                            + "parameterizedSql={}, parameters={}",
                    elapsedMs, query.executableSql(), query.sql(),
                    query.parameters(), exception);
            throw exception;
        }
    }
}
