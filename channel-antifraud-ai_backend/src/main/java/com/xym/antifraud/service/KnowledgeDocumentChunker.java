package com.xym.antifraud.service;

import com.xym.antifraud.config.KnowledgeFileProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeDocumentChunker {
    private final KnowledgeFileProperties properties;

    public KnowledgeDocumentChunker(KnowledgeFileProperties properties) {
        this.properties = properties;
    }

    public List<String> chunk(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ").replaceAll("\n{3,}", "\n\n").trim();
        if (normalized.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < properties.maxChunks()) {
            int targetEnd = Math.min(start + properties.chunkChars(), normalized.length());
            int end = targetEnd;
            if (targetEnd < normalized.length()) {
                int minimumBoundary = start + properties.chunkChars() * 3 / 5;
                int paragraph = normalized.lastIndexOf("\n\n", targetEnd);
                int line = normalized.lastIndexOf('\n', targetEnd);
                int sentence = Math.max(normalized.lastIndexOf('。', targetEnd),
                        normalized.lastIndexOf('.', targetEnd));
                int boundary = Math.max(paragraph, Math.max(line, sentence));
                if (boundary >= minimumBoundary) end = boundary + 1;
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= normalized.length()) {
                start = normalized.length();
                break;
            }
            int next = end - properties.chunkOverlapChars();
            start = Math.max(start + 1, next);
        }
        if (start < normalized.length()) {
            throw new IllegalArgumentException("文档切片数超过上限 " + properties.maxChunks() + "，请拆分文件后上传");
        }
        return List.copyOf(chunks);
    }
}
