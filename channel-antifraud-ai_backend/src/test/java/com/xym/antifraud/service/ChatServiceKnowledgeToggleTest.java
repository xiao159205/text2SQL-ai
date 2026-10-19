package com.xym.antifraud.service;

import com.xym.antifraud.ai.SqlPlanGenerator;
import com.xym.antifraud.dto.chat.ChatResponse;
import com.xym.antifraud.dto.knowledge.KnowledgeSearchHit;
import com.xym.antifraud.dto.knowledge.RagAnswerResponse;
import com.xym.antifraud.entity.ChatConversation;
import com.xym.antifraud.entity.ChatMessage;
import com.xym.antifraud.query.QueryExecutor;
import com.xym.antifraud.query.SqlAstCompiler;
import com.xym.antifraud.query.SqlAstValidator;
import com.xym.antifraud.query.ast.SqlQueryPlan;
import com.xym.antifraud.schema.SchemaCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceKnowledgeToggleTest {

    private final SqlPlanGenerator planGenerator = mock(SqlPlanGenerator.class);
    private final KnowledgeRetriever knowledgeRetriever = mock(KnowledgeRetriever.class);
    private final ChatIntentRouter intentRouter = mock(ChatIntentRouter.class);
    private final RagService ragService = mock(RagService.class);
    private final GeneralChatService generalChatService = mock(GeneralChatService.class);
    private final ChatHistoryService historyService = mock(ChatHistoryService.class);
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(planGenerator, mock(SqlAstCompiler.class),
                mock(SqlAstValidator.class), mock(QueryExecutor.class), mock(SchemaCatalog.class),
                mock(ChartService.class), historyService, knowledgeRetriever, intentRouter,
                ragService, generalChatService);

        ChatConversation conversation = new ChatConversation();
        conversation.setId(10L);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(11L);
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setId(12L);
        when(historyService.getOrCreate(anyLong(), any(), anyString(), anyString()))
                .thenReturn(conversation);
        when(historyService.context(10L, null)).thenReturn("");
        when(historyService.saveUser(anyLong(), anyString())).thenReturn(userMessage);
        when(historyService.saveAssistant(anyLong(), any(ChatResponse.class)))
                .thenReturn(assistantMessage);
        when(intentRouter.shouldProbeKnowledge(anyString())).thenReturn(true);
    }

    @Test
    void answersKnowledgeQuestionWithoutRagWhenUserDisablesKnowledgeBase() {
        when(intentRouter.route(anyString(), anyString(), anyString()))
                .thenReturn(ChatResponse.ResponseType.KNOWLEDGE_QA);
        when(generalChatService.answer("什么是销售额", "")).thenReturn("通用回答");

        var turn = chatService.ask(1L, null, null, "什么是销售额", "mysql", false);

        assertThat(turn.result().responseType()).isEqualTo(ChatResponse.ResponseType.GENERAL_CHAT);
        assertThat(turn.result().summary()).isEqualTo("通用回答");
        verify(ragService, never()).ask(anyString(), anyString());
        verify(knowledgeRetriever, never()).retrieve(anyString(), anyString());
    }

    @Test
    void generatesDataPlanWithEmptyKnowledgeContextWhenDisabled() {
        when(intentRouter.route(anyString(), anyString(), anyString()))
                .thenReturn(ChatResponse.ResponseType.DATA_QUERY);
        SqlQueryPlan clarification = new SqlQueryPlan(
                SqlQueryPlan.PlanStatus.NEED_CLARIFICATION, "请补充条件",
                SqlQueryPlan.StatementType.SELECT, null, List.of(), false, List.of(),
                null, List.of(), List.of(), List.of(), 100);
        when(planGenerator.generate("查询订单", "", "")).thenReturn(clarification);

        var turn = chatService.ask(1L, null, null, "查询订单", "mysql", false);

        assertThat(turn.result().responseType()).isEqualTo(ChatResponse.ResponseType.CLARIFICATION);
        verify(knowledgeRetriever, never()).retrieve(anyString(), anyString());
        verify(planGenerator).generate("查询订单", "", "");
    }

    @Test
    void routesWithRealKnowledgeEvidenceAndReusesRetrievalForAnswer() {
        KnowledgeSearchHit hit = new KnowledgeSearchHit(
                "doc-1", 0.93, "Spark分布式荔枝分类方案", "内部实现步骤",
                "GENERAL", "mysql", List.of(), List.of("Spark", "荔枝分类"), "方案.docx");
        KnowledgeRetrieval retrieval = new KnowledgeRetrieval(
                KnowledgeRetrieval.Status.READY, "Spark 分布式 荔枝 分类",
                List.of(hit), "[1] Spark分布式荔枝分类方案\n内部实现步骤\n");
        when(knowledgeRetriever.retrieve("如何实现Spark荔枝分类", "mysql"))
                .thenReturn(retrieval);
        when(intentRouter.route("如何实现Spark荔枝分类", "", retrieval.routingEvidence()))
                .thenReturn(ChatResponse.ResponseType.KNOWLEDGE_QA);
        when(ragService.ask("如何实现Spark荔枝分类", retrieval))
                .thenReturn(new RagAnswerResponse(
                        "如何实现Spark荔枝分类", "Spark 分布式 荔枝 分类", "依据内部方案回答",
                        List.of(new RagAnswerResponse.Source(
                                "doc-1", "Spark分布式荔枝分类方案", "方案.docx", 0.93))));

        var turn = chatService.ask(1L, null, null,
                "如何实现Spark荔枝分类", "mysql", true);

        assertThat(turn.result().responseType()).isEqualTo(ChatResponse.ResponseType.KNOWLEDGE_QA);
        assertThat(turn.result().summary()).isEqualTo("依据内部方案回答");
        verify(knowledgeRetriever).retrieve("如何实现Spark荔枝分类", "mysql");
        verify(ragService).ask("如何实现Spark荔枝分类", retrieval);
        verify(ragService, never()).ask(anyString(), anyString());
    }
}
