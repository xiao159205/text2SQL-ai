package com.xym.antifraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.config.ChatMemoryProperties;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.dto.chat.ConversationView;
import com.xym.antifraud.entity.ChatConversation;
import com.xym.antifraud.entity.ChatMessage;
import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.mapper.ChatHistoryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatHistoryService {

    private final ChatHistoryMapper mapper;
    private final ObjectMapper objectMapper;
    private final int contextMessageLimit;

    public ChatHistoryService(ChatHistoryMapper mapper, ObjectMapper objectMapper,
                              ChatMemoryProperties properties) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.contextMessageLimit = properties.recentRounds() * 2;
    }

    public ChatConversation getOrCreate(Long userId, Long conversationId,
                                        String database, String firstQuestion) {
        requireUser(userId);
        if (conversationId != null) {
            ChatConversation conversation = requireOwned(conversationId, userId);
            if (!conversation.getDatabaseType().equalsIgnoreCase(database)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DATABASE_MISMATCH",
                        "当前会话的数据源不能切换，请新建会话");
            }
            return conversation;
        }

        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setTitle(titleOf(firstQuestion));
        conversation.setDatabaseType(database.toLowerCase());
        mapper.insertConversation(conversation);
        return conversation;
    }

    public List<ConversationView> list(Long userId) {
        requireUser(userId);
        return mapper.selectConversations(userId).stream()
                .map(item -> new ConversationView(item.getId(), item.getTitle(),
                        item.getDatabaseType(), item.getUpdatedAt()))
                .toList();
    }

    public ConversationView.Detail detail(Long userId, Long conversationId) {
        ChatConversation conversation = requireOwned(conversationId, userId);
        List<ConversationView.Message> messages = mapper.selectMessages(conversationId).stream()
                .map(this::toView)
                .toList();
        return new ConversationView.Detail(conversation.getId(), conversation.getTitle(),
                conversation.getDatabaseType(), messages);
    }

    public void delete(Long userId, Long conversationId) {
        requireOwned(conversationId, userId);
        mapper.deleteConversation(conversationId, userId);
    }

    public void truncateFrom(Long conversationId, Long messageId) {
        if (messageId != null) {
            mapper.deleteMessagesFrom(conversationId, messageId);
        }
    }

    public String context(Long conversationId, Long beforeMessageId) {
        if (beforeMessageId != null) {
            ChatMessage replacement = mapper.selectMessage(conversationId, beforeMessageId);
            if (replacement == null || !"USER".equals(replacement.getRole())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REPLACEMENT",
                        "只能编辑当前会话中的用户问题");
            }
        }
        List<ChatMessage> messages = mapper.selectRecentMessages(
                conversationId, beforeMessageId, contextMessageLimit);
        if (messages.isEmpty()) {
            return "无历史对话，这是当前会话的第一轮问题。";
        }

        StringBuilder context = new StringBuilder();
        for (ChatMessage message : messages) {
            if ("USER".equals(message.getRole())) {
                context.append("用户：").append(message.getContent()).append('\n');
            } else {
                context.append("助手：").append(message.getContent()).append('\n');
                if (message.getQueryPlanJson() != null && !message.getQueryPlanJson().isBlank()) {
                    context.append("该轮完整查询计划：")
                            .append(message.getQueryPlanJson()).append('\n');
                }
                if (message.getSqlText() != null && !message.getSqlText().isBlank()) {
                    context.append("该轮执行SQL：").append(message.getSqlText()).append('\n');
                }
            }
        }
        return context.toString();
    }

    public ChatMessage saveUser(Long conversationId, String question) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole("USER");
        message.setContent(question);
        mapper.insertMessage(message);
        return message;
    }

    public ChatMessage saveAssistant(Long conversationId, ChatResponse response) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent(response.summary());
        message.setSqlText(response.sql());
        message.setQueryPlanJson(writeJson(response.queryPlan()));
        message.setResponseJson(writeJson(response));
        mapper.insertMessage(message);
        mapper.touchConversation(conversationId);
        return message;
    }

    private ConversationView.Message toView(ChatMessage message) {
        ChatResponse response = null;
        if ("ASSISTANT".equals(message.getRole()) && message.getResponseJson() != null) {
            try {
                response = objectMapper.readValue(message.getResponseJson(), ChatResponse.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("历史消息解析失败", exception);
            }
        }
        return new ConversationView.Message(message.getId(), message.getRole().toLowerCase(),
                message.getContent(), response, message.getCreatedAt());
    }

    private ChatConversation requireOwned(Long conversationId, Long userId) {
        requireUser(userId);
        ChatConversation conversation = mapper.selectConversation(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                    "会话不存在或不属于当前用户");
        }
        return conversation;
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_REQUIRED", "缺少用户身份");
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会话数据序列化失败", exception);
        }
    }

    private String titleOf(String question) {
        String title = question.replaceAll("\\s+", " ").trim();
        return title.length() <= 30 ? title : title.substring(0, 30) + "…";
    }
}
