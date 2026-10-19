package com.xym.antifraud.mapper;

import com.xym.antifraud.entity.ChatConversation;
import com.xym.antifraud.entity.ChatMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatHistoryMapper {
    int insertConversation(ChatConversation conversation);
    ChatConversation selectConversation(@Param("id") Long id, @Param("userId") Long userId);
    List<ChatConversation> selectConversations(@Param("userId") Long userId);
    int touchConversation(@Param("id") Long id);
    int deleteConversation(@Param("id") Long id, @Param("userId") Long userId);
    int insertMessage(ChatMessage message);
    List<ChatMessage> selectMessages(@Param("conversationId") Long conversationId);
    ChatMessage selectMessage(@Param("conversationId") Long conversationId,
                              @Param("messageId") Long messageId);
    List<ChatMessage> selectRecentMessages(@Param("conversationId") Long conversationId,
                                           @Param("beforeMessageId") Long beforeMessageId,
                                           @Param("limit") int limit);
    int deleteMessagesFrom(@Param("conversationId") Long conversationId,
                           @Param("messageId") Long messageId);
}
