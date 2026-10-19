package com.xym.antifraud.service;

import com.xym.antifraud.config.KnowledgeFileProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentChunkerTest {
    @Test
    void splitsLongDocumentsAndKeepsOverlap() {
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(
                new KnowledgeFileProperties("./data/test", 1024, 10000, 300, 30, 20));
        String text = "第一段。" + "业务指标知识".repeat(80) + "\n\n第二段。" + "渠道销售口径".repeat(80);

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 301);
        String previousTail = chunks.get(0).substring(chunks.get(0).length() - 20);
        assertThat(chunks.get(1)).contains(previousTail);
    }
}
