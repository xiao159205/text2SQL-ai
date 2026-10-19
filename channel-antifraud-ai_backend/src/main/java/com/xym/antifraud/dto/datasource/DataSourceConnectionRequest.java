package com.xym.antifraud.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DataSourceConnectionRequest(
        @NotBlank String databaseType,
        @NotBlank @Size(max = 255) String host,
        @Min(1) @Max(65535) int port,
        @NotBlank @Size(max = 128) String username,
        @Size(max = 512) String password,
        @Size(max = 128) String database
) {
}
