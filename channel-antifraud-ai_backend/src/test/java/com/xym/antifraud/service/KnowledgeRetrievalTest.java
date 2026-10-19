package com.xym.antifraud.service;

import com.xym.antifraud.dto.knowledge.KnowledgeSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrievalTest {

    @Test
    void exposesActualKnowledgeHitsAsCompactRoutingEvidence() {
        KnowledgeSearchHit hit = new KnowledgeSearchHit(
                "doc-1", 0.91, "Spark分布式荔枝分类方案", "第一步先准备荔枝图片数据。\n第二步训练模型。",
                "TECHNICAL_GUIDE", "mysql", List.of(), List.of("Spark"), "内部方案.docx");
        KnowledgeRetrieval retrieval = new KnowledgeRetrieval(
                KnowledgeRetrieval.Status.READY, "Spark 荔枝 分类", List.of(hit), "完整上下文");

        assertThat(retrieval.routingEvidence())
                .contains("检索状态: READY")
                .contains("命中数量: 1")
                .contains("Spark分布式荔枝分类方案")
                .contains("内部方案.docx")
                .contains("第一步先准备荔枝图片数据。 第二步训练模型。");
    }
}
