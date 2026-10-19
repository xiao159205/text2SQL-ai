package com.xym.antifraud.service;

import com.xym.antifraud.ai.SqlPlanGenerator;
import com.xym.antifraud.datasource.DatabaseContext;
import com.xym.antifraud.datasource.DatabaseConnectionContext;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.dto.chat.ChatTurnResponse;
import com.xym.antifraud.entity.ChatConversation;
import com.xym.antifraud.entity.ChatMessage;
import com.xym.antifraud.query.CompiledQuery;
import com.xym.antifraud.query.QueryExecutor;
import com.xym.antifraud.query.SqlAstCompiler;
import com.xym.antifraud.query.SqlAstValidator;
import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.FieldDefinition;
import com.xym.antifraud.schema.SchemaCatalog;
import com.xym.antifraud.stream.AgUiEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Routes a question and executes the selected chat pipeline. */
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final SqlPlanGenerator planGenerator;
    private final SqlAstCompiler compiler;
    private final SqlAstValidator validator;
    private final QueryExecutor queryExecutor;
    private final SchemaCatalog schemaCatalog;
    private final ChartService chartService;
    private final ChatHistoryService historyService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final ChatIntentRouter intentRouter;
    private final RagService ragService;
    private final GeneralChatService generalChatService;
    private final UserDataSourceConnectionService connectionService;

    @Autowired
    public ChatService(SqlPlanGenerator planGenerator, SqlAstCompiler compiler,
                       SqlAstValidator validator, QueryExecutor queryExecutor,
                       SchemaCatalog schemaCatalog, ChartService chartService,
                       ChatHistoryService historyService, KnowledgeRetriever knowledgeRetriever,
                       ChatIntentRouter intentRouter, RagService ragService,
                       GeneralChatService generalChatService,
                       UserDataSourceConnectionService connectionService) {
        this.planGenerator = planGenerator;
        this.compiler = compiler;
        this.validator = validator;
        this.queryExecutor = queryExecutor;
        this.schemaCatalog = schemaCatalog;
        this.chartService = chartService;
        this.historyService = historyService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.intentRouter = intentRouter;
        this.ragService = ragService;
        this.generalChatService = generalChatService;
        this.connectionService = connectionService;
    }

    ChatService(SqlPlanGenerator planGenerator, SqlAstCompiler compiler,
                SqlAstValidator validator, QueryExecutor queryExecutor,
                SchemaCatalog schemaCatalog, ChartService chartService,
                ChatHistoryService historyService, KnowledgeRetriever knowledgeRetriever,
                ChatIntentRouter intentRouter, RagService ragService,
                GeneralChatService generalChatService) {
        this(planGenerator, compiler, validator, queryExecutor, schemaCatalog, chartService,
                historyService, knowledgeRetriever, intentRouter, ragService, generalChatService, null);
    }

    public ChatTurnResponse ask(Long userId, Long conversationId, Long replaceFromMessageId,
                                String question, String databaseName) {
        return ask(userId, conversationId, replaceFromMessageId, question, databaseName, true,
                AgUiEventPublisher.noop());
    }

    public ChatTurnResponse ask(Long userId, Long conversationId, Long replaceFromMessageId,
                                String question, String databaseName, boolean useKnowledgeBase) {
        return ask(userId, conversationId, replaceFromMessageId, question, databaseName,
                useKnowledgeBase, AgUiEventPublisher.noop());
    }

    public ChatTurnResponse ask(Long userId, Long conversationId, Long replaceFromMessageId,
                                String question, String databaseName, String connectionId,
                                boolean useKnowledgeBase) {
        return ask(userId, conversationId, replaceFromMessageId, question, databaseName,
                connectionId, useKnowledgeBase, AgUiEventPublisher.noop());
    }

    public ChatTurnResponse ask(Long userId, Long conversationId, Long replaceFromMessageId,
                                String question, String databaseName,
                                AgUiEventPublisher events) {
        return ask(userId, conversationId, replaceFromMessageId, question, databaseName,
                true, events);
    }

    public ChatTurnResponse ask(Long userId, Long conversationId, Long replaceFromMessageId,
                                String question, String databaseName, boolean useKnowledgeBase,
                                AgUiEventPublisher events) {
        return ask(userId, conversationId, replaceFromMessageId, question, databaseName,
                null, useKnowledgeBase, events);
    }

    public ChatTurnResponse ask(Long userId, Long conversationId, Long replaceFromMessageId,
                                String question, String databaseName, String connectionId,
                                boolean useKnowledgeBase, AgUiEventPublisher events) {
        DatabaseType database = DatabaseType.from(databaseName);
        ChatConversation conversation = historyService.getOrCreate(userId, conversationId,
                database.name().toLowerCase(), question);
        String context = historyService.context(conversation.getId(), replaceFromMessageId);
        try {
            events.stepStarted("intent-routing");
            KnowledgeRetrieval knowledgeRetrieval = KnowledgeRetrieval.disabled();
            if (useKnowledgeBase && intentRouter.shouldProbeKnowledge(question)) {
                events.reasoning("正在预检知识库，让意图判断基于真实命中资料而不是仅凭问题猜测。");
                String knowledgeTool = events.toolStarted("probe_knowledge_base",
                        Map.of("question", question, "database", database.name().toLowerCase()));
                knowledgeRetrieval = knowledgeRetriever.retrieve(
                        question, database.name().toLowerCase());
                events.toolFinished(knowledgeTool, Map.of(
                        "status", knowledgeRetrieval.status().name(),
                        "hitCount", knowledgeRetrieval.hits().size(),
                        "rewrittenQuery", knowledgeRetrieval.rewrittenQuery()));
            } else if (!useKnowledgeBase) {
                events.reasoning("知识库检索已关闭，正在结合会话上下文判断本次请求应使用哪项能力。");
            } else {
                events.reasoning("当前问题已命中本地快速路由，无需访问知识库。");
            }
            String routingTool = events.toolStarted("route_chat_intent",
                    Map.of("question", question));
            ChatResponse.ResponseType intent = intentRouter.route(
                    question, context, knowledgeRetrieval.routingEvidence());
            events.toolFinished(routingTool, Map.of("intent", intent.name()));
            events.stepFinished("intent-routing");

            events.stepStarted("capability-execution");
            ChatResponse response = answerByIntent(intent, question, database, connectionId,
                    userId, context,
                    useKnowledgeBase, knowledgeRetrieval, events);
            events.stepFinished("capability-execution");

            events.stepStarted("conversation-persistence");
            historyService.truncateFrom(conversation.getId(), replaceFromMessageId);
            ChatMessage userMessage = historyService.saveUser(conversation.getId(), question);
            ChatMessage assistantMessage = historyService.saveAssistant(conversation.getId(), response);
            events.stepFinished("conversation-persistence");
            return new ChatTurnResponse(conversation.getId(), userMessage.getId(),
                    assistantMessage.getId(), response);
        } catch (RuntimeException exception) {
            if (conversationId == null) historyService.delete(userId, conversation.getId());
            throw exception;
        }
    }

    private ChatResponse answerByIntent(ChatResponse.ResponseType intent, String question,
                                         DatabaseType database, String connectionId, Long userId,
                                         String context,
                                         boolean useKnowledgeBase,
                                         KnowledgeRetrieval knowledgeRetrieval,
                                         AgUiEventPublisher events) {
        return switch (intent) {
            case DATA_QUERY -> {
                if (connectionId != null) connectionService.requireOwned(userId, connectionId, database);
                DatabaseContext.use(database);
                DatabaseConnectionContext.use(connectionId);
                try {
                    yield askUsingSelectedDatabase(question, database, context,
                            useKnowledgeBase, knowledgeRetrieval, events);
                } finally {
                    DatabaseConnectionContext.clear();
                    DatabaseContext.clear();
                }
            }
            case KNOWLEDGE_QA -> {
                if (!useKnowledgeBase) {
                    events.reasoning("知识库检索已关闭，将按通用问答方式回答。");
                    String toolCallId = events.toolStarted("generate_general_answer",
                            Map.of("question", question));
                    String answer = generalChatService.answer(question, context);
                    events.toolFinished(toolCallId, Map.of("status", "completed"));
                    yield ChatResponse.conversational(ChatResponse.ResponseType.GENERAL_CHAT,
                            answer, List.of());
                }
                events.reasoning("已识别为企业知识问答，正在检索并核对相关资料。");
                String toolCallId = events.toolStarted("search_knowledge_base",
                        Map.of("question", question, "database", database.name().toLowerCase()));
                var rag = ragService.ask(question, knowledgeRetrieval);
                events.toolFinished(toolCallId,
                        Map.of("sourceCount", rag.sources().size(), "rewrittenQuery", rag.rewrittenQuery()));
                List<ChatResponse.Source> sources = rag.sources().stream()
                        .map(source -> new ChatResponse.Source(source.id(), source.title(),
                                source.source(), source.score()))
                        .toList();
                yield ChatResponse.conversational(intent, rag.answer(), sources);
            }
            case GENERAL_CHAT -> {
                events.reasoning("已识别为通用交流，正在组织回答。");
                String toolCallId = events.toolStarted("generate_general_answer",
                        Map.of("question", question));
                String answer = generalChatService.answer(question, context);
                events.toolFinished(toolCallId, Map.of("status", "completed"));
                yield ChatResponse.conversational(intent, answer, List.of());
            }
            case CLARIFICATION -> ChatResponse.conversational(intent,
                    "我还不确定你是想查询业务数据，还是查找知识库资料。请补充一下目标，例如“统计近30天各渠道交易金额”或“查询销售额指标口径”。",
                    List.of());
        };
    }

    private ChatResponse askUsingSelectedDatabase(String question, DatabaseType database,
                                                   String context, boolean useKnowledgeBase,
                                                   KnowledgeRetrieval knowledgeRetrieval,
                                                   AgUiEventPublisher events) {
        long startedAt = System.nanoTime();
        log.info("[Text2SQL start] question={}", question);
        String knowledgeContext = "";
        if (useKnowledgeBase) {
            events.reasoning("已识别为数据查询，正在复用预检得到的业务知识并读取当前数据源的真实 Schema。");
            knowledgeContext = knowledgeRetrieval.context();
            log.info("[Knowledge retrieval reused] status={}, hits={}, contextChars={}",
                    knowledgeRetrieval.status(), knowledgeRetrieval.hits().size(),
                    knowledgeContext.length());
        } else {
            events.reasoning("知识库检索已关闭，正在读取当前数据源的真实 Schema。");
            log.info("[Knowledge retrieval skipped] disabled by user");
        }

        events.reasoning("正在将问题转换为受约束的只读查询计划。");
        String planTool = events.toolStarted("generate_sql_query_plan",
                Map.of("question", question, "database", database.name().toLowerCase()));
        SqlQueryPlan plan = planGenerator.generate(question, context, knowledgeContext);
        events.toolFinished(planTool, Map.of("status", plan.status().name(), "plan", plan));
        log.info("[SQL plan generated] queryPlan={}", plan);
        if (plan.status() == SqlQueryPlan.PlanStatus.NEED_CLARIFICATION) {
            events.reasoning("查询条件仍有歧义，需要先向你确认后才能安全执行。");
            return new ChatResponse(ChatResponse.ResponseType.CLARIFICATION, plan.clarification(), "",
                    new ChatResponse.TableResult(List.of(), List.of()), null, plan, List.of());
        }

        events.reasoning("查询计划已生成，正在校验表、字段、操作符和返回行数限制。");
        String compileTool = events.toolStarted("validate_and_compile_sql",
                Map.of("database", database.name().toLowerCase()));
        CompiledQuery compiledQuery = compiler.compile(plan);
        events.toolFinished(compileTool, Map.of("sql", compiledQuery.executableSql()));
        log.info("[Executable SQL] {}", compiledQuery.executableSql());

        events.reasoning("安全校验通过，正在对所选数据源执行参数化只读查询。");
        String queryTool = events.toolStarted("execute_database_query",
                Map.of("database", database.name().toLowerCase(), "sql", compiledQuery.executableSql()));
        List<Map<String, Object>> rows = addFrontendKeys(queryExecutor.execute(compiledQuery));
        events.toolFinished(queryTool, Map.of("rowCount", rows.size()));
        List<ChatResponse.Column> columns = buildColumns(plan, validator.scope(plan));

        events.reasoning("查询完成，正在判断结果是否适合用图表展示。");
        String chartTool = events.toolStarted("select_result_chart",
                Map.of("rowCount", rows.size(), "columnCount", columns.size()));
        ChatResponse.ChartResult chart = chartService.recommend(question, columns, rows);
        events.toolFinished(chartTool, Map.of("chartType", chart == null ? "NONE" : chart.type()));

        ChatResponse response = new ChatResponse("查询完成，共找到%d条结果。".formatted(rows.size()),
                compiledQuery.executableSql(), new ChatResponse.TableResult(columns, rows), chart, plan);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("[Text2SQL complete] elapsedMs={}, rowCount={}", elapsedMs, rows.size());
        return response;
    }

    private List<ChatResponse.Column> buildColumns(SqlQueryPlan plan, Map<String, String> scope) {
        List<ChatResponse.Column> columns = new ArrayList<>();
        for (SqlQueryPlan.SelectItem item : plan.select()) {
            FieldDefinition field = schemaCatalog.resolveField(item.field(), scope).field();
            String dataIndex = SqlAstCompiler.outputColumnName(
                    item.aggregate(), item.alias(), field.columnName());
            String title = item.aggregate() == null || item.aggregate() == SqlQueryPlan.Aggregate.NONE
                    ? field.displayName() : item.aggregate().name() + "(" + field.displayName() + ")";
            columns.add(new ChatResponse.Column(title, dataIndex));
        }
        return List.copyOf(columns);
    }

    private List<Map<String, Object>> addFrontendKeys(List<Map<String, Object>> result) {
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
