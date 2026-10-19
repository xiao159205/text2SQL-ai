package com.xym.antifraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.dto.chat.ChatRequest;
import com.xym.antifraud.dto.chat.ChatTurnResponse;
import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.stream.AgUiEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatStreamService {
    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private final ChatService chatService;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper;

    public ChatStreamService(ChatService chatService,
                             @Qualifier("chatStreamExecutor") TaskExecutor executor,
                             ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public SseEmitter stream(Long userId, ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean connected = new AtomicBoolean(true);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onTimeout(() -> connected.set(false));
        emitter.onError(error -> connected.set(false));

        AgUiEventPublisher events = new AgUiEventPublisher(
                event -> send(emitter, connected, event), objectMapper);
        String runId = UUID.randomUUID().toString();
        String threadId = request.conversationId() == null
                ? "new-" + runId : request.conversationId().toString();
        events.runStarted(threadId, runId);

        executor.execute(() -> {
            try {
                ChatTurnResponse turn = chatService.ask(userId, request.conversationId(),
                        request.replaceFromMessageId(), request.question().trim(),
                        request.database(), request.connectionId(),
                        request.knowledgeBaseEnabled(), events);
                events.assistantMessage(turn.result().summary());
                events.runFinished(threadId, runId, turn);
                emitter.complete();
            } catch (RuntimeException | LinkageError exception) {
                String code = exception instanceof BusinessException business
                        ? business.getCode() : "CHAT_STREAM_ERROR";
                String message = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? "智能问答处理失败，请稍后重试" : exception.getMessage();
                log.warn("[Chat stream failed] runId={}, code={}, message={}",
                        runId, code, message, exception);
                events.runError(message, code);
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AtomicBoolean connected, Map<String, Object> event) {
        if (!connected.get()) return;
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException | IllegalStateException exception) {
            connected.set(false);
            log.debug("[Chat stream disconnected] {}", exception.getMessage());
        }
    }
}
