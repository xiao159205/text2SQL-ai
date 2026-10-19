package com.xym.antifraud.service;

import com.xym.antifraud.ai.SqlPlanGenerator;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.entity.PromptConfig;
import com.xym.antifraud.mapper.PromptConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptConfigServiceTest {

    private PromptConfigMapper mapper;
    private PromptConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PromptConfigMapper.class);
        service = new PromptConfigService(mapper);
    }

    @Test
    void returnsBuiltInDefaultWhenNoOverrideExists() {
        PromptConfigService.PromptView result = service.get(DatabaseType.HIVE);

        assertThat(result.database()).isEqualTo("hive");
        assertThat(result.content()).isEqualTo(
                SqlPlanGenerator.defaultSystemPrompt(DatabaseType.HIVE));
        assertThat(result.defaultContent()).isEqualTo(result.content());
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void savesAndReturnsDatabaseOverride() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 25, 10, 30);
        PromptConfig saved = configured("MYSQL", "custom {schema} {format} {maxRows}", updatedAt);
        when(mapper.selectByDatabaseType("MYSQL")).thenReturn(saved);

        PromptConfigService.PromptView result = service.save(
                DatabaseType.MYSQL, saved.getContent());

        verify(mapper).upsert("MYSQL", saved.getContent());
        assertThat(result.content()).isEqualTo(saved.getContent());
        assertThat(result.defaultContent()).isNotEqualTo(saved.getContent());
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void resetDeletesOverrideAndReturnsBuiltInDefault() {
        PromptConfigService.PromptView result = service.reset(DatabaseType.MYSQL);

        verify(mapper).deleteByDatabaseType("MYSQL");
        assertThat(result.content()).isEqualTo(
                SqlPlanGenerator.defaultSystemPrompt(DatabaseType.MYSQL));
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> service.save(DatabaseType.MYSQL, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("提示词内容不能为空");
    }

    private PromptConfig configured(String databaseType, String content,
                                    LocalDateTime updatedAt) {
        PromptConfig config = new PromptConfig();
        config.setId(1L);
        config.setDatabaseType(databaseType);
        config.setContent(content);
        config.setUpdatedAt(updatedAt);
        return config;
    }
}
