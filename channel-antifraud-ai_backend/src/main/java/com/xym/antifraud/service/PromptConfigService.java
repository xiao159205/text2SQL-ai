package com.xym.antifraud.service;

import com.xym.antifraud.ai.SqlPlanGenerator;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.entity.PromptConfig;
import com.xym.antifraud.mapper.PromptConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 管理 MySQL/Hive 各自的 System Prompt 覆盖配置。 */
@Service
public class PromptConfigService {

    private static final Logger log = LoggerFactory.getLogger(PromptConfigService.class);
    private static final int MAX_CONTENT_LENGTH = 200_000;

    private final PromptConfigMapper mapper;

    public PromptConfigService(PromptConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 返回当前生效内容；没有数据库覆盖配置时使用代码内置默认值。 */
    public String content(DatabaseType database) {
        PromptConfig configured = mapper.selectByDatabaseType(database.name());
        return configured == null
                ? SqlPlanGenerator.defaultSystemPrompt(database)
                : configured.getContent();
    }

    public PromptView get(DatabaseType database) {
        String defaultContent = SqlPlanGenerator.defaultSystemPrompt(database);
        PromptConfig configured = mapper.selectByDatabaseType(database.name());
        if (configured == null) {
            return new PromptView(database.name().toLowerCase(), defaultContent,
                    defaultContent, null);
        }
        return new PromptView(database.name().toLowerCase(), configured.getContent(),
                defaultContent, configured.getUpdatedAt());
    }

    public PromptView save(DatabaseType database, String content) {
        validate(content);
        mapper.upsert(database.name(), content);
        PromptConfig saved = mapper.selectByDatabaseType(database.name());
        if (saved == null) {
            throw new IllegalStateException("提示词保存后未能读取到配置: " + database);
        }
        log.info("[提示词配置] 保存完成: database={}, chars={}", database, content.length());
        return new PromptView(database.name().toLowerCase(), saved.getContent(),
                SqlPlanGenerator.defaultSystemPrompt(database), saved.getUpdatedAt());
    }

    /** 删除数据库覆盖记录，使该数据库类型重新跟随代码内置默认提示词。 */
    public PromptView reset(DatabaseType database) {
        mapper.deleteByDatabaseType(database.name());
        log.info("[提示词配置] 已恢复内置默认值: database={}", database);
        String defaultContent = SqlPlanGenerator.defaultSystemPrompt(database);
        return new PromptView(database.name().toLowerCase(), defaultContent,
                defaultContent, null);
    }

    private void validate(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("提示词内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("提示词内容不能超过" + MAX_CONTENT_LENGTH + "个字符");
        }
    }

    public record PromptView(String database, String content, String defaultContent,
                             LocalDateTime updatedAt) {
    }
}
