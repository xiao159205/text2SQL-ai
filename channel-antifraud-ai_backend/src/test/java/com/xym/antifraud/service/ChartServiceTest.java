package com.xym.antifraud.service;

import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.config.AntifraudProperties;
import com.xym.antifraud.report.ChartSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChartServiceTest {

    private ChartService service;
    private List<ChatResponse.Column> columns;
    private List<Map<String, Object>> rows;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        AntifraudProperties properties = new AntifraudProperties(
                List.of(), 300, 5000, 1000, 30, 5,
                false, false, false, 20);
        service = new ChartService(builder, properties);
        columns = List.of(
                new ChatResponse.Column("Category", "category"),
                new ChatResponse.Column("Count", "count"));
        rows = List.of(
                Map.of("category", "Food", "count", 12L),
                Map.of("category", "Clothing", "count", 8L));
    }

    @Test
    void buildsBarOptionFromActualQueryRows() {
        ChatResponse.ChartResult result = service.buildResult(
                new ChartSpec(ChartSpec.Type.BAR, "Category ranking",
                        "category", List.of("count")),
                columns, rows);

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo("bar");
        Map<?, ?> xAxis = (Map<?, ?>) result.option().get("xAxis");
        assertThat(xAxis.get("data")).isEqualTo(List.of("Food", "Clothing"));

        List<?> series = (List<?>) result.option().get("series");
        Map<?, ?> firstSeries = (Map<?, ?>) series.get(0);
        assertThat(firstSeries.get("data")).isEqualTo(List.of(12L, 8L));
    }

    @Test
    void rejectsInventedOrNonNumericColumns() {
        assertThat(service.buildResult(
                new ChartSpec(ChartSpec.Type.LINE, "", "category", List.of("invented")),
                columns, rows)).isNull();

        assertThat(service.buildResult(
                new ChartSpec(ChartSpec.Type.LINE, "", "count", List.of("category")),
                columns, rows)).isNull();
    }

    @Test
    void keepsTableOnlyWhenModelSelectsNone() {
        assertThat(service.buildResult(
                new ChartSpec(ChartSpec.Type.NONE, "", "", List.of()),
                columns, rows)).isNull();
    }
}
