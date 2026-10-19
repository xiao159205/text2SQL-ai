package com.xym.antifraud.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "app.knowledge.enabled=true",
                    "app.knowledge.base-url=http://localhost:9200",
                    "app.knowledge.index-name=knowledge-test",
                    "app.knowledge.embedding-enabled=true",
                    "app.knowledge.embedding-base-url=https://api.siliconflow.cn/v1",
                    "app.knowledge.embedding-api-key=test-key",
                    "app.knowledge.embedding-model=Qwen/Qwen3-Embedding-8B",
                    "app.knowledge.embedding-dimensions=1536");

    @Test
    void bindsRecordUsingItsCanonicalConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            KnowledgeProperties properties = context.getBean(KnowledgeProperties.class);
            assertThat(properties.embeddingEnabled()).isTrue();
            assertThat(properties.embeddingBaseUrl()).isEqualTo("https://api.siliconflow.cn/v1");
            assertThat(properties.embeddingModel()).isEqualTo("Qwen/Qwen3-Embedding-8B");
            assertThat(properties.embeddingDimensions()).isEqualTo(1536);
        });
    }

    @EnableConfigurationProperties(KnowledgeProperties.class)
    static class TestConfiguration {
    }
}
