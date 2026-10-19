package com.xym.antifraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.config.ChatMemoryProperties;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.dto.chat.ConversationView;
import com.xym.antifraud.entity.ChatConversation;
import com.xym.antifraud.entity.ChatMessage;
import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.mapper.ChatHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {

    private ChatHistoryMapper mapper;
    private ChatHistoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ChatHistoryMapper.class);
        service = new ChatHistoryService(mapper, new ObjectMapper().findAndRegisterModules(),
                new ChatMemoryProperties(6));
    }

    @Test
    void createsConversationWithQuestionAsTitle() {
        when(mapper.insertConversation(any())).thenAnswer(invocation -> {
            ChatConversation conversation = invocation.getArgument(0);
            conversation.setId(21L);
            return 1;
        });

        ChatConversation result = service.getOrCreate(7L, null, "mysql",
                "查询最近30天存在夜间大额交易并且发生异地登录行为的高风险客户名单并按金额排序");

        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getDatabaseType()).isEqualTo("mysql");
        assertThat(result.getTitle()).endsWith("…").hasSize(31);
    }

    @Test
    void buildsContextWithPreviousQuestionPlanAndSql() {
        ChatMessage user = message("USER", "查询最近30天高风险客户", null, null);
        ChatMessage assistant = message("ASSISTANT", "共找到20条结果",
                "{\"status\":\"READY\",\"where\":[]}", "SELECT * FROM customer");
        when(mapper.selectRecentMessages(9L, null, 12)).thenReturn(List.of(user, assistant));

        String context = service.context(9L, null);

        assertThat(context)
                .contains("用户：查询最近30天高风险客户")
                .contains("该轮完整查询计划")
                .contains("SELECT * FROM customer");
    }

    @Test
    void restoresPersistedAssistantResponse() throws Exception {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(9L);
        conversation.setUserId(7L);
        conversation.setTitle("历史查询");
        conversation.setDatabaseType("mysql");
        when(mapper.selectConversation(9L, 7L)).thenReturn(conversation);

        ChatResponse response = new ChatResponse("查询完成", "SELECT 1",
                new ChatResponse.TableResult(List.of(), List.of()), null, null);
        ChatMessage assistant = message("ASSISTANT", "查询完成", null, "SELECT 1");
        assistant.setId(13L);
        assistant.setResponseJson(new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(response));
        when(mapper.selectMessages(9L)).thenReturn(List.of(assistant));

        ConversationView.Detail detail = service.detail(7L, 9L);

        assertThat(detail.messages()).hasSize(1);
        assertThat(detail.messages().get(0).result().sql()).isEqualTo("SELECT 1");
    }

    @Test
    void preventsReadingAnotherUsersConversation() {
        when(mapper.selectConversation(9L, 8L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(8L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void rejectsEditingAnAssistantMessage() {
        ChatMessage assistant = message("ASSISTANT", "查询完成", null, "SELECT 1");
        when(mapper.selectMessage(9L, 13L)).thenReturn(assistant);

        assertThatThrownBy(() -> service.context(9L, 13L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能编辑");
    }

    private ChatMessage message(String role, String content, String plan, String sql) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(9L);
        message.setRole(role);
        message.setContent(content);
        message.setQueryPlanJson(plan);
        message.setSqlText(sql);
        return message;
    }
}
