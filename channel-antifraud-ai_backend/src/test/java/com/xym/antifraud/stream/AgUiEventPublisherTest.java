package com.xym.antifraud.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgUiEventPublisherTest {

    @Test
    void emitsAgUiLifecycleReasoningAndToolSequence() {
        List<Map<String, Object>> events = new ArrayList<>();
        AgUiEventPublisher publisher = new AgUiEventPublisher(events::add, new ObjectMapper());

        publisher.runStarted("thread-1", "run-1");
        publisher.reasoning("正在识别意图");
        String toolCallId = publisher.toolStarted("route_chat_intent", Map.of("question", "统计交易"));
        publisher.toolFinished(toolCallId, Map.of("intent", "DATA_QUERY"));
        publisher.assistantMessage("查询完成");
        publisher.runFinished("thread-1", "run-1", Map.of("result", "ok"));

        assertThat(events).extracting(event -> event.get("type")).containsExactly(
                "RUN_STARTED",
                "REASONING_START", "REASONING_MESSAGE_START", "REASONING_MESSAGE_CONTENT",
                "REASONING_MESSAGE_END", "REASONING_END",
                "TOOL_CALL_START", "TOOL_CALL_ARGS", "TOOL_CALL_END", "TOOL_CALL_RESULT",
                "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END",
                "RUN_FINISHED");
        assertThat(events.get(0)).containsEntry("threadId", "thread-1")
                .containsEntry("runId", "run-1");
        assertThat(events.get(6)).containsEntry("toolCallName", "route_chat_intent")
                .containsEntry("toolCallId", toolCallId);
        assertThat(events).allSatisfy(event -> assertThat(event).containsKeys("type", "timestamp"));
    }

    @Test
    void emitsStructuredRunError() {
        List<Map<String, Object>> events = new ArrayList<>();
        AgUiEventPublisher publisher = new AgUiEventPublisher(events::add, new ObjectMapper());

        publisher.runError("数据库不可用", "DATABASE_ERROR");

        assertThat(events).singleElement().satisfies(event -> assertThat(event)
                .containsEntry("type", "RUN_ERROR")
                .containsEntry("message", "数据库不可用")
                .containsEntry("code", "DATABASE_ERROR"));
    }
}
