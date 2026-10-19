package com.xym.antifraud.dto.datasource;

public record DataSourceConnectionResponse(
        String id,
        String databaseType,
        String host,
        int port,
        String database,
        String label
) {
}
