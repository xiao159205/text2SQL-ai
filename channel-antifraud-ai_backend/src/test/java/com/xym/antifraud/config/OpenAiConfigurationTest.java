package com.xym.antifraud.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiConfigurationTest {

    @Test
    void configuresBothRootAndChatApiKeys() throws Exception {
        var source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .get(0);

        assertThat(source.getProperty("spring.ai.openai.api-key"))
                .asString().isNotBlank();
        assertThat(source.getProperty("spring.ai.openai.chat.api-key"))
                .asString().isNotBlank();
    }
}
