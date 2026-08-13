package com.xym.antifraud.service;

import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.mapper.SchemaConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema 表/字段黑白名单（黑名单模式：默认全部发送给大模型，visible=0 表示拉黑）
 * 与自定义注释配置。配置通过 MyBatis 存储在业务 MySQL 的两张配置表中。
 * key 统一使用小写，与 SchemaCatalog.normalize 对齐。
 */
@Service
public class SchemaConfigService {

    private static final Logger log = LoggerFactory.getLogger(SchemaConfigService.class);

    private final SchemaConfigMapper mapper;
    private final Map<DatabaseType, Rules> rulesCache = new ConcurrentHashMap<>();

    public SchemaConfigService(SchemaConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 规则不存在时返回 null（表示默认：可见、无注释覆盖）。 */
    public Rule tableRule(DatabaseType database, String tableName) {
        return rulesFor(database).tables().get(normalize(tableName));
    }

    /** 规则不存在时返回 null。 */
    public Rule fieldRule(DatabaseType database, String tableName, String fieldName) {
        Map<String, Rule> fields = rulesFor(database).fields().get(normalize(tableName));
        return fields == null ? null : fields.get(normalize(fieldName));
    }

    public boolean isTableVisible(DatabaseType database, String tableName) {
        Rule rule = tableRule(database, tableName);
        return rule == null || rule.visible();
    }

    public boolean isFieldVisible(DatabaseType database, String tableName, String fieldName) {
        Rule rule = fieldRule(database, tableName, fieldName);
        return rule == null || rule.visible();
    }

    public Optional<String> tableComment(DatabaseType database, String tableName) {
        Rule rule = tableRule(database, tableName);
        return rule == null ? Optional.empty() : Optional.ofNullable(rule.customComment());
    }

    public Optional<String> fieldComment(DatabaseType database, String tableName, String fieldName) {
        Rule rule = fieldRule(database, tableName, fieldName);
        return rule == null ? Optional.empty() : Optional.ofNullable(rule.customComment());
    }

    public void saveTableRule(DatabaseType database, String tableName, boolean visible, String customComment) {
        mapper.upsertTableRule(
                database.name(), tableName, visible ? 1 : 0, blankToNull(customComment));
        reload(database);
        log.info("[Schema配置] 表规则已保存: database={}, table={}, visible={}, customComment={}",
                database, tableName, visible, customComment);
    }

    public void saveFieldRule(DatabaseType database, String tableName, String fieldName,
                              boolean visible, String customComment) {
        mapper.upsertFieldRule(
                database.name(), tableName, fieldName, visible ? 1 : 0, blankToNull(customComment));
        reload(database);
        log.info("[Schema配置] 字段规则已保存: database={}, table={}, field={}, visible={}, customComment={}",
                database, tableName, fieldName, visible, customComment);
    }

    private Rules rulesFor(DatabaseType database) {
        return rulesCache.computeIfAbsent(database, this::loadRules);
    }

    private synchronized void reload(DatabaseType database) {
        rulesCache.put(database, loadRules(database));
    }

    private Rules loadRules(DatabaseType database) {
        Map<String, Rule> tables = new HashMap<>();
        mapper.selectTableRules(database.name()).forEach(row ->
                tables.put(normalize(row.tableName()),
                        new Rule(row.visible() == 1, row.customComment())));
        Map<String, Map<String, Rule>> fields = new HashMap<>();
        mapper.selectFieldRules(database.name()).forEach(row ->
                fields.computeIfAbsent(normalize(row.tableName()), key -> new HashMap<>())
                        .put(normalize(row.fieldName()),
                                new Rule(row.visible() == 1, row.customComment())));
        log.info("[Schema配置] 规则加载完成: database={}, tableRules={}, fieldRules={}",
                database, tables.size(),
                fields.values().stream().mapToInt(Map::size).sum());
        return new Rules(tables, fields);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Rule(boolean visible, String customComment) {
    }

    /** tableName(小写) -> 字段名(小写) -> 规则 */
    public record Rules(Map<String, Rule> tables, Map<String, Map<String, Rule>> fields) {
    }
}
