package com.xym.antifraud.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 智能问答请求。 */
public record ChatRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 2000, message = "问题不能超过2000个字符")
        String question,
        String database
) {
}

