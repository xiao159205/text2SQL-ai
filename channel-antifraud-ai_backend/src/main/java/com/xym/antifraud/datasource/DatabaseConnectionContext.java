package com.xym.antifraud.datasource;

/** 当前数据查询使用的用户连接；为空时继续使用系统内置的数据源。 */
public final class DatabaseConnectionContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private DatabaseConnectionContext() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static void use(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) CURRENT.remove();
        else CURRENT.set(connectionId);
    }

    public static String cacheKey(DatabaseType fallbackType) {
        String connectionId = current();
        return connectionId == null ? "builtin:" + fallbackType.name() : "user:" + connectionId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
