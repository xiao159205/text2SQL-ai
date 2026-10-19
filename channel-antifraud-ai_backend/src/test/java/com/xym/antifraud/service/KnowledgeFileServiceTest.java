package com.xym.antifraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.config.KnowledgeFileProperties;
import com.xym.antifraud.config.KnowledgeProperties;
import com.xym.antifraud.dto.knowledge.KnowledgeFileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KnowledgeFileServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void uploadsParsesChunksEmbedsAndIndexesTextFile() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties knowledge = new KnowledgeProperties(true, "http://localhost:9200", "knowledge-test",
                false, 8, 8000, 30, 0.45, 0.55, 60, true, true, true,
                "https://api.siliconflow.cn/v1", "key", "Qwen/Qwen3-Embedding-8B", 3);
        KnowledgeFileProperties files = new KnowledgeFileProperties(
                tempDirectory.toString(), 1024 * 1024, 10000, 300, 30, 20);
        KnowledgeEmbeddingService embedding = mock(KnowledgeEmbeddingService.class);
        when(embedding.embedAll(anyList())).thenAnswer(invocation -> {
            List<String> inputs = invocation.getArgument(0);
            List<List<Double>> vectors = new ArrayList<>();
            inputs.forEach(ignored -> vectors.add(List.of(0.1, 0.2, 0.3)));
            return Optional.of(vectors);
        });
        ElasticsearchKnowledgeService knowledgeService = mock(ElasticsearchKnowledgeService.class);
        KnowledgeFileService service = new KnowledgeFileService(builder.build(), knowledge, files, embedding,
                knowledgeService, new KnowledgeDocumentChunker(files), new ObjectMapper().findAndRegisterModules());

        server.expect(requestTo("http://localhost:9200/knowledge-test-files"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://localhost:9200/knowledge-test-files"))
                .andRespond(withSuccess("{\"acknowledged\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/knowledge-test-files/_doc/")))
                .andExpect(content().string(containsString("PROCESSING")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/knowledge-test/_delete_by_query")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/_bulk?refresh=wait_for"))
                .andExpect(content().string(containsString("渠道销售指标口径")))
                .andRespond(withSuccess("{\"errors\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/knowledge-test-files/_doc/")))
                .andExpect(content().string(containsString("READY")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MockMultipartFile file = new MockMultipartFile("file", "risk-guide.txt", "text/plain",
                "渠道销售指标口径：按订单支付时间统计渠道销售额与客户数。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KnowledgeFileResponse response = service.upload(file, "mysql", "BUSINESS_TERM",
                List.of("sales_order"), List.of("渠道", "销售额"), "业务指标手册");

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.chunkCount()).isEqualTo(1);
        assertThat(tempDirectory.resolve(response.id() + ".txt")).exists();
        server.verify();
    }
}
