package com.xym.antifraud.config;

import com.zaxxer.hikari.HikariDataSource;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.datasource.UserRoutingDataSource;
import com.xym.antifraud.service.UserDataSourceConnectionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource mysqlDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * Schema 黑白名单/注释配置专用的可写数据源。
     * 业务连接池（spring.datasource.hikari.read-only=true）是只读的，配置写入必须走独立连接池，
     * 且不参与下面的 routing 数据源，避免受 DatabaseContext 线程上下文影响。
     */
    @Bean(name = "configDataSource")
    public HikariDataSource configDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        dataSource.setPoolName("ConfigPool");
        dataSource.setReadOnly(false);
        dataSource.setMaximumPoolSize(2);
        return dataSource;
    }

    @Bean(name = "configJdbcTemplate")
    public JdbcTemplate configJdbcTemplate(@Qualifier("configDataSource") DataSource configDataSource) {
        return new JdbcTemplate(configDataSource);
    }

    @Bean(name = "hiveDataSource")
    public HikariDataSource hiveDataSource(HiveProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("HivePool");
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        dataSource.setDriverClassName(properties.driverClassName());
        dataSource.setMaximumPoolSize(properties.maximumPoolSize());
        dataSource.setConnectionTimeout(properties.connectionTimeout());
        return dataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("mysqlDataSource") DataSource mysqlDataSource,
            @Qualifier("hiveDataSource") DataSource hiveDataSource,
            UserDataSourceConnectionService connectionService) {
        return new UserRoutingDataSource(Map.of(
                DatabaseType.MYSQL, mysqlDataSource,
                DatabaseType.HIVE, hiveDataSource), connectionService);
    }

    /**
     * 主业务 JdbcTemplate，必须显式绑定动态路由数据源。
     * 项目已经声明了 configJdbcTemplate，Spring Boot 不会再自动创建默认模板；
     * 若缺少此 Bean，SchemaCatalog 会误用固定连接 MySQL 的 configJdbcTemplate。
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
