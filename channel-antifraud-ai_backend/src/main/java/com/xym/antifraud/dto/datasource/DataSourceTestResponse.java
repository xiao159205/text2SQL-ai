package com.xym.antifraud.dto.datasource;

import java.util.List;

public record DataSourceTestResponse(List<String> databases, long elapsedMs) {
}
