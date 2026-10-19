package com.xym.antifraud.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagQuestionRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 2000, message = "问题不能超过2000个字符")
        String question,
        String database
) {
}
