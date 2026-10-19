package com.xym.antifraud.controller;

import com.xym.antifraud.datasource.DatabaseContext;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.schema.FieldDefinition;
import com.xym.antifraud.schema.SchemaCatalog;
import com.xym.antifraud.service.MySqlSchemaCommentService;
import com.xym.antifraud.service.SchemaConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Schema 黑白名单/注释配置接口。黑名单模式：默认全部发送给大模型，visible=false 表示排除。
 */
@RestController
@RequestMapping("/api/schema-config")
public class SchemaConfigController {

    private final SchemaConfigService configService;
    private final SchemaCatalog schemaCatalog;
    private final MySqlSchemaCommentService mySqlSchemaCommentService;

    public SchemaConfigController(SchemaConfigService configService, SchemaCatalog schemaCatalog,
                                  MySqlSchemaCommentService mySqlSchemaCommentService) {
        this.configService = configService;
        this.schemaCatalog = schemaCatalog;
        this.mySqlSchemaCommentService = mySqlSchemaCommentService;
    }

    /**
     * 返回指定数据库中所有可配置的表和字段（未应用黑名单过滤），并合并当前配置规则，
     * 供配置页面渲染。仅包含通过 text2sql.allowed-tables 静态白名单的表。
     */
    @GetMapping("/tree")
    public List<TableConfigView> tree(@RequestParam(defaultValue = "mysql") String database) {
        DatabaseType databaseType = DatabaseType.from(database);
        DatabaseContext.use(databaseType);
        try {
            return schemaCatalog.listConfigurableTables().stream()
                    .map(table -> toView(databaseType, table))
                    .toList();
        } finally {
            DatabaseContext.clear();
        }
    }

    @PutMapping("/tables/{tableName}")
    public Map<String, Object> saveTableRule(@PathVariable String tableName,
                                             @RequestBody RuleRequest request) {
        DatabaseType databaseType = DatabaseType.from(request.database());
        String configuredComment = request.comment();
        if (databaseType == DatabaseType.MYSQL) {
            if (request.comment() != null) {
                mySqlSchemaCommentService.updateTableComment(tableName, request.comment());
                configuredComment = null;
            } else {
                SchemaConfigService.Rule currentRule =
                        configService.tableRule(databaseType, tableName);
                configuredComment = currentRule == null ? null : currentRule.customComment();
            }
        }
        configService.saveTableRule(databaseType, tableName,
                request.visible(), configuredComment);
        schemaCatalog.evictAll();
        return Map.of("code", 0);
    }

    @PutMapping("/tables/batch-visibility")
    public Map<String, Object> saveTableVisibilityBatch(@RequestBody TableBatchRequest request) {
        DatabaseType databaseType = DatabaseType.from(request.database());
        configService.saveTableVisibilityBatch(databaseType, request.tableNames(), request.visible());
        schemaCatalog.evictAll();
        return Map.of("code", 0, "updated", request.tableNames().size());
    }

    @PutMapping("/tables/{tableName}/fields/{fieldName}")
    public Map<String, Object> saveFieldRule(@PathVariable String tableName,
                                             @PathVariable String fieldName,
                                             @RequestBody RuleRequest request) {
        DatabaseType databaseType = DatabaseType.from(request.database());
        String configuredComment = request.comment();
        if (databaseType == DatabaseType.MYSQL) {
            if (request.comment() != null) {
                mySqlSchemaCommentService.updateFieldComment(tableName, fieldName, request.comment());
                configuredComment = null;
            } else {
                SchemaConfigService.Rule currentRule =
                        configService.fieldRule(databaseType, tableName, fieldName);
                configuredComment = currentRule == null ? null : currentRule.customComment();
            }
        }
        configService.saveFieldRule(databaseType, tableName, fieldName,
                request.visible(), configuredComment);
        schemaCatalog.evictAll();
        return Map.of("code", 0);
    }

    @PutMapping("/tables/{tableName}/fields/batch-visibility")
    public Map<String, Object> saveFieldVisibilityBatch(
            @PathVariable String tableName,
            @RequestBody FieldBatchRequest request) {
        DatabaseType databaseType = DatabaseType.from(request.database());
        configService.saveFieldVisibilityBatch(databaseType, tableName,
                request.fieldNames(), request.visible());
        schemaCatalog.evictAll();
        return Map.of("code", 0, "updated", request.fieldNames().size());
    }

    /** 预览当前生效的、发给大模型的 Schema 文本。 */
    @GetMapping("/prompt-preview")
    public Map<String, String> promptPreview(@RequestParam(defaultValue = "mysql") String database) {
        DatabaseType databaseType = DatabaseType.from(database);
        DatabaseContext.use(databaseType);
        try {
            return Map.of("prompt", schemaCatalog.promptSchema());
        } finally {
            DatabaseContext.clear();
        }
    }

    private TableConfigView toView(DatabaseType database, SchemaCatalog.TableDefinition table) {
        SchemaConfigService.Rule tableRule = configService.tableRule(database, table.tableName());
        List<FieldConfigView> fields = table.columns().values().stream()
                .sorted(Comparator.comparingInt(FieldDefinition::ordinalPosition))
                .map(field -> {
                    SchemaConfigService.Rule fieldRule =
                            configService.fieldRule(database, table.tableName(), field.columnName());
                    return new FieldConfigView(
                            field.columnName(),
                            field.mysqlType(),
                            field.ordinalPosition(),
                            field.comment(),
                            fieldRule == null ? null : fieldRule.customComment(),
                            fieldRule == null || fieldRule.visible());
                })
                .toList();
        return new TableConfigView(
                table.tableName(),
                table.comment(),
                tableRule == null ? null : tableRule.customComment(),
                tableRule == null || tableRule.visible(),
                fields);
    }

    public record RuleRequest(String database, boolean visible, String comment) {
    }

    public record TableBatchRequest(String database, boolean visible, List<String> tableNames) {
        public TableBatchRequest {
            tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
        }
    }

    public record FieldBatchRequest(String database, boolean visible, List<String> fieldNames) {
        public FieldBatchRequest {
            fieldNames = fieldNames == null ? List.of() : List.copyOf(fieldNames);
        }
    }

    public record TableConfigView(String tableName, String dbComment, String customComment,
                                  boolean visible, List<FieldConfigView> fields) {
    }

    public record FieldConfigView(String fieldName, String mysqlType, int ordinalPosition,
                                  String dbComment, String customComment, boolean visible) {
    }
}
