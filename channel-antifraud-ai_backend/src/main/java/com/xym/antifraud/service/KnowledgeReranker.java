package com.xym.antifraud.service;

import com.xym.antifraud.config.KnowledgeProperties;
import com.xym.antifraud.dto.knowledge.KnowledgeSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeReranker {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeReranker.class);
    private final ChatClient chatClient;
    private final KnowledgeProperties properties;
    private final BeanOutputConverter<RerankOrder> converter = new BeanOutputConverter<>(RerankOrder.class);

    public KnowledgeReranker(ChatClient.Builder builder, KnowledgeProperties properties) {
        this.chatClient = builder.build();
        this.properties = properties;
    }

    public List<KnowledgeSearchHit> rerank(String question, List<KnowledgeSearchHit> candidates) {
        if (candidates.isEmpty()) return List.of();
        if (!properties.rerankEnabled()) return candidates.stream().limit(properties.topK()).toList();
        try {
            StringBuilder candidateText = new StringBuilder();
            for (KnowledgeSearchHit hit : candidates) {
                String content = hit.content().length() > 800 ? hit.content().substring(0, 800) : hit.content();
                candidateText.append("ID=").append(hit.id()).append("\n标题=").append(hit.title())
                        .append("\n关键词=").append(hit.keywords()).append("\n内容=").append(content).append("\n---\n");
            }
            String response = chatClient.prompt().system(system -> system.text("""
                            你是知识库相关性重排器。根据用户问题，按相关性从高到低选择文档。
                            只允许返回候选中真实存在的ID，不得回答问题。严格遵循JSON格式：{format}
                            """).param("format", converter.getFormat()))
                    .user("问题：\n" + question + "\n\n候选文档：\n" + candidateText)
                    .call().content();
            RerankOrder order = converter.convert(response);
            return select(order == null ? List.of() : order.ids(), candidates);
        } catch (RuntimeException exception) {
            log.warn("[RAG rerank fallback] {}", exception.getMessage());
            return candidates.stream().limit(properties.topK()).toList();
        }
    }

    private List<KnowledgeSearchHit> select(List<String> ids, List<KnowledgeSearchHit> candidates) {
        Map<String, KnowledgeSearchHit> byId = new LinkedHashMap<>();
        candidates.forEach(hit -> byId.put(hit.id(), hit));
        List<KnowledgeSearchHit> result = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                KnowledgeSearchHit hit = byId.remove(id);
                if (hit != null && result.size() < properties.topK()) result.add(hit);
            }
        }
        for (KnowledgeSearchHit hit : byId.values()) {
            if (result.size() >= properties.topK()) break;
            result.add(hit);
        }
        return List.copyOf(result);
    }

    public record RerankOrder(List<String> ids) {
        public RerankOrder { ids = ids == null ? List.of() : List.copyOf(ids); }
    }
}
