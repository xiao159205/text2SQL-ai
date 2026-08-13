package com.xym.antifraud.entity;

/** 数据库中的 Schema 字段级配置。 */
public record SchemaFieldRule(String tableName, String fieldName, int visible, String customComment) {
}
