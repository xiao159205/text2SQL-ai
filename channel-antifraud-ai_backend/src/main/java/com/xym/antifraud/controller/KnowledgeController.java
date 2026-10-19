package com.xym.antifraud.controller;

import com.xym.antifraud.dto.knowledge.KnowledgeDocumentRequest;
import com.xym.antifraud.dto.knowledge.KnowledgeDocumentResponse;
import com.xym.antifraud.dto.knowledge.KnowledgeSearchResponse;
import com.xym.antifraud.dto.knowledge.KnowledgeFileResponse;
import com.xym.antifraud.service.ElasticsearchKnowledgeService;
import com.xym.antifraud.service.KnowledgeFileService;
import com.xym.antifraud.service.UserSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final ElasticsearchKnowledgeService knowledgeService;
    private final UserSessionService userSessionService;
    private final KnowledgeFileService knowledgeFileService;

    public KnowledgeController(ElasticsearchKnowledgeService knowledgeService,
                               UserSessionService userSessionService,
                               KnowledgeFileService knowledgeFileService) {
        this.knowledgeService = knowledgeService;
        this.userSessionService = userSessionService;
        this.knowledgeFileService = knowledgeFileService;
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeFileResponse uploadFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "all") String database,
            @RequestParam(defaultValue = "GENERAL") String knowledgeType,
            @RequestParam(required = false) List<String> tableNames,
            @RequestParam(required = false) List<String> keywords,
            @RequestParam(required = false) String source) {
        userSessionService.requireAdmin(authorization);
        return knowledgeFileService.upload(file, database, knowledgeType, tableNames, keywords, source);
    }

    @GetMapping("/files")
    public List<KnowledgeFileResponse> listFiles(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "all") String database) {
        userSessionService.requireAdmin(authorization);
        return knowledgeFileService.list(database);
    }

    @PostMapping("/files/{id}/rebuild")
    public KnowledgeFileResponse rebuildFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        userSessionService.requireAdmin(authorization);
        return knowledgeFileService.rebuild(id);
    }

    @DeleteMapping("/files/{id}")
    public Map<String, Object> deleteFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        userSessionService.requireAdmin(authorization);
        knowledgeFileService.delete(id);
        return Map.of("deleted", true, "id", id);
    }

    @PostMapping
    public KnowledgeDocumentResponse save(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        userSessionService.requireAdmin(authorization);
        return knowledgeService.save(request);
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String question,
            @RequestParam(defaultValue = "all") String database) {
        userSessionService.requireAdmin(authorization);
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        return new KnowledgeSearchResponse(
                knowledgeService.search(question.trim(), database),
                knowledgeService.theoreticalMaxRrfScore());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        userSessionService.requireAdmin(authorization);
        knowledgeService.delete(id);
        return Map.of("deleted", true, "id", id);
    }
}
