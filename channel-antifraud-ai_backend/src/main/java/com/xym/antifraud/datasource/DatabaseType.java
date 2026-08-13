package com.xym.antifraud.datasource;

import java.util.Locale;

/** 前端可选择的数据源类型。 */
public enum DatabaseType {
    MYSQL,
    HIVE;

    public static DatabaseType from(String value) {
        if (value == null || value.isBlank()) {
            return MYSQL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "不支持的数据库类型: " + value + "，仅支持 mysql、hive");
        }
    }
}
