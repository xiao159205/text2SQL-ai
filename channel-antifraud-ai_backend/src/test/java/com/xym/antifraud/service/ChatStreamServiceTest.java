package com.xym.antifraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.dto.chat.ChatRequest;
import com.xym.antifraud.stream.AgUiEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatStreamServiceTest {

    @Test
    void completesStreamWhenAClassLinkageErrorOccurs() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(anyLong(), any(), any(), anyString(), anyString(),
                any(), any(Boolean.class), any(AgUiEventPublisher.class)))
                .thenThrow(new NoClassDefFoundError("missing/runtime/Class"));
        ChatStreamService streamService = new ChatStreamService(
                chatService, new SyncTaskExecutor(), new ObjectMapper());
        ChatRequest request = new ChatRequest(null, null, "查询作者", "hive", null, true);

        assertThatCode(() -> streamService.stream(1L, request))
                .doesNotThrowAnyException();
    }
}
