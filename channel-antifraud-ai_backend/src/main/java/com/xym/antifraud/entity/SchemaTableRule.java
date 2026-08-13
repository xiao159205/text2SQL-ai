package com.xym.antifraud.entity;

/** 数据库中的 Schema 表级配置。 */
public record SchemaTableRule(String tableName, int visible, String customComment) {
}
