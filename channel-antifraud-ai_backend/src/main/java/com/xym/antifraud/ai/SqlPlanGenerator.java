package com.xym.antifraud.ai;

import com.xym.antifraud.config.AntifraudProperties;
import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.SchemaCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/** 让大模型把自然语言问题转换为受约束的查询计划，而不是直接生成 SQL。 */
@Component
public class SqlPlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlPlanGenerator.class);
    private static final String SYSTEM_PROMPT = """
            你是通用MySQL Text2SQL查询规划器。你会收到当前数据库的实时Schema和用户问题。
            请在内部完成SQL设计，但只返回SqlQueryPlan结构化对象，不返回SQL字符串。

            强制规则：
            1. 只允许SELECT；只能使用Schema里存在的表和字段。
            2. from是主表；跨表查询通过joins表达，只允许INNER或LEFT等值JOIN。
            3. 每张表使用简短安全的英文别名。多表查询的所有字段都写成“别名.字段名”。
            4. JOIN的leftField引用已经进入查询范围的表，rightField引用本次新加入的表。
            5. SELECT结果列必须有唯一名称；多表同名字段必须设置不同的安全英文alias。
            6. 用户输入值只能放入Condition.values或HavingCondition.value，不得放SQL片段。
            7. 累计金额、次数、客户汇总等使用GROUP BY、聚合SELECT和HAVING。
            8. HOUR_EQ和HOUR_BETWEEN只用于MySQL DATE/DATETIME/TIMESTAMP/TIME字段。
            9. 无法确定表、关联关系、字段或业务口径时，返回NEED_CLARIFICATION并提出一个具体问题。
            10. READY时clarification为空字符串；默认limit为100，最大为{maxRows}。
            11. 不得臆造表、字段、关联键或业务含义。

            当前数据库Schema：
            {schema}

            严格按照以下JSON格式返回，不要添加Markdown代码块或解释文字：
            {format}
            """;

    private final ChatClient chatClient;
    private final SchemaCatalog schemaCatalog;
    private final AntifraudProperties properties;
    private final BeanOutputConverter<SqlQueryPlan> outputConverter =
            new BeanOutputConverter<>(SqlQueryPlan.class);

    public SqlPlanGenerator(ChatClient.Builder builder, SchemaCatalog schemaCatalog,
                            AntifraudProperties properties) {
        this.chatClient = builder.build();
        this.schemaCatalog = schemaCatalog;
        this.properties = properties;
    }

    public SqlQueryPlan generate(String question) {
        // 每次都取当前生效的 Schema，黑名单和注释修改会影响下一次提问。
        String schema = schemaCatalog.promptSchema();
        String format = outputConverter.getFormat();
        if (properties.logFullPrompt()) {
            String renderedSystemPrompt = SYSTEM_PROMPT
                    .replace("{maxRows}", String.valueOf(properties.maxResultRows()))
                    .replace("{schema}", schema)
                    .replace("{format}", format);
            log.info("[发送给大模型-System完整提示]\n{}", renderedSystemPrompt);
            log.info("[发送给大模型-User问题]\n{}", question);
        } else {
            log.info("[发送给大模型] question={}, schemaChars={}, formatChars={}",
                    question, schema.length(), format.length());
        }

        long startedAt = System.nanoTime();
        String rawResponse = chatClient.prompt()
                .system(system -> system.text(SYSTEM_PROMPT)
                        .param("maxRows", properties.maxResultRows())
                        .param("schema", schema)
                        .param("format", format))
                .user(question)
                .call()
                .content();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        if (properties.logModelResponse()) {
            log.info("[大模型原始返回] elapsedMs={}\n{}", elapsedMs, rawResponse);
        } else {
            log.info("[大模型返回完成] elapsedMs={}, responseChars={}",
                    elapsedMs, rawResponse == null ? 0 : rawResponse.length());
        }

        try {
            SqlQueryPlan plan = outputConverter.convert(rawResponse);
            log.info("[大模型返回解析为AST] {}", plan);
            return plan;
        } catch (RuntimeException exception) {
            log.error("[大模型返回解析失败] rawResponse={}", rawResponse, exception);
            throw exception;
        }
    }
}
