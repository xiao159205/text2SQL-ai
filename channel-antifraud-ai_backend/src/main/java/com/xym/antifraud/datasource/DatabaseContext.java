package com.xym.antifraud.datasource;

/** 保存当前请求选择的数据源，供动态路由在取连接时使用。 */
public final class DatabaseContext {
    private static final ThreadLocal<DatabaseType> CURRENT = new ThreadLocal<>();

    private DatabaseContext() {
    }

    public static DatabaseType current() {
        DatabaseType database = CURRENT.get();
        return database == null ? DatabaseType.MYSQL : database;
    }

    public static void use(DatabaseType database) {
        CURRENT.set(database);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
