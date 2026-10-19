package com.xym.antifraud.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeDocumentRequest(
        String id,
        @NotBlank(message = "知识标题不能为空")
        @Size(max = 200, message = "知识标题不能超过200个字符")
        String title,
        @NotBlank(message = "知识内容不能为空")
        @Size(max = 20000, message = "单条知识内容不能超过20000个字符")
        String content,
        String knowledgeType,
        String database,
        List<String> tableNames,
        List<String> keywords,
        String source
) {
}
