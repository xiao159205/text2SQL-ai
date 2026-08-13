package com.xym.antifraud.controller;

import com.xym.antifraud.dto.chat.ChatRequest;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 智能问答入口，只做参数接收，业务流程交给 ChatService。 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request.question().trim(), request.database());
    }
}

