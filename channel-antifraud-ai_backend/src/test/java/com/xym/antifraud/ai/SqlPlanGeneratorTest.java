package com.xym.antifraud.ai;

import com.xym.antifraud.datasource.DatabaseType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlPlanGeneratorTest {

    @Test
    void selectsMysqlDialectPrompt() {
        String prompt = SqlPlanGenerator.defaultSystemPrompt(DatabaseType.MYSQL);

        assertThat(prompt)
                .contains("通用MySQL Text2SQL查询规划器")
                .contains("MySQL DATE/DATETIME/TIMESTAMP/TIME字段")
                .doesNotContain("通用HiveQL Text2SQL查询规划器");
    }

    @Test
    void selectsIndependentHiveDialectPrompt() {
        String prompt = SqlPlanGenerator.defaultSystemPrompt(DatabaseType.HIVE);

        assertThat(prompt)
                .contains("通用HiveQL Text2SQL查询规划器")
                .contains("采用HiveQL语义")
                .contains("分区字段")
                .contains("非聚合SELECT字段必须出现在groupBy中")
                .doesNotContain("通用MySQL Text2SQL查询规划器");
    }
}
