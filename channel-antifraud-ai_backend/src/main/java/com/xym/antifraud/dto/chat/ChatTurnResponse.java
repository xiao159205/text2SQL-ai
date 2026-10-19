package com.xym.antifraud.dto.chat;

public record ChatTurnResponse(
        Long conversationId,
        Long userMessageId,
        Long assistantMessageId,
        ChatResponse result
) {
}
