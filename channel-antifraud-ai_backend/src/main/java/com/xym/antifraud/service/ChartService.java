package com.xym.antifraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.config.AntifraudProperties;
import com.xym.antifraud.report.ChartSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 根据查询结果选择图表，并生成前端可直接使用的 ECharts option。 */
@Component
public class ChartService {

    private static final Logger log = LoggerFactory.getLogger(ChartService.class);
    private static final int MAX_MODEL_ROWS = 200;
    private static final int MAX_SERIES = 4;
    private static final String SYSTEM_PROMPT = """
            你是一个报表图表选择引擎。
            你会收到用户问题、查询结果的列定义以及数据库查询返回的真实数据行。
            你必须且只能调用一次 select_result_chart，不得使用普通文本回答。

            图表选择规则：
            - NONE：明细记录、仅有一行的结果、不适合绘图的数据结构，或者图表无法提供额外信息时选择。
            - LINE：用于展示随时间变化的趋势或有序序列。
            - BAR：用于比较不同类别或展示类别排名。
            - PIE：用于展示构成占比；只能包含一个数值列，类别数量最好不超过12个。
            - SCATTER：用于展示关系或分布；使用一个类别列或X轴列，以及一个数值型Y轴列。
            - 只能使用所提供列中完全一致的 dataIndex 值。
            - valueColumns 必须是查询结果中的数值型列，并且最多包含4个元素。
            - 禁止在函数调用中虚构、聚合、转换或复制数据。
            """;

    private final ChatClient chatClient;
    private final AntifraudProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChartService(ChatClient.Builder builder, AntifraudProperties properties) {
        this.chatClient = builder.build();
        this.properties = properties;
    }

    public ChatResponse.ChartResult recommend(
            String question,
            List<ChatResponse.Column> columns,
            List<Map<String, Object>> rows
    ) {
        // 模型只选择图表字段，数据和 option 始终由后端根据真实结果生成。
        int rowCount = rows == null ? 0 : rows.size();
        int columnCount = columns == null ? 0 : columns.size();
        log.info("[Chart-1 eligibility] start, rowCount={}, columnCount={}, columns={}",
                rowCount, columnCount, columns);
        if (rows == null || rows.size() < 2 || columns == null || columns.size() < 2) {
            log.info("[Chart-1 eligibility] skipped: chart requires at least 2 rows and 2 columns");
            return null;
        }

        ChartSelectionTool tool = new ChartSelectionTool();
        try {
            String payload = buildPayload(question, columns, rows);
            if (properties.logFullPrompt()) {
                log.info("[Chart-2 model request] systemPrompt=\n{}", SYSTEM_PROMPT);
                log.info("[Chart-2 model request] payload=\n{}", payload);
            } else {
                log.info("[Chart-2 model request] question={}, rowCount={}, modelRowCount={}, "
                                + "columns={}, payloadChars={}",
                        question, rows.size(), Math.min(rows.size(), MAX_MODEL_ROWS), columns,
                        payload.length());
            }

            long startedAt = System.nanoTime();
            String modelContent = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(payload)
                    .tools(tool)
                    .call()
                    .content();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            ChartSpec requested = tool.selection();
            if (properties.logModelResponse()) {
                log.info("[Chart-4 model result] elapsedMs={}, content={}, toolSelection={}",
                        elapsedMs, modelContent, requested);
            } else {
                log.info("[Chart-4 model result] elapsedMs={}, toolCalled={}, selectedType={}",
                        elapsedMs, requested != null,
                        requested == null ? null : requested.type());
            }
            if (requested == null) {
                log.warn("[Chart-4 model result] no select_result_chart function call was received; "
                        + "falling back to table-only display");
            }

            return buildResult(requested, columns, rows);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("[Chart-error] chart recommendation failed; falling back to table-only display, "
                            + "exceptionType={}, message={}",
                    exception.getClass().getName(), exception.getMessage(), exception);
            return null;
        }

    }
    ChatResponse.ChartResult buildResult(
            ChartSpec requested,
            List<ChatResponse.Column> columns,
            List<Map<String, Object>> rows
    ) {
        ChartSpec selection = validate(requested, columns, rows);
        if (selection == null) {
            log.info("[Chart-5 validation] no valid chart selection; tableOnly=true");
            return null;
        }

        Map<String, Object> option = buildOption(selection, columns, rows);
        log.info("[Chart-6 option] generated, type={}, title={}, categoryColumn={}, "
                        + "valueColumns={}, rowCount={}, optionKeys={}",
                selection.type(), selection.title(), selection.categoryColumn(),
                selection.valueColumns(), rows.size(), option.keySet());
        ChatResponse.ChartResult result = new ChatResponse.ChartResult(
                selection.type().name().toLowerCase(Locale.ROOT),
                selection.title().isBlank() ? "\u67e5\u8be2\u7ed3\u679c\u5206\u6790" : selection.title(),
                option);
        log.info("[Chart-7 response] chart included in API response, type={}, title={}",
                result.type(), result.title());
        return result;
    }

    private String buildPayload(
            String question,
            List<ChatResponse.Column> columns,
            List<Map<String, Object>> rows
    ) throws JsonProcessingException {
        Set<String> allowedColumns = columns.stream()
                .map(ChatResponse.Column::dataIndex)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> modelRows = rows.stream()
                .limit(MAX_MODEL_ROWS)
                .map(row -> {
                    Map<String, Object> sanitized = new LinkedHashMap<>();
                    allowedColumns.forEach(column -> sanitized.put(column, value(row, column)));
                    return sanitized;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("columns", columns);
        payload.put("rows", modelRows);
        payload.put("totalRowCount", rows.size());
        payload.put("rowsTruncated", rows.size() > modelRows.size());
        return objectMapper.writeValueAsString(payload);
    }

    private ChartSpec validate(
            ChartSpec selection,
            List<ChatResponse.Column> columns,
            List<Map<String, Object>> rows
    ) {
        // 工具调用也是模型输出，使用前仍要确认列存在且类型匹配。
        if (selection == null || selection.type() == ChartSpec.Type.NONE) {
            log.info("[Chart-5 validation] rejected, reason={}",
                    selection == null ? "function was not called" : "model selected NONE");
            return null;
        }

        log.info("[Chart-5 validation] validating selection={}", selection);
        Set<String> allowed = columns.stream()
                .map(ChatResponse.Column::dataIndex)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowed.contains(selection.categoryColumn())) {
            log.warn("[Chart-5 validation] rejected, reason=category column does not exist, "
                    + "categoryColumn={}, allowedColumns={}", selection.categoryColumn(), allowed);
            return null;
        }

        List<String> values = selection.valueColumns().stream()
                .filter(allowed::contains)
                .filter(column -> !column.equals(selection.categoryColumn()))
                .distinct()
                .limit(MAX_SERIES)
                .toList();
        if (values.isEmpty() || !values.stream().allMatch(column -> isNumericColumn(rows, column))) {
            log.warn("[Chart-5 validation] rejected, reason=value columns are missing or non-numeric, "
                            + "requestedValueColumns={}, acceptedValueColumns={}, allowedColumns={}",
                    selection.valueColumns(), values, allowed);
            return null;
        }
        if (selection.type() == ChartSpec.Type.PIE && values.size() != 1) {
            log.warn("[Chart-5 validation] rejected, reason=PIE requires exactly one value column, "
                    + "valueColumns={}", values);
            return null;
        }
        if (selection.type() == ChartSpec.Type.SCATTER && values.size() != 1) {
            log.warn("[Chart-5 validation] rejected, reason=SCATTER requires exactly one value column, "
                    + "valueColumns={}", values);
            return null;
        }

        ChartSpec validated = new ChartSpec(selection.type(), selection.title(),
                selection.categoryColumn(), values);
        log.info("[Chart-5 validation] accepted, selection={}", validated);
        return validated;
    }

    private Map<String, Object> buildOption(
            ChartSpec selection,
            List<ChatResponse.Column> columns,
            List<Map<String, Object>> rows
    ) {
        Map<String, String> titles = new LinkedHashMap<>();
        columns.forEach(column -> titles.put(column.dataIndex(), column.title()));
        return switch (selection.type()) {
            case PIE -> pieOption(selection, titles, rows);
            case LINE, BAR, SCATTER -> axisOption(selection, titles, rows);
            case NONE -> Map.of();
        };
    }

    private Map<String, Object> axisOption(
            ChartSpec selection,
            Map<String, String> titles,
            List<Map<String, Object>> rows
    ) {
        List<String> categories = rows.stream()
                .map(row -> displayValue(value(row, selection.categoryColumn())))
                .toList();
        List<Map<String, Object>> series = new ArrayList<>();
        for (String column : selection.valueColumns()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", titles.getOrDefault(column, column));
            item.put("type", switch (selection.type()) {
                case BAR -> "bar";
                case SCATTER -> "scatter";
                default -> "line";
            });
            item.put("data", rows.stream()
                    .map(row -> numericValue(value(row, column)))
                    .toList());
            if (selection.type() == ChartSpec.Type.LINE) {
                item.put("smooth", true);
                item.put("showSymbol", rows.size() <= 30);
            }
            if (selection.type() == ChartSpec.Type.BAR) {
                item.put("barMaxWidth", 42);
            }
            series.add(item);
        }

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("animationDuration", 500);
        option.put("color", List.of("#111111", "#5B7C99", "#C57B57", "#7B8F71"));
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("legend", Map.of("top", 0, "type", "scroll"));
        option.put("grid", Map.of("left", 18, "right", 18, "top", 46,
                "bottom", 12, "containLabel", true));
        option.put("xAxis", Map.of(
                "type", "category",
                "name", titles.getOrDefault(selection.categoryColumn(), selection.categoryColumn()),
                "data", categories,
                "axisLabel", Map.of("hideOverlap", true)));
        option.put("yAxis", Map.of("type", "value", "splitLine",
                Map.of("lineStyle", Map.of("color", "#ececec"))));
        option.put("series", series);
        return option;
    }

    private Map<String, Object> pieOption(
            ChartSpec selection,
            Map<String, String> titles,
            List<Map<String, Object>> rows
    ) {
        String valueColumn = selection.valueColumns().get(0);
        List<Map<String, Object>> data = rows.stream()
                .map(row -> Map.<String, Object>of(
                        "name", displayValue(value(row, selection.categoryColumn())),
                        "value", numericValue(value(row, valueColumn))))
                .toList();

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", titles.getOrDefault(valueColumn, valueColumn));
        series.put("type", "pie");
        series.put("radius", List.of("38%", "68%"));
        series.put("center", List.of("50%", "45%"));
        series.put("avoidLabelOverlap", true);
        series.put("itemStyle", Map.of("borderColor", "#ffffff", "borderWidth", 2));
        series.put("label", Map.of("formatter", "{b}\n{d}%"));
        series.put("data", data);

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("animationDuration", 500);
        option.put("color", List.of("#111111", "#5B7C99", "#C57B57", "#7B8F71",
                "#B99B6B", "#8B7E9E", "#6B8E8E", "#A86565"));
        option.put("tooltip", Map.of("trigger", "item", "formatter", "{b}: {c} ({d}%)"));
        option.put("legend", Map.of("bottom", 0, "type", "scroll"));
        option.put("series", List.of(series));
        return option;
    }

    private boolean isNumericColumn(List<Map<String, Object>> rows, String column) {
        boolean found = false;
        for (Map<String, Object> row : rows) {
            Object value = value(row, column);
            if (value == null) {
                continue;
            }
            if (!isNumeric(value)) {
                return false;
            }
            found = true;
        }
        return found;
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        try {
            new BigDecimal(value.toString());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private Object numericValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return value;
        }
        return new BigDecimal(value.toString());
    }

    private String displayValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Object value(Map<String, Object> row, String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        return row.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(column))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
