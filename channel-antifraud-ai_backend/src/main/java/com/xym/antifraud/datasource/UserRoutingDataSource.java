package com.xym.antifraud.datasource;

import com.xym.antifraud.service.UserDataSourceConnectionService;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** 在系统内置连接与用户临时连接之间进行请求级路由。 */
public class UserRoutingDataSource extends AbstractDataSource {
    private final Map<DatabaseType, DataSource> builtInDataSources;
    private final UserDataSourceConnectionService connectionService;

    public UserRoutingDataSource(Map<DatabaseType, DataSource> builtInDataSources,
                                 UserDataSourceConnectionService connectionService) {
        this.builtInDataSources = Map.copyOf(builtInDataSources);
        this.connectionService = connectionService;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return selected().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return selected().getConnection(username, password);
    }

    private DataSource selected() {
        String connectionId = DatabaseConnectionContext.current();
        if (connectionId != null) return connectionService.dataSource(connectionId);
        DataSource builtIn = builtInDataSources.get(DatabaseContext.current());
        if (builtIn == null) throw new IllegalStateException("没有可用的系统数据源");
        return builtIn;
    }
}
