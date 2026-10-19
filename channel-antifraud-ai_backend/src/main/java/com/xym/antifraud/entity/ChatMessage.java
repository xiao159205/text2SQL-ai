package com.xym.antifraud.entity;

import java.time.LocalDateTime;

public class ChatMessage {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String sqlText;
    private String queryPlanJson;
    private String responseJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public String getQueryPlanJson() { return queryPlanJson; }
    public void setQueryPlanJson(String queryPlanJson) { this.queryPlanJson = queryPlanJson; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
