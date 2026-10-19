package com.xym.antifraud.service;

import com.xym.antifraud.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KnowledgeEmbeddingServiceTest {

    @Test
    void sendsSiliconFlowModelAndConfiguredDimensions() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.siliconflow.cn/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = new KnowledgeProperties(
                true, "http://localhost:9200", "knowledge-test", false,
                8, 8000, 50, 0.45, 0.55, 60, true, true,
                true, "https://api.siliconflow.cn/v1", "test-key",
                "Qwen/Qwen3-Embedding-8B", 3);
        KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(builder.build(), properties);
        server.expect(requestTo("https://api.siliconflow.cn/v1/embeddings"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Qwen/Qwen3-Embedding-8B"),
                        org.hamcrest.Matchers.containsString("\"dimensions\":3"),
                        org.hamcrest.Matchers.containsString("\"encoding_format\":\"float\""))))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.1,0.2,0.3]}]}
                        """, MediaType.APPLICATION_JSON));

        List<Double> vector = service.embed("测试知识").orElseThrow();

        assertThat(vector).containsExactly(0.1, 0.2, 0.3);
        server.verify();
    }
}
