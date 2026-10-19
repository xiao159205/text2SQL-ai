package com.xym.antifraud.controller;

import com.xym.antifraud.dto.chat.ChatRequest;
import com.xym.antifraud.dto.chat.ChatTurnResponse;
import com.xym.antifraud.service.ChatService;
import com.xym.antifraud.service.ChatStreamService;
import com.xym.antifraud.service.UserSessionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 智能问答入口，只做参数接收，业务流程交给 ChatService。 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;
    private final UserSessionService userSessionService;

    public ChatController(ChatService chatService, ChatStreamService chatStreamService,
                          UserSessionService userSessionService) {
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
        this.userSessionService = userSessionService;
    }

    @PostMapping
    public ChatTurnResponse ask(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @Valid @RequestBody ChatRequest request) {
        Long userId = userSessionService.requireDataSourceAccess(authorization, request.database());
        return chatService.ask(userId, request.conversationId(),
                request.replaceFromMessageId(), request.question().trim(), request.database(),
                request.connectionId(), request.knowledgeBaseEnabled());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChatRequest request) {
        Long userId = userSessionService.requireDataSourceAccess(authorization, request.database());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(chatStreamService.stream(userId, request));
    }
}

