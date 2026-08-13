package com.xym.antifraud.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MyBatisConfigTest {

    @Test
    void loadsAllXmlMappers() throws Exception {
        SqlSessionFactory factory = new MyBatisConfig()
                .configSqlSessionFactory(mock(DataSource.class));

        assertThat(factory.getConfiguration().hasStatement(
                "com.xym.antifraud.mapper.SchemaConfigMapper.selectTableRules")).isTrue();
        assertThat(factory.getConfiguration().hasStatement(
                "com.xym.antifraud.mapper.UserMapper.selectByPhone")).isTrue();
        assertThat(factory.getConfiguration().hasStatement(
                "com.xym.antifraud.mapper.UserMapper.insert")).isTrue();

        var tableConstructorTypes = factory.getConfiguration()
                .getResultMap("com.xym.antifraud.mapper.SchemaConfigMapper.tableRuleMap")
                .getConstructorResultMappings().stream()
                .map(mapping -> mapping.getJavaType().getName())
                .toList();
        var fieldConstructorTypes = factory.getConfiguration()
                .getResultMap("com.xym.antifraud.mapper.SchemaConfigMapper.fieldRuleMap")
                .getConstructorResultMappings().stream()
                .map(mapping -> mapping.getJavaType().getName())
                .toList();

        assertThat(tableConstructorTypes)
                .containsExactly("java.lang.String", "int", "java.lang.String");
        assertThat(fieldConstructorTypes)
                .containsExactly("java.lang.String", "java.lang.String", "int", "java.lang.String");
    }
}
