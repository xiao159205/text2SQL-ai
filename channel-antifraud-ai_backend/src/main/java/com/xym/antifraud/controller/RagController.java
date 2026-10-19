package com.xym.antifraud.controller;

import com.xym.antifraud.dto.knowledge.RagAnswerResponse;
import com.xym.antifraud.dto.knowledge.RagQuestionRequest;
import com.xym.antifraud.service.RagService;
import com.xym.antifraud.service.UserSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService ragService;
    private final UserSessionService userSessionService;

    public RagController(RagService ragService, UserSessionService userSessionService) {
        this.ragService = ragService;
        this.userSessionService = userSessionService;
    }

    @PostMapping("/ask")
    public RagAnswerResponse ask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RagQuestionRequest request) {
        userSessionService.requireUserId(authorization);
        return ragService.ask(request.question().trim(),
                request.database() == null || request.database().isBlank() ? "all" : request.database().trim());
    }
}
