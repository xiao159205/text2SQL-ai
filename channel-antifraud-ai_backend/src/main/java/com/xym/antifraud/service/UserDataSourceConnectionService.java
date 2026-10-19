package com.xym.antifraud.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.xym.antifraud.datasource.DatabaseType;
import com.xym.antifraud.dto.datasource.DataSourceConnectionRequest;
import com.xym.antifraud.dto.datasource.DataSourceConnectionResponse;
import com.xym.antifraud.dto.datasource.DataSourceTestResponse;
import com.xym.antifraud.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class UserDataSourceConnectionService {
    private static final Pattern HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]+$");
    private static final Pattern DATABASE_PATTERN = Pattern.compile("^[a-zA-Z0-9_$-]{1,128}$");
    private static final int CONNECTION_TIMEOUT_MS = 8_000;
    private final Map<String, UserConnection> connections = new ConcurrentHashMap<>();

    public DataSourceTestResponse test(DataSourceConnectionRequest request) {
        ValidatedConnection input = validate(request, false);
        long startedAt = System.nanoTime();
        try (HikariDataSource dataSource = createDataSource(input, null);
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW DATABASES")) {
            List<String> databases = new ArrayList<>();
            while (rows.next()) databases.add(rows.getString(1));
            databases.sort(Comparator.naturalOrder());
            return new DataSourceTestResponse(List.copyOf(databases),
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (Exception exception) {
            throw connectionFailed(exception);
        }
    }

    public DataSourceConnectionResponse connect(Long userId, DataSourceConnectionRequest request) {
        ValidatedConnection input = validate(request, true);
        HikariDataSource dataSource = null;
        try {
            dataSource = createDataSource(input, input.database());
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
            String id = UUID.randomUUID().toString();
            UserConnection selected = new UserConnection(id, userId, input.type(), input.host(),
                    input.port(), input.database(), dataSource);
            dataSource = null;
            connections.entrySet().removeIf(entry -> {
                UserConnection existing = entry.getValue();
                if (existing.userId().equals(userId) && existing.type() == input.type()) {
                    existing.dataSource().close();
                    return true;
                }
                return false;
            });
            connections.put(id, selected);
            return selected.response();
        } catch (Exception exception) {
            if (dataSource != null) dataSource.close();
            throw connectionFailed(exception);
        }
    }

    public void requireOwned(Long userId, String connectionId, DatabaseType type) {
        UserConnection connection = require(connectionId);
        if (!connection.userId().equals(userId) || connection.type() != type) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DATA_SOURCE_CONNECTION_FORBIDDEN",
                    "该数据库连接不属于当前用户");
        }
    }

    public DataSource dataSource(String connectionId) {
        return require(connectionId).dataSource();
    }

    @PreDestroy
    void closeAll() {
        connections.values().forEach(connection -> connection.dataSource().close());
        connections.clear();
    }

    private UserConnection require(String connectionId) {
        UserConnection connection = connectionId == null ? null : connections.get(connectionId);
        if (connection == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DATA_SOURCE_CONNECTION_REQUIRED",
                    "数据库连接已失效，请重新填写连接信息并测试");
        }
        return connection;
    }

    private ValidatedConnection validate(DataSourceConnectionRequest request, boolean requireDatabase) {
        DatabaseType type = DatabaseType.from(request.databaseType());
        String host = request.host().trim();
        String database = request.database() == null ? null : request.database().trim();
        if (!HOST_PATTERN.matcher(host).matches()) throw new IllegalArgumentException("数据库地址格式不正确");
        if (requireDatabase && (database == null || !DATABASE_PATTERN.matcher(database).matches())) {
            throw new IllegalArgumentException("请选择有效的数据库");
        }
        return new ValidatedConnection(type, host, request.port(), request.username().trim(),
                request.password() == null ? "" : request.password(), database);
    }

    private HikariDataSource createDataSource(ValidatedConnection input, String database) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("UserDb-" + UUID.randomUUID().toString().substring(0, 8));
        config.setDriverClassName(input.type() == DatabaseType.HIVE
                ? "org.apache.hive.jdbc.HiveDriver" : "com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl(input, database));
        config.setUsername(input.username());
        config.setPassword(input.password());
        if (input.type() == DatabaseType.MYSQL) config.setReadOnly(true);
        config.setMaximumPoolSize(database == null ? 1 : 3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setValidationTimeout(5_000);
        config.setInitializationFailTimeout(CONNECTION_TIMEOUT_MS);
        return new HikariDataSource(config);
    }

    private String jdbcUrl(ValidatedConnection input, String database) {
        String host = input.host().contains(":") && !input.host().startsWith("[")
                ? "[" + input.host() + "]" : input.host();
        String selected = database == null || database.isBlank()
                ? (input.type() == DatabaseType.HIVE ? "default" : "") : database;
        if (input.type() == DatabaseType.HIVE) {
            return "jdbc:hive2://" + host + ":" + input.port() + "/" + selected;
        }
        return "jdbc:mysql://" + host + ":" + input.port() + "/" + selected
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    private BusinessException connectionFailed(Exception exception) {
        String detail = exception.getMessage() == null ? "未知错误" : exception.getMessage();
        if (detail.length() > 300) detail = detail.substring(0, 300);
        return new BusinessException(HttpStatus.BAD_REQUEST, "DATA_SOURCE_CONNECTION_FAILED",
                "数据库连接失败：" + detail);
    }

    private record ValidatedConnection(DatabaseType type, String host, int port,
                                       String username, String password, String database) {
    }

    private record UserConnection(String id, Long userId, DatabaseType type, String host,
                                  int port, String database, HikariDataSource dataSource) {
        DataSourceConnectionResponse response() {
            String typeLabel = type == DatabaseType.HIVE ? "Hive" : "MySQL";
            return new DataSourceConnectionResponse(id, type.name().toLowerCase(Locale.ROOT), host,
                    port, database, typeLabel + " · " + host + ":" + port + "/" + database);
        }
    }
}
