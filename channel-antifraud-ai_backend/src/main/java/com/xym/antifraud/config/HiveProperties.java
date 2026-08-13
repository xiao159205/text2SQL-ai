package com.xym.antifraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hive JDBC 连接参数。 */
@ConfigurationProperties(prefix = "text2sql.hive")
public record HiveProperties(
        String url,
        String username,
        String password,
        String driverClassName,
        int maximumPoolSize,
        long connectionTimeout
) {
}
