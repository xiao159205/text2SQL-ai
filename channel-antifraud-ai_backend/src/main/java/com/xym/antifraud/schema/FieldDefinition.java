package com.xym.antifraud.schema;

/** 从数据库元数据中读取出的字段定义。 */
public record FieldDefinition(
        String tableName,
        int ordinalPosition,
        String columnName,
        String mysqlType,
        String comment
) {
    public boolean isNumeric() {
        String type = mysqlType.toUpperCase();
        return type.startsWith("BIGINT") || type.startsWith("SMALLINT")
                || type.startsWith("TINYINT") || type.startsWith("MEDIUMINT")
                || type.startsWith("INT") || type.startsWith("DECIMAL")
                || type.startsWith("NUMERIC") || type.startsWith("DOUBLE")
                || type.startsWith("FLOAT") || type.startsWith("REAL");
    }

    public boolean isIntegral() {
        String type = mysqlType.toUpperCase();
        return type.startsWith("BIGINT") || type.startsWith("SMALLINT")
                || type.startsWith("TINYINT") || type.startsWith("MEDIUMINT")
                || type.startsWith("INT");
    }

    public boolean isTextual() {
        String type = mysqlType.toUpperCase();
        return type.startsWith("CHAR") || type.startsWith("VARCHAR")
                || type.startsWith("STRING") || type.contains("TEXT") || type.startsWith("ENUM")
                || type.startsWith("SET");
    }

    public boolean isTemporal() {
        String type = mysqlType.toUpperCase();
        return type.startsWith("DATE") || type.startsWith("DATETIME")
                || type.startsWith("TIMESTAMP") || type.startsWith("TIME");
    }

    public String displayName() {
        return comment == null || comment.isBlank() ? columnName : comment;
    }
}
