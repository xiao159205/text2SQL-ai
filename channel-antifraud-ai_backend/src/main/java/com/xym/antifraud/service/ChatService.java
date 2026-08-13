package com.xym.antifraud.service;

import com.xym.antifraud.ai.SqlPlanGenerator;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.datasource.DatabaseContext;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.query.CompiledQuery;
import com.xym.antifraud.query.QueryExecutor;
import com.xym.antifraud.query.SqlAstCompiler;
import com.xym.antifraud.query.SqlAstValidator;
import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.FieldDefinition;
import com.xym.antifraud.schema.SchemaCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 串起 Text2SQL、查询执行和图表推荐的主流程。 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final SqlPlanGenerator planGenerator;
    private final SqlAstCompiler compiler;
    private final SqlAstValidator validator;
    private final QueryExecutor queryExecutor;
    private final SchemaCatalog schemaCatalog;
    private final ChartService chartService;

    public ChatService(SqlPlanGenerator planGenerator, SqlAstCompiler compiler,
                       SqlAstValidator validator, QueryExecutor queryExecutor,
                       SchemaCatalog schemaCatalog, ChartService chartService) {
        this.planGenerator = planGenerator;
        this.compiler = compiler;
        this.validator = validator;
        this.queryExecutor = queryExecutor;
        this.schemaCatalog = schemaCatalog;
        this.chartService = chartService;
    }

    public ChatResponse ask(String question, String databaseName) {
        DatabaseType database = DatabaseType.from(databaseName);
        // 路由数据源依赖线程上下文，用完必须清掉，避免线程复用时串库。
        DatabaseContext.use(database);
        try {
            return askUsingSelectedDatabase(question, database);
        } finally {
            DatabaseContext.clear();
        }
    }

    private ChatResponse askUsingSelectedDatabase(String question, DatabaseType database) {
        long startedAt = System.nanoTime();
        log.info("[Text2SQL开始] 前端问题={}", question);
        log.info("[步骤1] 读取数据库Schema并请求大模型生成AST");
        SqlQueryPlan plan = planGenerator.generate(question);
        log.info("[步骤1完成] queryPlan={}", plan);
        if (plan.status() == SqlQueryPlan.PlanStatus.NEED_CLARIFICATION) {
            // 业务口径不明确时不猜 SQL，直接把模型的问题返回给前端。
            ChatResponse response = new ChatResponse(plan.clarification(), "",
                    new ChatResponse.TableResult(List.of(), List.of()), null, plan);
            log.info("[Text2SQL需要澄清] clarification={}, response={}", plan.clarification(), response);
            return response;
        }

        log.info("[步骤2] 校验AST并编译参数化SQL");
        CompiledQuery compiledQuery = compiler.compile(plan);
        log.info("[步骤2完成-最终可执行SQL] {}", compiledQuery.executableSql());
        log.info("[步骤3] 执行MySQL只读查询");
        List<Map<String, Object>> rows = addFrontendKeys(queryExecutor.execute(compiledQuery));
        log.info("[步骤4] 根据Schema生成前端表格列");
        List<ChatResponse.Column> columns = buildColumns(plan, validator.scope(plan));
        log.info("[Chart pipeline] query data ready, database={}, rowCount={}, columns={}",
                database, rows.size(), columns);
        ChatResponse.ChartResult chart = chartService.recommend(question, columns, rows);
        log.info("[Chart pipeline] recommendation complete, chartIncluded={}, chartType={}",
                chart != null, chart == null ? null : chart.type());

        ChatResponse response = new ChatResponse("查询完成，共找到%d条结果。".formatted(rows.size()),
                compiledQuery.executableSql(), new ChatResponse.TableResult(columns, rows), chart, plan);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("[Text2SQL完成] elapsedMs={}, summary={}, columns={}, rowCount={}, sql={}",
                elapsedMs, response.summary(), response.table().columns(), rows.size(), response.sql());
        log.info("[Chart pipeline] API response assembled, chart={}", response.chart());
        return response;
    }

    private List<ChatResponse.Column> buildColumns(SqlQueryPlan plan, Map<String, String> scope) {
        // 表头使用数据库注释，dataIndex 则必须和查询结果里的列名保持一致。
        List<ChatResponse.Column> columns = new ArrayList<>();
        for (SqlQueryPlan.SelectItem item : plan.select()) {
            FieldDefinition field = schemaCatalog.resolveField(item.field(), scope).field();
            String dataIndex = item.alias() == null || item.alias().isBlank()
                    ? field.columnName() : item.alias();
            String title = item.aggregate() == null || item.aggregate() == SqlQueryPlan.Aggregate.NONE
                    ? field.displayName() : item.aggregate().name() + "(" + field.displayName() + ")";
            columns.add(new ChatResponse.Column(title, dataIndex));
        }
        return List.copyOf(columns);
    }

    private List<Map<String, Object>> addFrontendKeys(List<Map<String, Object>> result) {
        // Ant Design Table 需要稳定的行 key，这个字段不参与业务查询。
        List<Map<String, Object>> rows = new ArrayList<>(result.size());
        for (int index = 0; index < result.size(); index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", index + 1);
            row.putAll(result.get(index));
            rows.add(row);
        }
        return rows;
    }
}
