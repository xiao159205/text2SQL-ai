package com.xym.antifraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 服务启动入口。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ChannelAntifraudAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChannelAntifraudAiApplication.class, args);
    }
}
