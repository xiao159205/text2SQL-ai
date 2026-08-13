package com.xym.antifraud.query;

import com.xym.antifraud.config.AntifraudProperties;
import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.FieldDefinition;
import com.xym.antifraud.schema.SchemaCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.xym.antifraud.query.ast.SqlQueryPlan.Aggregate;
import static com.xym.antifraud.query.ast.SqlQueryPlan.Operator;

/** 编译前检查模型返回的查询计划，拦住越权字段和不安全结构。 */
@Component
public class SqlAstValidator {

    private static final Logger log = LoggerFactory.getLogger(SqlAstValidator.class);

    private static final int MAX_SELECT_COLUMNS = 100;
    private static final Pattern SAFE_ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<Operator> HAVING_OPERATORS =
            Set.of(Operator.EQ, Operator.NE, Operator.GT, Operator.GTE, Operator.LT, Operator.LTE);

    private final SchemaCatalog catalog;
    private final AntifraudProperties properties;

    public SqlAstValidator(SchemaCatalog catalog, AntifraudProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    public void validate(SqlQueryPlan plan) {
        // 校验失败直接终止，不尝试修补模型输出，避免改变用户原意。
        log.info("[AST校验开始] plan={}", plan);
        if (plan == null) {
            throw new IllegalArgumentException("模型没有返回查询计划");
        }
        if (plan.status() == SqlQueryPlan.PlanStatus.NEED_CLARIFICATION) {
            log.info("[AST校验跳过] 模型要求澄清: {}", plan.clarification());
            return;
        }
        if (plan.statementType() != SqlQueryPlan.StatementType.SELECT) {
            throw new IllegalArgumentException("只允许SELECT查询");
        }
        if (plan.from() == null) {
            throw new IllegalArgumentException("FROM主表不能为空");
        }
        if (safeList(plan.joins()).size() > properties.maxJoins()) {
            throw new IllegalArgumentException("JOIN数量不能超过" + properties.maxJoins());
        }
        Map<String, String> scope = validateTablesAndBuildScope(plan);
        if (plan.select() == null || plan.select().isEmpty()
                || plan.select().size() > MAX_SELECT_COLUMNS) {
            throw new IllegalArgumentException("SELECT字段数量必须在1到100之间");
        }
        if (plan.limit() < 1 || plan.limit() > properties.maxResultRows()) {
            throw new IllegalArgumentException("LIMIT必须在1到" + properties.maxResultRows() + "之间");
        }

        Set<String> aliases = new java.util.HashSet<>();
        Set<String> outputNames = new java.util.HashSet<>();
        for (SqlQueryPlan.SelectItem item : plan.select()) {
            FieldDefinition field = catalog.resolveField(item.field(), scope).field();
            Aggregate aggregate = item.aggregate() == null ? Aggregate.NONE : item.aggregate();
            validateAggregateField(aggregate, field);
            String outputName = field.columnName();
            if (item.alias() != null && !item.alias().isBlank()) {
                validateAlias(item.alias());
                outputName = item.alias();
                if (!aliases.add(normalize(item.alias()))) {
                    throw new IllegalArgumentException("SELECT别名重复: " + item.alias());
                }
            }
            if (!outputNames.add(normalize(outputName))) {
                throw new IllegalArgumentException("SELECT结果列名重复，请设置唯一别名: " + outputName);
            }
        }

        validateWhere(plan.where(), scope);
        safeList(plan.groupBy()).forEach(field -> catalog.resolveField(field, scope));

        for (SqlQueryPlan.HavingCondition condition : safeList(plan.having())) {
            if (condition.aggregate() == null || condition.aggregate() == Aggregate.NONE) {
                throw new IllegalArgumentException("HAVING必须使用聚合函数");
            }
            if (!HAVING_OPERATORS.contains(condition.operator())) {
                throw new IllegalArgumentException("HAVING不支持操作符: " + condition.operator());
            }
            FieldDefinition field = catalog.resolveField(condition.field(), scope).field();
            validateAggregateField(condition.aggregate(), field);
            if (condition.value() == null || condition.value().isBlank()) {
                throw new IllegalArgumentException("HAVING比较值不能为空");
            }
        }

        for (SqlQueryPlan.OrderItem order : safeList(plan.orderBy())) {
            if (order.direction() == null) {
                throw new IllegalArgumentException("ORDER BY方向不能为空");
            }
            if (!aliases.contains(normalize(order.field()))) {
                catalog.resolveField(order.field(), scope);
            }
        }
        log.info("[AST校验通过] scope={}, selectCount={}, whereCount={}, joinCount={}, limit={}",
                scope, plan.select().size(),
                plan.where() == null ? 0 : safeList(plan.where().conditions()).size(),
                safeList(plan.joins()).size(), plan.limit());
    }

    public Map<String, String> scope(SqlQueryPlan plan) {
        // scope 同时承担别名解析和字段归属校验。
        return Map.copyOf(validateTablesAndBuildScope(plan));
    }

    private Map<String, String> validateTablesAndBuildScope(SqlQueryPlan plan) {
        LinkedHashMap<String, String> scope = new LinkedHashMap<>();
        addTable(scope, plan.from().table(), plan.from().alias());
        for (SqlQueryPlan.JoinItem join : safeList(plan.joins())) {
            if (join.type() == null) {
                throw new IllegalArgumentException("JOIN类型不能为空");
            }
            LinkedHashMap<String, String> joinedTableScope = new LinkedHashMap<>();
            addTable(joinedTableScope, join.table(), join.alias());
            SchemaCatalog.ResolvedField left = catalog.resolveField(join.leftField(), scope);
            SchemaCatalog.ResolvedField right = catalog.resolveField(join.rightField(), joinedTableScope);
            if (left.field().tableName().equalsIgnoreCase(right.field().tableName())
                    && scope.size() == 1) {
                throw new IllegalArgumentException("JOIN必须连接两个不同的表");
            }
            String qualifier = joinedTableScope.keySet().iterator().next();
            if (scope.containsKey(qualifier)) {
                throw new IllegalArgumentException("表别名重复: " + qualifier);
            }
            scope.putAll(joinedTableScope);
        }
        return scope;
    }

    private void addTable(Map<String, String> scope, String tableName, String alias) {
        SchemaCatalog.TableDefinition table = catalog.requireTable(tableName);
        String qualifier = alias == null || alias.isBlank() ? table.tableName() : alias;
        validateAlias(qualifier);
        String key = normalize(qualifier);
        if (scope.putIfAbsent(key, table.tableName()) != null) {
            throw new IllegalArgumentException("表别名重复: " + qualifier);
        }
    }

    private void validateWhere(SqlQueryPlan.WhereGroup where, Map<String, String> scope) {
        if (where == null || where.conditions() == null || where.conditions().isEmpty()) {
            return;
        }
        if (where.logic() == null) {
            throw new IllegalArgumentException("WHERE逻辑关系不能为空");
        }
        if (where.conditions().size() > properties.maxConditions()) {
            throw new IllegalArgumentException("WHERE条件不能超过" + properties.maxConditions() + "个");
        }
        for (SqlQueryPlan.Condition condition : where.conditions()) {
            FieldDefinition field = catalog.resolveField(condition.field(), scope).field();
            if (condition.operator() == null) {
                throw new IllegalArgumentException("WHERE操作符不能为空");
            }
            int valueCount = safeList(condition.values()).size();
            switch (condition.operator()) {
                case IS_NULL, IS_NOT_NULL -> requireValueCount(condition, valueCount, 0);
                case BETWEEN, HOUR_BETWEEN -> requireValueCount(condition, valueCount, 2);
                case IN -> {
                    if (valueCount < 1 || valueCount > 100) {
                        throw new IllegalArgumentException("IN参数数量必须在1到100之间");
                    }
                }
                default -> requireValueCount(condition, valueCount, 1);
            }
            if (condition.operator() == Operator.LIKE && !field.isTextual()) {
                throw new IllegalArgumentException("LIKE只能用于文本字段: " + field.columnName());
            }
            if ((condition.operator() == Operator.HOUR_EQ || condition.operator() == Operator.HOUR_BETWEEN)
                    && !field.isTemporal()) {
                throw new IllegalArgumentException("小时操作只能用于MySQL日期时间字段: " + field.columnName());
            }
        }
    }

    private void validateAggregateField(Aggregate aggregate, FieldDefinition field) {
        if (Set.of(Aggregate.SUM, Aggregate.AVG).contains(aggregate) && !field.isNumeric()) {
            throw new IllegalArgumentException(aggregate + "只能用于数值字段: " + field.columnName());
        }
    }

    private void validateAlias(String alias) {
        if (!SAFE_ALIAS.matcher(alias).matches()) {
            throw new IllegalArgumentException("非法SQL别名: " + alias);
        }
    }

    private void requireValueCount(SqlQueryPlan.Condition condition, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(condition.operator() + "参数数量应为" + expected + "，实际为" + actual);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
