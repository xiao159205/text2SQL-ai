package com.xym.antifraud.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySqlSchemaCommentServiceTest {

    @Test
    void updatesTheRealMysqlTableComment() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(eq("SELECT QUOTE(?)"), eq(String.class), any()))
                .thenReturn("'订单表'");

        new MySqlSchemaCommentService(jdbcTemplate)
                .updateTableComment("orders", "订单表");

        verify(jdbcTemplate).execute("ALTER TABLE `orders` COMMENT = '订单表'");
    }

    @Test
    void preservesFullColumnDefinitionWhileReplacingComment() {
        String createSql = """
                CREATE TABLE `metrics` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `total_coin_count` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'old comment',
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;

        String definition = MySqlSchemaCommentService.findColumnDefinition(
                createSql, "total_coin_count");
        String updated = MySqlSchemaCommentService.replaceComment(
                definition, "'投币的数量'");

        assertThat(updated).isEqualTo(
                "`total_coin_count` bigint unsigned NOT NULL DEFAULT '0' COMMENT '投币的数量'");
    }

    @Test
    void appendsCommentWhenColumnHasNone() {
        assertThat(MySqlSchemaCommentService.replaceComment(
                "`name` varchar(64) DEFAULT NULL", "'名称'"))
                .isEqualTo("`name` varchar(64) DEFAULT NULL COMMENT '名称'");
    }

    @Test
    void quotesIdentifiersContainingBackticks() {
        assertThat(MySqlSchemaCommentService.quoteIdentifier("a`b"))
                .isEqualTo("`a``b`");
    }
}
