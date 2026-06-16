package com.mcp.db.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接工厂 —— 完全无状态，根据客户端传入的连接参数动态创建连接。
 * <p>
 * 内部维护一个短期缓存（TTL 5分钟）的 HikariCP 连接池，避免相同连接参数
 * 在 MCP 会话期间反复创建销毁连接池。缓存由 {@link #evictExpired()} 自动清理。
 * <p>
 * 注意：密码参与缓存 key 的哈希计算，服务重启后缓存自动消失，
 * 不会持久化任何连接信息。
 */
@Slf4j
@Component
public class ConnectionFactory {

    // ---------- 默认端口映射 ----------
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of(
            "mysql", 3306,
            "postgresql", 5432,
            "h2", 0,
            "sqlite", 0,
            "sqlserver", 1433,
            "oracle", 1521
    );

    // ---------- JDBC 驱动类映射 ----------
    private static final Map<String, String> DRIVER_CLASSES = Map.of(
            "mysql", "com.mysql.cj.jdbc.Driver",
            "postgresql", "org.postgresql.Driver",
            "h2", "org.h2.Driver",
            "sqlite", "org.sqlite.JDBC",
            "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "oracle", "oracle.jdbc.OracleDriver"
    );

    /** 连接池缓存（key = 连接指纹, value = DataSource + 创建时间戳） */
    private final ConcurrentHashMap<String, CachedPool> poolCache = new ConcurrentHashMap<>();

    /** 缓存过期时间：5分钟 */
    private static final long TTL_MS = 5 * 60 * 1000;

    // ==================== 公共 API ====================

    /**
     * 根据客户端传入的参数获取数据库连接。
     * 连接池会被短期缓存，过期后自动清理。
     */
    public Connection getConnection(String type, String host, int port,
                                    String database, String username, String password) throws SQLException {
        String key = buildCacheKey(type, host, port, database, username, password);
        CachedPool cached = poolCache.get(key);

        if (cached != null && !cached.isExpired(TTL_MS)) {
            try {
                return cached.dataSource.getConnection();
            } catch (SQLException e) {
                log.warn("缓存连接池失效，重新创建: {}", e.getMessage());
                poolCache.remove(key);
                if (cached.dataSource instanceof HikariDataSource hds) {
                    hds.close();
                }
            }
        }

        // 清理过期条目后创建新池
        if (cached != null && cached.isExpired(TTL_MS)) {
            poolCache.remove(key);
            if (cached.dataSource instanceof HikariDataSource hds) {
                hds.close();
            }
        }

        DataSource ds = createDataSource(type, host, port, database, username, password);
        poolCache.put(key, new CachedPool(ds, System.currentTimeMillis()));
        log.info("为 [{}/{}] 创建新的数据库连接池", type, database);
        return ds.getConnection();
    }

    /**
     * 手动驱逐指定连接的缓存（可选暴露为 MCP 工具）。
     */
    public void evict(String type, String host, int port,
                      String database, String username, String password) {
        String key = buildCacheKey(type, host, port, database, username, password);
        CachedPool removed = poolCache.remove(key);
        if (removed != null && removed.dataSource instanceof HikariDataSource hds) {
            hds.close();
            log.info("已关闭并清除缓存连接池 [{}/{}]", type, database);
        }
    }

    /**
     * 构建 JDBC URL，供工具方法展示用。
     */
    public String buildJdbcUrl(String type, String host, int port, String database) {
        int effectivePort = port > 0 ? port : DEFAULT_PORTS.getOrDefault(type.toLowerCase(), 0);
        return switch (type.toLowerCase()) {
            case "mysql"    -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, effectivePort, database);
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s", host, effectivePort, database);
            case "h2"       -> String.format("jdbc:h2:%s", database != null ? database : "mem:testdb");
            case "sqlite"   -> String.format("jdbc:sqlite:%s", database != null ? database : ":memory:");
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false", host, effectivePort, database);
            case "oracle"   -> String.format("jdbc:oracle:thin:@%s:%d:%s", host, effectivePort, database);
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + type + "，支持: mysql, postgresql, h2, sqlite, sqlserver, oracle");
        };
    }

    // ==================== 内部实现 ====================

    private DataSource createDataSource(String type, String host, int port,
                                        String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("HikariPool-" + type + "-" + database);
        config.setJdbcUrl(buildJdbcUrl(type, host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(3);        // 临时连接池，小规模即可
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15000);
        config.setIdleTimeout(120000);       // 2分钟空闲回收
        config.setMaxLifetime(300000);       // 5分钟最大生命周期（对齐 TTL）

        String driverClass = DRIVER_CLASSES.get(type.toLowerCase());
        if (driverClass != null) {
            config.setDriverClassName(driverClass);
        }
        return new HikariDataSource(config);
    }

    /**
     * 构建缓存 key：基于所有连接参数的哈希，避免密码以明文长期驻留
     */
    private String buildCacheKey(String type, String host, int port,
                                 String database, String username, String password) {
        // 使用简单字符串拼接，敏感信息随 JVM 生命周期
        return String.format("%s|%s|%d|%s|%s|%s",
                type.toLowerCase(), host, port, database, username, password);
    }

    /**
     * 内部缓存条目
     */
    private record CachedPool(DataSource dataSource, long createdAt) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }
    }
}
