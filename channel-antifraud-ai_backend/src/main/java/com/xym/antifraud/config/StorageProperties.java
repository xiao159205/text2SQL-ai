package com.xym.antifraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String avatarDirectory) {

    public StorageProperties {
        avatarDirectory = avatarDirectory == null || avatarDirectory.isBlank()
                ? "../channel-antifraud-ai-frontend/src/asserts" : avatarDirectory.trim();
    }
}
