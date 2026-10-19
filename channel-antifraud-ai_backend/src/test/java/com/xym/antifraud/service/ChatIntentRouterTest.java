package com.xym.antifraud.service;

import com.xym.antifraud.dto.chat.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatIntentRouterTest {

    private final ChatClient.Builder builder = mock(ChatClient.Builder.class);

    ChatIntentRouterTest() {
        when(builder.build()).thenReturn(mock(ChatClient.class));
    }

    @Test
    void routesGreetingWithoutCallingModel() {
        ChatIntentRouter router = new ChatIntentRouter(builder);

        assertThat(router.route("你好", "无历史对话"))
                .isEqualTo(ChatResponse.ResponseType.GENERAL_CHAT);
        assertThat(router.shouldProbeKnowledge("你好")).isFalse();
    }

    @Test
    void asksForClarificationWhenQuestionIsTooShort() {
        ChatIntentRouter router = new ChatIntentRouter(builder);

        assertThat(router.route("这个", "无历史对话"))
                .isEqualTo(ChatResponse.ResponseType.CLARIFICATION);
        assertThat(router.shouldProbeKnowledge("这个")).isFalse();
    }
}
