package com.xym.antifraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xym.antifraud.config.KnowledgeFileProperties;
import com.xym.antifraud.config.KnowledgeProperties;
import com.xym.antifraud.dto.knowledge.KnowledgeFileResponse;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeFileService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileService.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "txt", "pdf", "doc", "docx");
    private static final int EMBEDDING_BATCH_SIZE = 32;

    private final RestClient restClient;
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeFileProperties fileProperties;
    private final KnowledgeEmbeddingService embeddingService;
    private final ElasticsearchKnowledgeService knowledgeService;
    private final KnowledgeDocumentChunker chunker;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    public KnowledgeFileService(@Qualifier("knowledgeRestClient") RestClient restClient,
                                KnowledgeProperties knowledgeProperties,
                                KnowledgeFileProperties fileProperties,
                                KnowledgeEmbeddingService embeddingService,
                                ElasticsearchKnowledgeService knowledgeService,
                                KnowledgeDocumentChunker chunker,
                                ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.knowledgeProperties = knowledgeProperties;
        this.fileProperties = fileProperties;
        this.embeddingService = embeddingService;
        this.knowledgeService = knowledgeService;
        this.chunker = chunker;
        this.objectMapper = objectMapper;
    }

    public KnowledgeFileResponse upload(MultipartFile file, String database, String knowledgeType,
                                        List<String> tableNames, List<String> keywords, String source) {
        validateFile(file);
        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = extension(originalName);
        String id = UUID.randomUUID().toString();
        String storedName = id + "." + extension;
        Instant now = Instant.now();
        FileManifest manifest = new FileManifest(id, originalName, extension, file.getSize(), "PROCESSING", 0,
                normalizeDatabase(database), normalizeType(knowledgeType), normalizeList(tableNames),
                normalizeList(keywords), blankToDefault(source, originalName), "", storedName, now, now);
        Path storedPath = storagePath(storedName);
        try {
            Files.createDirectories(storedPath.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, storedPath, StandardCopyOption.REPLACE_EXISTING);
            }
            ensureFileIndex();
            saveManifest(manifest);
            return build(manifest, storedPath);
        } catch (RuntimeException | IOException exception) {
            log.error("[Knowledge file build failed] id={}, fileName={}", id, originalName, exception);
            FileManifest failed = manifest.failed(rootMessage(exception));
            try { ensureFileIndex(); saveManifest(failed); }
            catch (RuntimeException statusException) {
                log.warn("[Knowledge file failure status save failed] id={}, message={}", id, statusException.getMessage());
            }
            throw new IllegalStateException("知识文件构建失败：" + rootMessage(exception), exception);
        }
    }

    public List<KnowledgeFileResponse> list(String database) {
        ensureFileIndex();
        String normalized = normalizeDatabase(database);
        Map<String, Object> query = "all".equals(normalized)
                ? Map.of("match_all", Map.of())
                : Map.of("term", Map.of("database", normalized));
        JsonNode response = restClient.post().uri("/{index}/_search", fileIndexName())
                .body(Map.of("size", 200, "sort", List.of(Map.of("updatedAt", "desc")), "query", query))
                .retrieve().body(JsonNode.class);
        List<KnowledgeFileResponse> result = new ArrayList<>();
        if (response != null) {
            for (JsonNode hit : response.path("hits").path("hits")) {
                result.add(toManifest(hit.path("_source"), hit.path("_id").asText()).response());
            }
        }
        return List.copyOf(result);
    }

    public KnowledgeFileResponse rebuild(String id) {
        ensureFileIndex();
        FileManifest manifest = getManifest(id).processing();
        saveManifest(manifest);
        try {
            return build(manifest, storagePath(manifest.storedName()));
        } catch (RuntimeException exception) {
            saveManifest(manifest.failed(rootMessage(exception)));
            throw new IllegalStateException("知识文件重新构建失败：" + rootMessage(exception), exception);
        }
    }

    public void delete(String id) {
        ensureFileIndex();
        FileManifest manifest = getManifest(id);
        deleteChunks(id);
        restClient.delete().uri("/{index}/_doc/{id}?refresh=wait_for", fileIndexName(), id)
                .retrieve().toBodilessEntity();
        try {
            Files.deleteIfExists(storagePath(manifest.storedName()));
        } catch (IOException exception) {
            log.warn("[Knowledge file cleanup failed] id={}, message={}", id, exception.getMessage());
        }
    }

    private KnowledgeFileResponse build(FileManifest manifest, Path path) {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("原始文件不存在，无法构建");
        String text = parse(path, manifest.fileName());
        List<String> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) throw new IllegalArgumentException("文件中没有可索引的文本内容");
        List<List<Double>> embeddings = embedInBatches(chunks);
        knowledgeService.ensureIndex();
        deleteChunks(manifest.id());
        bulkIndex(manifest, chunks, embeddings);
        FileManifest ready = manifest.ready(chunks.size());
        saveManifest(ready);
        log.info("[Knowledge file built] id={}, fileName={}, chunks={}", manifest.id(), manifest.fileName(), chunks.size());
        return ready.response();
    }

    private String parse(Path path, String fileName) {
        Metadata metadata = new Metadata();
        metadata.set("resourceName", fileName);
        try (InputStream input = Files.newInputStream(path)) {
            String text = tika.parseToString(input, metadata, fileProperties.maxExtractedChars());
            return text == null ? "" : text.trim();
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析文件，请确认文件未加密且格式正确", exception);
        }
    }

    private List<List<Double>> embedInBatches(List<String> chunks) {
        List<List<Double>> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
            List<String> inputs = chunks.subList(start, end);
            List<List<Double>> batch = embeddingService.embedAll(inputs).orElseGet(() -> {
                List<List<Double>> empty = new ArrayList<>();
                for (int i = 0; i < inputs.size(); i++) empty.add(List.of());
                return empty;
            });
            embeddings.addAll(batch);
        }
        return List.copyOf(embeddings);
    }

    private void bulkIndex(FileManifest manifest, List<String> chunks, List<List<Double>> embeddings) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            String chunkId = manifest.id() + "-" + String.format(Locale.ROOT, "%04d", index + 1);
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("title", manifest.fileName() + " · " + (index + 1) + "/" + chunks.size());
            document.put("content", chunks.get(index));
            document.put("knowledgeType", manifest.knowledgeType());
            document.put("database", manifest.database());
            document.put("tableNames", manifest.tableNames());
            document.put("keywords", manifest.keywords());
            document.put("source", manifest.source());
            document.put("status", "PUBLISHED");
            document.put("fileId", manifest.id());
            document.put("fileName", manifest.fileName());
            document.put("chunkIndex", index);
            document.put("chunkCount", chunks.size());
            document.put("updatedAt", Instant.now().toString());
            if (!embeddings.get(index).isEmpty()) document.put("embedding", embeddings.get(index));
            try {
                body.append(objectMapper.writeValueAsString(Map.of("index",
                        Map.of("_index", knowledgeProperties.indexName(), "_id", chunkId)))).append('\n');
                body.append(objectMapper.writeValueAsString(document)).append('\n');
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("知识切片序列化失败", exception);
            }
        }
        JsonNode response = restClient.post().uri("/_bulk?refresh=wait_for")
                .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .body(body.toString()).retrieve().body(JsonNode.class);
        if (response != null && response.path("errors").asBoolean(false)) {
            throw new IllegalStateException("Elasticsearch 批量写入部分失败");
        }
    }

    private void deleteChunks(String fileId) {
        knowledgeService.ensureIndex();
        restClient.post().uri("/{index}/_delete_by_query?refresh=true&conflicts=proceed", knowledgeProperties.indexName())
                .body(Map.of("query", Map.of("term", Map.of("fileId", fileId))))
                .retrieve().toBodilessEntity();
    }

    private void ensureFileIndex() {
        boolean exists = restClient.head().uri("/{index}", fileIndexName())
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
        if (exists) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String keyword : List.of("fileName", "fileType", "status", "database", "knowledgeType", "source", "storedName"))
            fields.put(keyword, Map.of("type", "keyword"));
        fields.put("fileSize", Map.of("type", "long")); fields.put("chunkCount", Map.of("type", "integer"));
        fields.put("tableNames", Map.of("type", "keyword")); fields.put("keywords", Map.of("type", "keyword"));
        fields.put("errorMessage", Map.of("type", "text"));
        fields.put("createdAt", Map.of("type", "date")); fields.put("updatedAt", Map.of("type", "date"));
        restClient.put().uri("/{index}", fileIndexName())
                .body(Map.of("mappings", Map.of("properties", fields))).retrieve().toBodilessEntity();
    }

    private void saveManifest(FileManifest manifest) {
        restClient.put().uri("/{index}/_doc/{id}?refresh=wait_for", fileIndexName(), manifest.id())
                .body(manifest.document()).retrieve().toBodilessEntity();
    }

    private FileManifest getManifest(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,200}")) throw new IllegalArgumentException("文件ID不合法");
        JsonNode response = restClient.get().uri("/{index}/_doc/{id}", fileIndexName(), id)
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("found").asBoolean(true)) throw new IllegalArgumentException("知识文件不存在");
        return toManifest(response.path("_source"), id);
    }

    private FileManifest toManifest(JsonNode node, String id) {
        return new FileManifest(id, node.path("fileName").asText(), node.path("fileType").asText(),
                node.path("fileSize").asLong(), node.path("status").asText(), node.path("chunkCount").asInt(),
                node.path("database").asText("all"), node.path("knowledgeType").asText("GENERAL"),
                textList(node.path("tableNames")), textList(node.path("keywords")), node.path("source").asText(),
                node.path("errorMessage").asText(), node.path("storedName").asText(),
                Instant.parse(node.path("createdAt").asText()), Instant.parse(node.path("updatedAt").asText()));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的知识文件");
        if (file.getSize() > fileProperties.maxFileBytes())
            throw new IllegalArgumentException("文件不能超过 " + fileProperties.maxFileBytes() / 1024 / 1024 + "MB");
        String name = safeOriginalName(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension(name)))
            throw new IllegalArgumentException("仅支持 md、txt、pdf、doc、docx 文件");
    }

    private Path storagePath(String storedName) {
        Path directory = Path.of(fileProperties.storageDirectory()).toAbsolutePath().normalize();
        Path path = directory.resolve(storedName).normalize();
        if (!path.startsWith(directory)) throw new IllegalArgumentException("文件存储路径不安全");
        return path;
    }
    private String fileIndexName() { return knowledgeProperties.indexName() + "-files"; }
    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("文件名不能为空");
        return Path.of(name).getFileName().toString();
    }
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private static String normalizeDatabase(String value) {
        String normalized = blankToDefault(value, "all").toLowerCase(Locale.ROOT);
        if (!Set.of("all", "mysql", "hive").contains(normalized)) throw new IllegalArgumentException("不支持的数据库：" + value);
        return normalized;
    }
    private static String normalizeType(String value) { return blankToDefault(value, "GENERAL").toUpperCase(Locale.ROOT); }
    private static List<String> normalizeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
    }
    private static List<String> textList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>(); node.forEach(v -> values.add(v.asText())); return List.copyOf(values);
    }
    private static String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable; while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private record FileManifest(String id, String fileName, String fileType, long fileSize, String status,
                                int chunkCount, String database, String knowledgeType, List<String> tableNames,
                                List<String> keywords, String source, String errorMessage, String storedName,
                                Instant createdAt, Instant updatedAt) {
        FileManifest ready(int count) { return new FileManifest(id, fileName, fileType, fileSize, "READY", count, database,
                knowledgeType, tableNames, keywords, source, "", storedName, createdAt, Instant.now()); }
        FileManifest failed(String error) { return new FileManifest(id, fileName, fileType, fileSize, "FAILED", 0, database,
                knowledgeType, tableNames, keywords, source, error, storedName, createdAt, Instant.now()); }
        FileManifest processing() { return new FileManifest(id, fileName, fileType, fileSize, "PROCESSING", 0, database,
                knowledgeType, tableNames, keywords, source, "", storedName, createdAt, Instant.now()); }
        KnowledgeFileResponse response() { return new KnowledgeFileResponse(id, fileName, fileType, fileSize, status,
                chunkCount, database, knowledgeType, source, errorMessage, createdAt, updatedAt); }
        Map<String, Object> document() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fileName", fileName); map.put("fileType", fileType); map.put("fileSize", fileSize);
            map.put("status", status); map.put("chunkCount", chunkCount); map.put("database", database);
            map.put("knowledgeType", knowledgeType); map.put("tableNames", tableNames); map.put("keywords", keywords);
            map.put("source", source); map.put("errorMessage", errorMessage); map.put("storedName", storedName);
            map.put("createdAt", createdAt.toString()); map.put("updatedAt", updatedAt.toString()); return map;
        }
    }
}
