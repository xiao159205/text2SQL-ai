package com.xym.antifraud.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Emits AG-UI compatible events while keeping business services transport agnostic. */
public final class AgUiEventPublisher {

    private static final AgUiEventPublisher NOOP = new AgUiEventPublisher(event -> { }, new ObjectMapper());

    private final Consumer<Map<String, Object>> sink;
    private final ObjectMapper objectMapper;

    public AgUiEventPublisher(Consumer<Map<String, Object>> sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    public static AgUiEventPublisher noop() {
        return NOOP;
    }

    public void runStarted(String threadId, String runId) {
        emit("RUN_STARTED", Map.of("threadId", threadId, "runId", runId));
    }

    public void runFinished(String threadId, String runId, Object result) {
        emit("RUN_FINISHED", Map.of("threadId", threadId, "runId", runId, "result", result));
    }

    public void runError(String message, String code) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", message);
        if (code != null && !code.isBlank()) fields.put("code", code);
        emit("RUN_ERROR", fields);
    }

    public void stepStarted(String stepName) {
        emit("STEP_STARTED", Map.of("stepName", stepName));
    }

    public void stepFinished(String stepName) {
        emit("STEP_FINISHED", Map.of("stepName", stepName));
    }

    /** Sends a concise execution summary, never hidden model chain-of-thought. */
    public void reasoning(String content) {
        String messageId = UUID.randomUUID().toString();
        emit("REASONING_START", Map.of("messageId", messageId));
        emit("REASONING_MESSAGE_START", Map.of("messageId", messageId, "role", "reasoning"));
        emit("REASONING_MESSAGE_CONTENT", Map.of("messageId", messageId, "delta", content));
        emit("REASONING_MESSAGE_END", Map.of("messageId", messageId));
        emit("REASONING_END", Map.of("messageId", messageId));
    }

    public String toolStarted(String toolName, Object arguments) {
        String toolCallId = UUID.randomUUID().toString();
        emit("TOOL_CALL_START", Map.of("toolCallId", toolCallId, "toolCallName", toolName));
        emit("TOOL_CALL_ARGS", Map.of("toolCallId", toolCallId, "delta", writeJson(arguments)));
        return toolCallId;
    }

    public void toolFinished(String toolCallId, Object result) {
        emit("TOOL_CALL_END", Map.of("toolCallId", toolCallId));
        emit("TOOL_CALL_RESULT", Map.of(
                "messageId", UUID.randomUUID().toString(),
                "toolCallId", toolCallId,
                "content", writeJson(result),
                "role", "tool"));
    }

    public void assistantMessage(String content) {
        String messageId = UUID.randomUUID().toString();
        emit("TEXT_MESSAGE_START", Map.of("messageId", messageId, "role", "assistant"));
        emit("TEXT_MESSAGE_CONTENT", Map.of("messageId", messageId, "delta", content));
        emit("TEXT_MESSAGE_END", Map.of("messageId", messageId));
    }

    private void emit(String type, Map<String, ?> fields) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("timestamp", Instant.now().toEpochMilli());
        event.putAll(fields);
        sink.accept(Map.copyOf(event));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }
}
