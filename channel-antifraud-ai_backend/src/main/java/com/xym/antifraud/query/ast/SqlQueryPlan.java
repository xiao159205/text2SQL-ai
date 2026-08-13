package com.xym.antifraud.query.ast;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 大模型输出的查询计划，也是后端允许编译的 SQL 结构边界。 */
public record SqlQueryPlan(
        @JsonPropertyDescription("READY表示可以查询；NEED_CLARIFICATION表示需要用户补充") PlanStatus status,
        @JsonPropertyDescription("需要澄清时的问题；READY时为空字符串") String clarification,
        @JsonPropertyDescription("固定为SELECT") StatementType statementType,
        @JsonPropertyDescription("主表及别名") TableRef from,
        @JsonPropertyDescription("关联表；不需要时为空数组") List<JoinItem> joins,
        @JsonPropertyDescription("是否SELECT DISTINCT") boolean distinct,
        @JsonPropertyDescription("SELECT字段和聚合表达式") List<SelectItem> select,
        @JsonPropertyDescription("WHERE条件组") WhereGroup where,
        @JsonPropertyDescription("GROUP BY字段引用") List<String> groupBy,
        @JsonPropertyDescription("HAVING条件") List<HavingCondition> having,
        @JsonPropertyDescription("ORDER BY规则") List<OrderItem> orderBy,
        @JsonPropertyDescription("最大返回行数") int limit
) {
    public enum PlanStatus { READY, NEED_CLARIFICATION }
    public enum StatementType { SELECT }
    public enum Aggregate { NONE, COUNT, COUNT_DISTINCT, SUM, AVG, MAX, MIN }
    public enum Logic { AND, OR }
    public enum Operator {
        EQ, NE, GT, GTE, LT, LTE, LIKE, IN, BETWEEN,
        IS_NULL, IS_NOT_NULL, HOUR_EQ, HOUR_BETWEEN
    }
    public enum Direction { ASC, DESC }
    public enum JoinType { INNER, LEFT }

    public record TableRef(
            @JsonPropertyDescription("Schema中的真实表名") String table,
            @JsonPropertyDescription("安全英文别名；没有时为空字符串") String alias
    ) {}

    public record JoinItem(
            @JsonPropertyDescription("INNER或LEFT") JoinType type,
            @JsonPropertyDescription("被关联的真实表名") String table,
            @JsonPropertyDescription("被关联表的安全英文别名") String alias,
            @JsonPropertyDescription("已在查询范围内的别名.字段名") String leftField,
            @JsonPropertyDescription("本次关联表的别名.字段名") String rightField
    ) {}

    public record SelectItem(
            @JsonPropertyDescription("字段引用；多表时使用别名.字段名") String field,
            @JsonPropertyDescription("NONE表示普通字段") Aggregate aggregate,
            @JsonPropertyDescription("结果别名") String alias
    ) {}

    public record WhereGroup(Logic logic, List<Condition> conditions) {}

    public record Condition(
            @JsonPropertyDescription("字段引用") String field,
            Operator operator,
            @JsonPropertyDescription("参数值列表") List<String> values
    ) {}

    public record HavingCondition(
            Aggregate aggregate,
            @JsonPropertyDescription("字段引用") String field,
            Operator operator,
            String value
    ) {}

    public record OrderItem(
            @JsonPropertyDescription("字段引用或SELECT别名") String field,
            Direction direction
    ) {}
}
