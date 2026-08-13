package com.xym.antifraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 修改 MySQL 中真实的表注释和字段注释。 */
@Service
public class MySqlSchemaCommentService {
    private static final Logger log = LoggerFactory.getLogger(MySqlSchemaCommentService.class);
    private static final Pattern COMMENT_CLAUSE = Pattern.compile(
            "\\s+COMMENT\\s+'(?:''|\\\\.|[^'])*'", Pattern.CASE_INSENSITIVE);
    private final JdbcTemplate jdbcTemplate;

    public MySqlSchemaCommentService(@Qualifier("configJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateTableComment(String tableName, String comment) {
        String tableIdentifier = quoteIdentifier(tableName);
        jdbcTemplate.execute("ALTER TABLE " + tableIdentifier
                + " COMMENT = " + quoteComment(comment));
        log.info("[MySQL表注释] 修改完成: table={}, comment={}", tableName, comment);
    }

    public void updateFieldComment(String tableName, String fieldName, String comment) {
        // MODIFY COLUMN 必须带上完整字段定义，所以先从 SHOW CREATE TABLE 中原样取出。
        String tableIdentifier = quoteIdentifier(tableName);
        String createTableSql = jdbcTemplate.queryForObject(
                "SHOW CREATE TABLE " + tableIdentifier, (rs, rowNum) -> rs.getString(2));
        String columnDefinition = findColumnDefinition(createTableSql, fieldName);
        String commentLiteral = quoteComment(comment);
        String updatedDefinition = replaceComment(columnDefinition, commentLiteral);
        jdbcTemplate.execute("ALTER TABLE " + tableIdentifier + " MODIFY COLUMN " + updatedDefinition);
        log.info("[MySQL字段注释] 修改完成: table={}, field={}, comment={}", tableName, fieldName, comment);
    }

    private String quoteComment(String comment) {
        // 交给 MySQL 自己转义注释内容，避免手工拼接字符串。
        return jdbcTemplate.queryForObject(
                "SELECT QUOTE(?)", String.class, comment == null ? "" : comment);
    }

    static String findColumnDefinition(String createTableSql, String fieldName) {
        if (createTableSql == null || createTableSql.isBlank()) {
            throw new IllegalStateException("MySQL未返回建表语句");
        }
        String fieldIdentifier = quoteIdentifier(fieldName);
        for (String line : createTableSql.split("\\R")) {
            String definition = line.trim();
            if (definition.startsWith(fieldIdentifier + " ")) {
                return definition.endsWith(",")
                        ? definition.substring(0, definition.length() - 1) : definition;
            }
        }
        throw new IllegalArgumentException("表中不存在字段: " + fieldName);
    }

    static String replaceComment(String columnDefinition, String commentLiteral) {
        Matcher matcher = COMMENT_CLAUSE.matcher(columnDefinition);
        String clause = " COMMENT " + commentLiteral;
        return matcher.find() ? matcher.replaceFirst(Matcher.quoteReplacement(clause))
                : columnDefinition + clause;
    }

    static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("表名和字段名不能为空");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }
}
