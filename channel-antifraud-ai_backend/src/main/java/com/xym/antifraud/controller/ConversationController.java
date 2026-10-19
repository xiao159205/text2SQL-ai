package com.xym.antifraud.controller;

import com.xym.antifraud.dto.chat.ConversationView;
import com.xym.antifraud.service.ChatHistoryService;
import com.xym.antifraud.service.UserSessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ChatHistoryService historyService;
    private final UserSessionService userSessionService;

    public ConversationController(ChatHistoryService historyService,
                                  UserSessionService userSessionService) {
        this.historyService = historyService;
        this.userSessionService = userSessionService;
    }

    @GetMapping
    public List<ConversationView> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return historyService.list(userSessionService.requireUserId(authorization));
    }

    @GetMapping("/{id}")
    public ConversationView.Detail detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return historyService.detail(userSessionService.requireUserId(authorization), id);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        historyService.delete(userSessionService.requireUserId(authorization), id);
    }
}
