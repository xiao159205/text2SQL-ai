package com.xym.antifraud.schema;

import com.xym.antifraud.config.AntifraudProperties;
import com.xym.antifraud.datasource.DatabaseContext;
import com.xym.antifraud.datasource.DatabaseConnectionContext;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.service.SchemaConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class SchemaCatalog {

    private static final Logger log = LoggerFactory.getLogger(SchemaCatalog.class);

    private static final String RELATIONSHIPS_SQL = """
            SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
            ORDER BY TABLE_NAME, COLUMN_NAME
            """;

    private static final String METADATA_SQL = """
            SELECT c.TABLE_NAME, t.TABLE_COMMENT, c.ORDINAL_POSITION,
                   c.COLUMN_NAME, c.COLUMN_TYPE, c.COLUMN_COMMENT
            FROM information_schema.COLUMNS c
            JOIN information_schema.TABLES t
              ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
            WHERE c.TABLE_SCHEMA = DATABASE()
            ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
            """;

    private static final String HIVE_METASTORE_SCHEMA_SERDES =
            "hive.serdes.using.metastore.for.schema";
    private static final String HIVE_JSON_SERDE =
            "org.apache.hadoop.hive.serde2.JsonSerDe";

    private final JdbcTemplate jdbcTemplate;
    private final AntifraudProperties properties;
    private final SchemaConfigService configService;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, Snapshot> snapshots = new java.util.concurrent.ConcurrentHashMap<>();

    public SchemaCatalog(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate,
                         AntifraudProperties properties,
                         SchemaConfigService configService) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.configService = configService;
    }

    public String promptSchema() {
        Snapshot current = snapshot();
        log.info("[Schema就绪] tables={}, columns={}, loadedAt={}",
                current.tables().size(), current.columnCount(), current.loadedAt());
        return "当前数据库类型: " + DatabaseContext.current() + "\n"
                + current.promptSchema();
    }

    public void refresh() {
        log.info("[Schema手动刷新] 开始重新读取MySQL元数据");
        synchronized (this) {
            DatabaseType type = DatabaseContext.current();
            snapshots.put(DatabaseConnectionContext.cacheKey(type), loadSnapshot());
        }
    }

    /** 黑白名单/注释配置变更后调用，使所有数据库的 Schema 快照在下一次提问时重建。 */
    public void evictAll() {
        log.info("[Schema缓存失效] 配置变更，清空全部Schema快照");
        synchronized (this) {
            snapshots.clear();
        }
    }

    /**
     * 配置页专用：返回当前数据库（DatabaseContext）中未应用黑白名单/注释覆盖的全部表结构
     * （仍受 text2sql.allowed-tables 静态白名单约束）。不缓存，仅供配置界面调用。
     */
    public List<TableDefinition> listConfigurableTables() {
        Map<String, MutableTable> rawTables = rawTables();
        List<TableDefinition> tables = new ArrayList<>();
        rawTables.forEach((key, value) -> tables.add(
                new TableDefinition(value.tableName(), value.comment(), Map.copyOf(value.columns()))));
        return tables;
    }

    public boolean containsTable(String tableName) {
        return tableName != null && snapshot().tables().containsKey(normalize(tableName));
    }

    public TableDefinition requireTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        return Optional.ofNullable(snapshot().tables().get(normalize(tableName)))
                .orElseThrow(() -> new IllegalArgumentException("当前Schema中不存在或不允许访问表: " + tableName));
    }

    public FieldDefinition requireField(String tableName, String columnName) {
        TableDefinition table = requireTable(tableName);
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        return Optional.ofNullable(table.columns().get(normalize(columnName)))
                .orElseThrow(() -> new IllegalArgumentException(
                        "表 " + table.tableName() + " 中不存在字段: " + columnName));
    }

    public ResolvedField resolveField(String reference, Map<String, String> scope) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("字段引用不能为空");
        }
        String[] parts = reference.split("\\.", -1);
        if (parts.length == 2) {
            String qualifier = normalize(parts[0]);
            String table = scope.get(qualifier);
            if (table == null) {
                throw new IllegalArgumentException("字段引用了查询范围之外的表或别名: " + reference);
            }
            return new ResolvedField(parts[0], requireField(table, parts[1]));
        }
        if (parts.length != 1) {
            throw new IllegalArgumentException("字段引用格式错误: " + reference);
        }

        List<ResolvedField> matches = new ArrayList<>();
        Set<String> visitedTables = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : scope.entrySet()) {
            if (!visitedTables.add(normalize(entry.getValue()))) {
                continue;
            }
            TableDefinition table = requireTable(entry.getValue());
            FieldDefinition field = table.columns().get(normalize(reference));
            if (field != null) {
                String qualifier = preferredQualifier(scope, table.tableName());
                matches.add(new ResolvedField(qualifier, field));
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("查询范围内不存在字段: " + reference);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("多表查询中的字段存在歧义，请使用别名.字段名: " + reference);
        }
        return matches.get(0);
    }

    private String preferredQualifier(Map<String, String> scope, String tableName) {
        return scope.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(tableName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(tableName);
    }

    private Snapshot snapshot() {
        DatabaseType database = DatabaseContext.current();
        String cacheKey = DatabaseConnectionContext.cacheKey(database);
        Snapshot current = snapshots.get(cacheKey);
        Instant now = clock.instant();
        if (current == null || now.isAfter(current.loadedAt().plusSeconds(properties.schemaCacheSeconds()))) {
            synchronized (this) {
                current = snapshots.get(cacheKey);
                if (current == null || now.isAfter(current.loadedAt().plusSeconds(properties.schemaCacheSeconds()))) {
                    current = loadSnapshot();
                    snapshots.put(cacheKey, current);
                }
            }
        }
        return current;
    }

    private Snapshot loadSnapshot() {
        if (DatabaseContext.current() == DatabaseType.HIVE) {
            return loadHiveSnapshot();
        }
        long startedAt = System.nanoTime();
        DatabaseType database = DatabaseContext.current();
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        log.info("[Schema加载开始] database={}, allowedTables={}", databaseName, properties.allowedTables());
        Map<String, MutableTable> mutableTables = rawTables();
        applyConfigRules(database, mutableTables);
        if (mutableTables.isEmpty()) {
            throw new IllegalStateException(
                    "当前数据库没有可用于Text2SQL的表，请检查数据源、text2sql.allowed-tables和黑白名单配置");
        }
        List<Relationship> relationships = jdbcTemplate.query(RELATIONSHIPS_SQL, (rs, rowNum) ->
                new Relationship(rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"),
                        rs.getString("REFERENCED_TABLE_NAME"), rs.getString("REFERENCED_COLUMN_NAME")))
                .stream()
                .filter(relation -> mutableTables.containsKey(normalize(relation.fromTable()))
                        && mutableTables.containsKey(normalize(relation.toTable())))
                .toList();
        int columnCount = mutableTables.values().stream().mapToInt(t -> t.columns().size()).sum();
        if (columnCount > properties.maxSchemaColumns()) {
            throw new IllegalStateException("Schema字段总数" + columnCount + "超过限制" + properties.maxSchemaColumns()
                    + "，请配置text2sql.allowed-tables或黑白名单缩小范围");
        }

        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        mutableTables.forEach((key, value) -> tables.put(key,
                new TableDefinition(value.tableName(),
                        configService.tableComment(database, value.tableName()).orElse(value.comment()),
                        Map.copyOf(value.columns()))));
        String prompt = buildPromptSchema(database, tables, relationships, columnCount);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("[Schema加载完成] database={}, tables={}, columns={}, relationships={}, tableNames={}, elapsedMs={}",
                databaseName, tables.size(), columnCount, relationships.size(),
                tables.values().stream().map(TableDefinition::tableName).toList(), elapsedMs);
        return new Snapshot(Instant.now(clock), Map.copyOf(tables), columnCount, prompt);
    }

    /**
     * 读取当前数据库（DatabaseContext）的原始表结构，仅应用 text2sql.allowed-tables 静态白名单，
     * 不应用黑白名单/注释覆盖。
     */
    private Map<String, MutableTable> rawTables() {
        if (DatabaseContext.current() == DatabaseType.HIVE) {
            return rawHiveTables();
        }
        Set<String> allowed = properties.allowedTables().stream()
                .map(SchemaCatalog::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, MutableTable> mutableTables = new LinkedHashMap<>();
        jdbcTemplate.query(METADATA_SQL, rs -> {
            String tableName = rs.getString("TABLE_NAME");
            if (!allowed.isEmpty() && !allowed.contains(normalize(tableName))) {
                return;
            }
            String tableComment = rs.getString("TABLE_COMMENT");
            MutableTable table = mutableTables.computeIfAbsent(normalize(tableName), ignored ->
                    new MutableTable(tableName, tableComment, new LinkedHashMap<>()));
            FieldDefinition field = new FieldDefinition(
                    tableName,
                    rs.getInt("ORDINAL_POSITION"),
                    rs.getString("COLUMN_NAME"),
                    rs.getString("COLUMN_TYPE"),
                    rs.getString("COLUMN_COMMENT")
            );
            table.columns().put(normalize(field.columnName()), field);
        });
        return mutableTables;
    }

    /**
     * 在原始表结构上应用动态黑名单和注释覆盖（注释覆盖发生在 FieldDefinition 构造处，
     * 因此大模型 prompt 与前端结果表格列名同时生效）。
     */
    private void applyConfigRules(DatabaseType database, Map<String, MutableTable> mutableTables) {
        mutableTables.values().removeIf(table ->
                !configService.isTableVisible(database, table.tableName()));
        mutableTables.values().forEach(table -> {
            table.columns().values().removeIf(field ->
                    !configService.isFieldVisible(database, table.tableName(), field.columnName()));
            table.columns().replaceAll((key, field) ->
                    configService.fieldComment(database, table.tableName(), field.columnName())
                            .map(comment -> (FieldDefinition) new FieldDefinition(
                                    field.tableName(), field.ordinalPosition(), field.columnName(),
                                    field.mysqlType(), comment))
                            .orElse(field));
        });
    }

    private Snapshot loadHiveSnapshot() {
        long startedAt = System.nanoTime();
        DatabaseType database = DatabaseContext.current();
        String databaseName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        Map<String, MutableTable> mutableTables = rawHiveTables();
        applyConfigRules(database, mutableTables);
        if (mutableTables.isEmpty()) {
            throw new IllegalStateException(
                    "Hive 库 " + databaseName + " 没有可用于 Text2SQL 的表，请检查 text2sql.allowed-tables 和黑白名单配置");
        }
        int columnCount = mutableTables.values().stream()
                .mapToInt(table -> table.columns().size())
                .sum();
        if (columnCount > properties.maxSchemaColumns()) {
            throw new IllegalStateException("Hive Schema 字段总数 " + columnCount
                    + " 超过限制 " + properties.maxSchemaColumns()
                    + "，请配置 text2sql.allowed-tables 或黑白名单缩小范围");
        }

        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        mutableTables.forEach((key, value) -> tables.put(key,
                new TableDefinition(value.tableName(),
                        configService.tableComment(database, value.tableName()).orElse(value.comment()),
                        Map.copyOf(value.columns()))));
        String prompt = buildPromptSchema(database, tables, List.of(), columnCount);
        Snapshot loaded = new Snapshot(Instant.now(clock), Map.copyOf(tables), columnCount, prompt);

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("[Hive Schema加载完成] database={}, tables={}, columns={}, elapsedMs={}",
                databaseName, loaded.tables().size(), loaded.columnCount(), elapsedMs);
        return loaded;
    }

    private Map<String, MutableTable> rawHiveTables() {
        String databaseName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        Set<String> allowed = properties.allowedTables().stream()
                .map(SchemaCatalog::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return jdbcTemplate.execute((ConnectionCallback<Map<String, MutableTable>>) connection -> {
            configureHiveMetastoreSchema(connection);
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, MutableTable> mutableTables = new LinkedHashMap<>();
            try (ResultSet tableRows = metadata.getTables(
                    null, databaseName, "%", new String[]{"TABLE", "VIEW"})) {
                while (tableRows.next()) {
                    String tableName = tableRows.getString("TABLE_NAME");
                    if (!allowed.isEmpty() && !allowed.contains(normalize(tableName))) {
                        continue;
                    }
                    String comment = tableRows.getString("REMARKS");
                    MutableTable table = new MutableTable(tableName, comment, new LinkedHashMap<>());
                    String safeTableName = tableName.replace("`", "``");
                    int ordinalPosition = 0;
                    try (var statement = connection.createStatement();
                         ResultSet columnRows = statement.executeQuery(
                                 "DESCRIBE `" + safeTableName + "`")) {
                        while (columnRows.next()) {
                            String columnName = columnRows.getString(1);
                            if (columnName == null || columnName.isBlank()
                                    || columnName.startsWith("#")) {
                                break;
                            }
                            String type = columnRows.getString(2);
                            FieldDefinition field = new FieldDefinition(
                                    tableName,
                                    ++ordinalPosition,
                                    columnName,
                                    type == null ? "STRING" : type,
                                    columnRows.getString(3));
                            table.columns().put(normalize(field.columnName()), field);
                        }
                    }
                    if (!table.columns().isEmpty()) {
                        mutableTables.put(normalize(tableName), table);
                    }
                }
            }
            return mutableTables;
        });
    }

    /**
     * JsonSerDe 默认会让 DESCRIBE 使用反序列化器推导的字段信息，并把 DDL 中的字段注释
     * 显示成 "from deserializer"。当前 JDBC 会话把它加入使用 metastore schema 的 SerDe
     * 列表后，DESCRIBE 才会返回 CREATE TABLE 中保存的真实字段注释。
     */
    private void configureHiveMetastoreSchema(java.sql.Connection connection) {
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SET " + HIVE_METASTORE_SCHEMA_SERDES)) {
            String configuredValue = null;
            while (rows.next()) {
                String setting = rows.getString(1);
                String prefix = HIVE_METASTORE_SCHEMA_SERDES + "=";
                if (setting != null && setting.startsWith(prefix)) {
                    configuredValue = setting.substring(prefix.length()).trim();
                    break;
                }
            }
            if (configuredValue == null) {
                log.warn("[Hive Schema] 无法读取配置 {}，字段注释可能显示为 from deserializer",
                        HIVE_METASTORE_SCHEMA_SERDES);
                return;
            }

            LinkedHashSet<String> serdes = new LinkedHashSet<>();
            if (!configuredValue.isBlank()) {
                for (String serde : configuredValue.split(",")) {
                    if (!serde.isBlank()) {
                        serdes.add(serde.trim());
                    }
                }
            }
            if (!serdes.add(HIVE_JSON_SERDE)) {
                return;
            }

            try (var updateStatement = connection.createStatement()) {
                updateStatement.execute("SET " + HIVE_METASTORE_SCHEMA_SERDES
                        + "=" + String.join(",", serdes));
            }
            log.info("[Hive Schema] 当前会话已启用 JsonSerDe metastore 字段注释");
        } catch (SQLException exception) {
            log.warn("[Hive Schema] 配置 JsonSerDe metastore 字段注释失败，将使用 Hive 默认元数据: {}",
                    exception.getMessage());
        }
    }

    private String buildPromptSchema(DatabaseType database, Map<String, TableDefinition> tables,
                                     List<Relationship> relationships, int columnCount) {
        StringBuilder builder = new StringBuilder(Math.max(4096, columnCount * 80));
        String databaseLabel = database == DatabaseType.HIVE ? "Hive" : "MySQL";
        builder.append("当前").append(databaseLabel).append("数据库Schema，共")
                .append(tables.size()).append("张表、")
                .append(columnCount).append("个字段。\n");
        builder.append("格式：字段名 | ").append(databaseLabel).append("类型 | 字段注释\n");
        if (!relationships.isEmpty()) {
            builder.append("\n数据库外键关系：\n");
            relationships.forEach(relation -> builder.append("  ")
                    .append(relation.fromTable()).append('.').append(relation.fromColumn())
                    .append(" -> ").append(relation.toTable()).append('.')
                    .append(relation.toColumn()).append('\n'));
        }
        for (TableDefinition table : tables.values()) {
            builder.append("\nTABLE ").append(table.tableName());
            if (table.comment() != null && !table.comment().isBlank()) {
                builder.append(" -- ").append(table.comment());
            }
            builder.append('\n');
            table.columns().values().stream()
                    .sorted(java.util.Comparator.comparingInt(FieldDefinition::ordinalPosition))
                    .forEach(field -> builder.append("  ").append(field.columnName()).append(" | ")
                            .append(field.mysqlType()).append(" | ")
                            .append(field.comment() == null ? "" : field.comment()).append('\n'));
        }
        return builder.toString();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public record TableDefinition(String tableName, String comment, Map<String, FieldDefinition> columns) {
    }

    public record ResolvedField(String qualifier, FieldDefinition field) {
    }

    private record Relationship(String fromTable, String fromColumn, String toTable, String toColumn) {
    }

    private record MutableTable(String tableName, String comment, Map<String, FieldDefinition> columns) {
    }

    private record Snapshot(Instant loadedAt, Map<String, TableDefinition> tables,
                            int columnCount, String promptSchema) {
    }
}
