package com.xym.antifraud.service;

import com.xym.antifraud.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class RagQueryRewriter {
    private static final Logger log = LoggerFactory.getLogger(RagQueryRewriter.class);
    private final ChatClient chatClient;
    private final KnowledgeProperties properties;

    public RagQueryRewriter(ChatClient.Builder builder, KnowledgeProperties properties) {
        this.chatClient = builder.build();
        this.properties = properties;
    }

    public String rewrite(String question) {
        if (!properties.queryRewriteEnabled()) return question;
        try {
            String rewritten = chatClient.prompt()
                    .system("""
                            你是检索查询改写器。消除口语和指代，补充常见同义词，使查询适合语义检索与BM25检索。
                            不回答问题，不添加未知事实，只输出一行改写后的查询，最长300字。
                            """)
                    .user(question).call().content();
            if (rewritten == null || rewritten.isBlank()) return question;
            rewritten = rewritten.strip().replaceAll("[\\r\\n]+", " ");
            return rewritten.length() > 300 ? rewritten.substring(0, 300) : rewritten;
        } catch (RuntimeException exception) {
            log.warn("[RAG query rewrite fallback] {}", exception.getMessage());
            return question;
        }
    }
}
