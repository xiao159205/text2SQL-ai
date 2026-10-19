package com.xym.antifraud.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 智能问答请求。 */
public record ChatRequest(
        Long conversationId,
        Long replaceFromMessageId,
        @NotBlank(message = "问题不能为空")
        @Size(max = 2000, message = "问题不能超过2000个字符")
        String question,
        String database,
        String connectionId,
        Boolean useKnowledgeBase
) {
    /** 兼容未传该字段的旧客户端，默认保持原有的知识库检索行为。 */
    public boolean knowledgeBaseEnabled() {
        return useKnowledgeBase == null || useKnowledgeBase;
    }
}

