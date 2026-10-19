package com.xym.antifraud.query;

import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.FieldDefinition;
import com.xym.antifraud.schema.SchemaCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.xym.antifraud.query.ast.SqlQueryPlan.Aggregate;

/** 把通过校验的查询计划编译成参数化 SQL。 */
@Component
public class SqlAstCompiler {

    private static final Logger log = LoggerFactory.getLogger(SqlAstCompiler.class);

    private final SchemaCatalog catalog;
    private final SqlAstValidator validator;
    private final SqlLiteralRenderer literalRenderer;

    public SqlAstCompiler(SchemaCatalog catalog, SqlAstValidator validator,
                          SqlLiteralRenderer literalRenderer) {
        this.catalog = catalog;
        this.validator = validator;
        this.literalRenderer = literalRenderer;
    }

    public CompiledQuery compile(SqlQueryPlan plan) {
        log.info("[SQL编译开始] plan={}", plan);
        // 后面的拼接逻辑只处理已经确认存在的表、字段和操作符。
        validator.validate(plan);
        if (plan.status() == SqlQueryPlan.PlanStatus.NEED_CLARIFICATION) {
            throw new IllegalArgumentException("需要澄清的问题不能编译SQL");
        }
        Map<String, String> scope = validator.scope(plan);
        Map<String, Object> parameters = new LinkedHashMap<>();
        AtomicInteger parameterSequence = new AtomicInteger();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (plan.distinct()) {
            sql.append("DISTINCT ");
        }
        sql.append(plan.select().stream()
                .map(item -> compileSelect(item, scope))
                .collect(Collectors.joining(", ")));
        sql.append(" FROM ").append(compileTable(plan.from().table(), plan.from().alias()));

        for (SqlQueryPlan.JoinItem join : safeList(plan.joins())) {
            sql.append(' ').append(join.type().name()).append(" JOIN ")
                    .append(compileTable(join.table(), join.alias()))
                    .append(" ON ")
                    .append(compileField(join.leftField(), scope))
                    .append(" = ")
                    .append(compileField(join.rightField(), scope));
        }

        if (plan.where() != null && plan.where().conditions() != null
                && !plan.where().conditions().isEmpty()) {
            String delimiter = " " + plan.where().logic().name() + " ";
            sql.append(" WHERE ").append(plan.where().conditions().stream()
                    .map(condition -> compileCondition(condition, scope, parameters, parameterSequence))
                    .collect(Collectors.joining(delimiter)));
        }

        if (!safeList(plan.groupBy()).isEmpty()) {
            sql.append(" GROUP BY ").append(plan.groupBy().stream()
                    .map(field -> compileField(field, scope))
                    .collect(Collectors.joining(", ")));
        }

        if (!safeList(plan.having()).isEmpty()) {
            sql.append(" HAVING ").append(plan.having().stream()
                    .map(condition -> compileHaving(condition, scope, parameters, parameterSequence))
                    .collect(Collectors.joining(" AND ")));
        }

        if (!safeList(plan.orderBy()).isEmpty()) {
            Set<String> aliases = plan.select().stream()
                    .map(SqlQueryPlan.SelectItem::alias)
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(alias -> alias.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            sql.append(" ORDER BY ").append(plan.orderBy().stream()
                    .map(item -> compileOrder(item, scope, aliases))
                    .collect(Collectors.joining(", ")));
        }

        sql.append(" LIMIT ").append(plan.limit());
        Map<String, Object> immutableParameters = Map.copyOf(parameters);
        String executableSql = literalRenderer.render(sql.toString(), immutableParameters);
        CompiledQuery compiled = new CompiledQuery(sql.toString(), immutableParameters, executableSql);
        log.info("[SQL编译完成-最终可执行SQL] {}", compiled.executableSql());
        log.info("[SQL编译明细] parameterizedSql={}, parameters={}",
                compiled.sql(), compiled.parameters());
        return compiled;
    }

    private String compileTable(String tableName, String alias) {
        String actualTable = catalog.requireTable(tableName).tableName();
        if (alias == null || alias.isBlank()) {
            return quoteIdentifier(actualTable);
        }
        return quoteIdentifier(actualTable) + " AS " + quoteIdentifier(alias.toLowerCase(Locale.ROOT));
    }

    private String compileSelect(SqlQueryPlan.SelectItem item, Map<String, String> scope) {
        SchemaCatalog.ResolvedField resolved = catalog.resolveField(item.field(), scope);
        String expression = aggregateExpression(item.aggregate(), qualifiedField(resolved));
        if (item.aggregate() != null && item.aggregate() != Aggregate.NONE) {
            // 聚合列强制加确定性别名，让结果列名与前端 dataIndex 完全一致，
            // 避免依赖 MySQL 对 SUM(x)/COUNT(x) 等表达式的返回命名。
            expression += " AS " + quoteIdentifier(
                    outputColumnName(item.aggregate(), item.alias(), resolved.field().columnName()));
        } else if (item.alias() != null && !item.alias().isBlank()) {
            expression += " AS " + quoteIdentifier(item.alias());
        }
        return expression;
    }

    /**
     * 计算 SELECT 结果列在结果集中的最终列名，与 {@link #compileSelect} 强制加的别名保持一致，
     * 供前端表格/图表的 dataIndex 复用。显式别名优先；普通字段用列名；聚合字段用「聚合名_列名」，
     * 避免与 MySQL 的表达式命名（如 {@code SUM(amount)}）耦合。
     */
    public static String outputColumnName(SqlQueryPlan.Aggregate aggregate,
                                          String alias, String columnName) {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        if (aggregate == null || aggregate == Aggregate.NONE) {
            return columnName;
        }
        return aggregate.name() + "_" + columnName;
    }

    private String compileCondition(SqlQueryPlan.Condition condition, Map<String, String> scope,
                                    Map<String, Object> parameters, AtomicInteger sequence) {
        SchemaCatalog.ResolvedField resolved = catalog.resolveField(condition.field(), scope);
        FieldDefinition field = resolved.field();
        String column = qualifiedField(resolved);
        List<String> values = safeList(condition.values());
        return switch (condition.operator()) {
            case IS_NULL -> column + " IS NULL";
            case IS_NOT_NULL -> column + " IS NOT NULL";
            case IN -> {
                String parameter = nextParameter(sequence);
                parameters.put(parameter, values.stream().map(value -> convertValue(field, value)).toList());
                yield column + " IN (:" + parameter + ")";
            }
            case BETWEEN -> {
                String first = nextParameter(sequence);
                String second = nextParameter(sequence);
                parameters.put(first, convertValue(field, values.get(0)));
                parameters.put(second, convertValue(field, values.get(1)));
                yield column + " BETWEEN :" + first + " AND :" + second;
            }
            case HOUR_EQ -> {
                String parameter = nextParameter(sequence);
                parameters.put(parameter, normalizeHour(values.get(0)));
                yield "LPAD(HOUR(" + column + "), 2, '0') = :" + parameter;
            }
            case HOUR_BETWEEN -> {
                String first = nextParameter(sequence);
                String second = nextParameter(sequence);
                parameters.put(first, normalizeHour(values.get(0)));
                parameters.put(second, normalizeHour(values.get(1)));
                yield "LPAD(HOUR(" + column + "), 2, '0') BETWEEN :" + first + " AND :" + second;
            }
            default -> {
                String parameter = nextParameter(sequence);
                parameters.put(parameter, convertValue(field, values.get(0)));
                yield column + " " + comparisonOperator(condition.operator()) + " :" + parameter;
            }
        };
    }

    private String compileHaving(SqlQueryPlan.HavingCondition condition, Map<String, String> scope,
                                 Map<String, Object> parameters, AtomicInteger sequence) {
        SchemaCatalog.ResolvedField resolved = catalog.resolveField(condition.field(), scope);
        String parameter = nextParameter(sequence);
        parameters.put(parameter, convertValue(resolved.field(), condition.value()));
        return aggregateExpression(condition.aggregate(), qualifiedField(resolved))
                + " " + comparisonOperator(condition.operator()) + " :" + parameter;
    }

    private String compileOrder(SqlQueryPlan.OrderItem item, Map<String, String> scope, Set<String> aliases) {
        String expression = aliases.contains(item.field().toLowerCase(Locale.ROOT))
                ? quoteIdentifier(item.field()) : compileField(item.field(), scope);
        return expression + " " + item.direction().name();
    }

    private String compileField(String reference, Map<String, String> scope) {
        return qualifiedField(catalog.resolveField(reference, scope));
    }

    private String qualifiedField(SchemaCatalog.ResolvedField resolved) {
        return quoteIdentifier(resolved.qualifier().toLowerCase(Locale.ROOT))
                + "." + quoteIdentifier(resolved.field().columnName());
    }

    private String aggregateExpression(Aggregate aggregate, String fieldExpression) {
        Aggregate safe = aggregate == null ? Aggregate.NONE : aggregate;
        return switch (safe) {
            case NONE -> fieldExpression;
            case COUNT -> "COUNT(" + fieldExpression + ")";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + fieldExpression + ")";
            case SUM -> "SUM(" + fieldExpression + ")";
            case AVG -> "AVG(" + fieldExpression + ")";
            case MAX -> "MAX(" + fieldExpression + ")";
            case MIN -> "MIN(" + fieldExpression + ")";
        };
    }

    private String comparisonOperator(SqlQueryPlan.Operator operator) {
        return switch (operator) {
            case EQ -> "="; case NE -> "<>"; case GT -> ">"; case GTE -> ">=";
            case LT -> "<"; case LTE -> "<="; case LIKE -> "LIKE";
            default -> throw new IllegalArgumentException("不支持的比较操作符: " + operator);
        };
    }

    private Object convertValue(FieldDefinition field, String value) {
        if (field.isIntegral()) return Long.valueOf(value);
        if (field.isNumeric()) return new BigDecimal(value);
        return value;
    }

    private String normalizeHour(String value) {
        int hour = Integer.parseInt(value);
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("小时必须在0到23之间");
        return "%02d".formatted(hour);
    }

    private String nextParameter(AtomicInteger sequence) {
        return "p" + sequence.incrementAndGet();
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
