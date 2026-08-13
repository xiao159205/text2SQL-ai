package com.xym.antifraud.query;

import com.xym.antifraud.config.AntifraudProperties;
import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.FieldDefinition;
import com.xym.antifraud.schema.SchemaCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlAstCompilerTest {

    private SqlAstCompiler compiler;

    @BeforeEach
    void setUp() {
        AntifraudProperties properties = new AntifraudProperties(List.of(), 300, 5000, 1000, 30, 5, true, true, true, 20);
        SchemaCatalog catalog = mockCatalog();
        SqlAstValidator validator = new SqlAstValidator(catalog, properties);
        compiler = new SqlAstCompiler(catalog, validator, new SqlLiteralRenderer());
    }

    @Test
    void compilesGenericSingleTableQuery() {
        SqlQueryPlan plan = new SqlQueryPlan(
                SqlQueryPlan.PlanStatus.READY, "", SqlQueryPlan.StatementType.SELECT,
                new SqlQueryPlan.TableRef("transactions", "t"), List.of(), false,
                List.of(
                        new SqlQueryPlan.SelectItem("t.customer_id", SqlQueryPlan.Aggregate.NONE, "customer_id"),
                        new SqlQueryPlan.SelectItem("t.amount", SqlQueryPlan.Aggregate.NONE, "amount")
                ),
                new SqlQueryPlan.WhereGroup(SqlQueryPlan.Logic.AND, List.of(
                        new SqlQueryPlan.Condition("t.trade_time", SqlQueryPlan.Operator.HOUR_EQ, List.of("3")),
                        new SqlQueryPlan.Condition("t.amount", SqlQueryPlan.Operator.GT, List.of("10000"))
                )),
                List.of(), List.of(),
                List.of(new SqlQueryPlan.OrderItem("amount", SqlQueryPlan.Direction.DESC)), 100
        );

        CompiledQuery result = compiler.compile(plan);

        assertThat(result.sql()).isEqualTo(
                "SELECT `t`.`customer_id` AS `customer_id`, `t`.`amount` AS `amount` "
                        + "FROM `transactions` AS `t` "
                        + "WHERE LPAD(HOUR(`t`.`trade_time`), 2, '0') = :p1 "
                        + "AND `t`.`amount` > :p2 ORDER BY `amount` DESC LIMIT 100"
        );
        assertThat(result.parameters()).containsEntry("p1", "03")
                .containsEntry("p2", new java.math.BigDecimal("10000"));
        assertThat(result.executableSql()).isEqualTo(
                "SELECT `t`.`customer_id` AS `customer_id`, `t`.`amount` AS `amount` "
                        + "FROM `transactions` AS `t` "
                        + "WHERE LPAD(HOUR(`t`.`trade_time`), 2, '0') = '03' "
                        + "AND `t`.`amount` > 10000 ORDER BY `amount` DESC LIMIT 100"
        );
    }

    @Test
    void compilesMultiTableJoinAndAggregation() {
        SqlQueryPlan plan = new SqlQueryPlan(
                SqlQueryPlan.PlanStatus.READY, "", SqlQueryPlan.StatementType.SELECT,
                new SqlQueryPlan.TableRef("customers", "c"),
                List.of(new SqlQueryPlan.JoinItem(SqlQueryPlan.JoinType.INNER,
                        "transactions", "t", "c.id", "t.customer_id")),
                false,
                List.of(
                        new SqlQueryPlan.SelectItem("c.id", SqlQueryPlan.Aggregate.NONE, "customer_id"),
                        new SqlQueryPlan.SelectItem("t.amount", SqlQueryPlan.Aggregate.SUM, "total_amount")
                ),
                new SqlQueryPlan.WhereGroup(SqlQueryPlan.Logic.AND, List.of()),
                List.of("c.id"),
                List.of(new SqlQueryPlan.HavingCondition(SqlQueryPlan.Aggregate.SUM,
                        "t.amount", SqlQueryPlan.Operator.GT, "10000")),
                List.of(new SqlQueryPlan.OrderItem("total_amount", SqlQueryPlan.Direction.DESC)), 100
        );

        CompiledQuery result = compiler.compile(plan);

        assertThat(result.sql()).contains(
                "FROM `customers` AS `c` INNER JOIN `transactions` AS `t` ON `c`.`id` = `t`.`customer_id`",
                "SUM(`t`.`amount`) AS `total_amount`",
                "GROUP BY `c`.`id`",
                "HAVING SUM(`t`.`amount`) > :p1"
        );
        assertThat(result.executableSql()).contains(
                "HAVING SUM(`t`.`amount`) > 10000"
        );
    }

    @Test
    void rendersStringsAndInCollectionsAsExecutableSql() {
        SqlLiteralRenderer renderer = new SqlLiteralRenderer();

        String rendered = renderer.render(
                "SELECT * FROM `customer` WHERE `name` = :p1 AND `status` IN (:p2)",
                Map.of(
                        "p1", "O'Reilly",
                        "p2", List.of("ACTIVE", "RISK")
                )
        );

        assertThat(rendered).isEqualTo(
                "SELECT * FROM `customer` WHERE `name` = 'O''Reilly' "
                        + "AND `status` IN ('ACTIVE', 'RISK')"
        );
    }
    private SchemaCatalog mockCatalog() {
        Map<String, SchemaCatalog.TableDefinition> tables = new LinkedHashMap<>();
        tables.put("customers", table("customers",
                field("customers", 1, "id", "bigint", "客户ID"),
                field("customers", 2, "name", "varchar(100)", "客户名称")));
        tables.put("transactions", table("transactions",
                field("transactions", 1, "id", "bigint", "交易ID"),
                field("transactions", 2, "customer_id", "bigint", "客户ID"),
                field("transactions", 3, "amount", "decimal(18,2)", "交易金额"),
                field("transactions", 4, "trade_time", "datetime", "交易时间")));

        SchemaCatalog catalog = mock(SchemaCatalog.class);
        when(catalog.requireTable(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT);
            SchemaCatalog.TableDefinition table = tables.get(name);
            if (table == null) throw new IllegalArgumentException("表不存在: " + name);
            return table;
        });
        when(catalog.resolveField(anyString(), anyMap())).thenAnswer(invocation -> {
            String reference = invocation.getArgument(0, String.class);
            Map<String, String> scope = invocation.getArgument(1);
            String[] parts = reference.split("\\.");
            String qualifier;
            String tableName;
            String column;
            if (parts.length == 2) {
                qualifier = parts[0].toLowerCase(Locale.ROOT);
                tableName = scope.get(qualifier);
                column = parts[1];
            } else {
                qualifier = scope.keySet().iterator().next();
                tableName = scope.get(qualifier);
                column = parts[0];
            }
            FieldDefinition field = tables.get(tableName.toLowerCase(Locale.ROOT))
                    .columns().get(column.toLowerCase(Locale.ROOT));
            if (field == null) throw new IllegalArgumentException("字段不存在: " + reference);
            return new SchemaCatalog.ResolvedField(qualifier, field);
        });
        return catalog;
    }

    private SchemaCatalog.TableDefinition table(String name, FieldDefinition... fields) {
        Map<String, FieldDefinition> columns = new LinkedHashMap<>();
        for (FieldDefinition field : fields) columns.put(field.columnName(), field);
        return new SchemaCatalog.TableDefinition(name, "", Map.copyOf(columns));
    }

    private FieldDefinition field(String table, int position, String name, String type, String comment) {
        return new FieldDefinition(table, position, name, type, comment);
    }
}
