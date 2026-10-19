package com.xym.antifraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat-memory")
public record ChatMemoryProperties(int recentRounds) {
    public ChatMemoryProperties {
        recentRounds = recentRounds <= 0 ? 6 : Math.min(recentRounds, 20);
    }
}
