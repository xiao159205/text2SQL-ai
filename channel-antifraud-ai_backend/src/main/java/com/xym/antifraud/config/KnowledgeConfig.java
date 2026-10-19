package com.xym.antifraud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KnowledgeConfig {

    @Bean("knowledgeRestClient")
    RestClient knowledgeRestClient(KnowledgeProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    @Bean("embeddingRestClient")
    RestClient embeddingRestClient(KnowledgeProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.embeddingBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory());
        if (!properties.embeddingApiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION,
                    "Bearer " + properties.embeddingApiKey());
        }
        return builder.build();
    }
}
