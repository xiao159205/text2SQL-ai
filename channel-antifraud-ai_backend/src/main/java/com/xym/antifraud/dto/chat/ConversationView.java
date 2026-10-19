package com.xym.antifraud.dto.chat;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationView(
        Long id,
        String title,
        String database,
        LocalDateTime updatedAt
) {
    public record Message(
            Long id,
            String role,
            String content,
            ChatResponse result,
            LocalDateTime createdAt
    ) {
    }

    public record Detail(
            Long id,
            String title,
            String database,
            List<Message> messages
    ) {
    }
}
