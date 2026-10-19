package com.xym.antifraud.service;

import com.xym.antifraud.config.KnowledgeProperties;
import com.xym.antifraud.dto.knowledge.KnowledgeSearchHit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticsearchKnowledgeServiceTest {

    @Test
    void searchesPublishedKnowledgeAndBuildsBusinessContext() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKnowledgeService service = new ElasticsearchKnowledgeService(
                builder.build(),
                new KnowledgeProperties(true, "http://localhost:9200", "knowledge-test",
                        false, 5, 8000, 50, 0.45, 0.55, 60,
                        true, true, false, "https://api.siliconflow.cn/v1", "",
                        "Qwen/Qwen3-Embedding-8B", 1536));

        String response = """
                {
                  "hits": {
                    "hits": [{
                      "_id": "high-risk-customer",
                      "_score": 8.5,
                      "_source": {
                        "title": "高风险客户口径",
                        "content": "按客户号去重，并排除测试数据。",
                        "knowledgeType": "METRIC",
                        "database": "mysql",
                        "tableNames": ["ebank_transaction_risk_detail"],
                        "keywords": ["高风险客户"],
                        "source": "业务指标手册"
                      }
                    }]
                  }
                }
                """;
        server.expect(requestTo("http://localhost:9200/knowledge-test/_search"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PUBLISHED")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        List<KnowledgeSearchHit> hits = service.search("查询高风险客户", "mysql");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo("high-risk-customer");
        assertThat(hits.get(0).tableNames())
                .containsExactly("ebank_transaction_risk_detail");
        server.verify();
    }

    @Test
    void fusesBm25AndVectorCandidateRanks() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = new KnowledgeProperties(true, "http://localhost:9200", "knowledge-test",
                false, 5, 8000, 30, 0.5, 0.5, 60,
                false, false, true, "http://embedding", "key", "model", 2);
        KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        when(embeddingService.embed(anyString())).thenReturn(Optional.of(List.of(0.1, 0.2)));
        ElasticsearchKnowledgeService service = new ElasticsearchKnowledgeService(
                builder.build(), properties, embeddingService);

        String bm25 = hits("bm25-only", "shared");
        String vector = hits("vector-only", "shared");
        server.expect(requestTo("http://localhost:9200/knowledge-test/_search"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("multi_match")))
                .andRespond(withSuccess(bm25, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/knowledge-test/_search"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cosineSimilarity")))
                .andRespond(withSuccess(vector, MediaType.APPLICATION_JSON));

        List<KnowledgeSearchHit> hits = service.search("risk customer", "mysql");

        assertThat(hits).extracting(KnowledgeSearchHit::id)
                .containsExactly("shared", "bm25-only", "vector-only");
        assertThat(service.theoreticalMaxRrfScore()).isEqualTo(1.0 / 61.0);
        server.verify();
    }

    @Test
    void searchesEveryDatabaseWhenFrontendSelectsAll() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = properties(false, 1536);
        ElasticsearchKnowledgeService service = new ElasticsearchKnowledgeService(builder.build(), properties);
        server.expect(requestTo("http://localhost:9200/knowledge-test/_search"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\\\"database\\\""))))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(service.search("销售额", "all")).isEmpty();
        server.verify();
    }

    private static KnowledgeProperties properties(boolean embeddingEnabled, int dimensions) {
        return new KnowledgeProperties(true, "http://localhost:9200", "knowledge-test",
                false, 5, 8000, 30, 0.5, 0.5, 60,
                false, false, embeddingEnabled, "https://api.siliconflow.cn/v1",
                embeddingEnabled ? "key" : "", "Qwen/Qwen3-Embedding-8B", dimensions);
    }

    private static String hits(String first, String second) {
        return """
                {"hits":{"hits":[
                  {"_id":"%s","_score":9,"_source":{"title":"first","content":"a","knowledgeType":"GENERAL","database":"all","tableNames":[],"keywords":[],"source":"test"}},
                  {"_id":"%s","_score":8,"_source":{"title":"second","content":"b","knowledgeType":"GENERAL","database":"all","tableNames":[],"keywords":[],"source":"test"}}
                ]}}
                """.formatted(first, second);
    }
}
