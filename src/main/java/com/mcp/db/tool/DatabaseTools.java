package com.mcp.db.tool;

import com.mcp.db.config.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 数据库操作 MCP 工具集 —— 完全无状态版本。
 * <p>
 * 每次工具调用都由 MCP 客户端传入完整的数据库连接参数（类型、主机、端口、
 * 数据库名、用户名、密码），服务端不持久化任何连接信息或数据。
 * <p>
 * 权限由数据库用户自身的 GRANT 权限决定，服务端不再做额外的权限拦截。
 */
@Slf4j
@Component
public class DatabaseTools {

    private final ConnectionFactory connectionFactory;

    /**
     * 用于检测 SQL 是否为只读查询（SELECT / WITH / EXPLAIN / SHOW / DESCRIBE）
     */
    private static final Pattern READ_ONLY_PATTERN = Pattern.compile(
            "^\\s*(SELECT|WITH|EXPLAIN|SHOW|DESCRIBE|DESC)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public DatabaseTools(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    // ==================== 工具：列出表 ====================

    /**
     * 列出指定数据库中的所有表。
     */
    @Tool(description = """
            列出数据库中的所有表。
            需要提供完整的数据库连接信息（类型、主机、端口、数据库名、用户名、密码）。
            返回：表名、表类型（TABLE/VIEW）、所属模式等信息。""")
    public List<Map<String, Object>> listTables(
            @ToolParam(description = "数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle") String type,
            @ToolParam(description = "数据库主机地址，如 localhost 或 192.168.1.100") String host,
            @ToolParam(description = "数据库端口号（0 表示使用该类型默认端口）") int port,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "模式名称（可选）。MySQL 中对应数据库名，PostgreSQL 中对应 schema 名。留空则查询所有模式", required = false) String schema) {

        log.info("MCP Tool: listTables - type={}, host={}, database={}", type, host, database);
        try (Connection conn = connectionFactory.getConnection(type, host, port, database, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<Map<String, Object>> tables = new ArrayList<>();

            String catalog = conn.getCatalog();
            String schemaPattern = (schema != null && !schema.isBlank()) ? schema : null;

            try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    Map<String, Object> tableInfo = new LinkedHashMap<>();
                    tableInfo.put("tableName", rs.getString("TABLE_NAME"));
                    tableInfo.put("tableType", rs.getString("TABLE_TYPE"));
                    tableInfo.put("schema", rs.getString("TABLE_SCHEM"));
                    tableInfo.put("catalog", rs.getString("TABLE_CAT"));
                    tableInfo.put("remarks", rs.getString("REMARKS"));
                    tables.add(tableInfo);
                }
            }
            log.info("listTables 返回 {} 个表", tables.size());
            return tables;
        } catch (SQLException e) {
            log.error("listTables 执行失败: {}", e.getMessage());
            throw new RuntimeException("列出数据库表失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具：查看表结构 ====================

    /**
     * 描述指定表的列信息（字段名、类型、是否可空、主键等）。
     */
    @Tool(description = """
            获取指定表的完整列信息。
            返回：列名、数据类型、是否可空、默认值、是否主键、是否自增、注释等。""")
    public List<Map<String, Object>> describeTable(
            @ToolParam(description = "数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle") String type,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口号（0 表示使用默认端口）") int port,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "表名") String table,
            @ToolParam(description = "模式名称（可选）", required = false) String schema) {

        log.info("MCP Tool: describeTable - type={}, database={}, table={}", type, database, table);
        try (Connection conn = connectionFactory.getConnection(type, host, port, database, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schemaPattern = (schema != null && !schema.isBlank()) ? schema : null;

            // 获取主键列
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schemaPattern, table)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // 获取列信息
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, table, "%")) {
                while (rs.next()) {
                    Map<String, Object> colInfo = new LinkedHashMap<>();
                    String columnName = rs.getString("COLUMN_NAME");
                    colInfo.put("columnName", columnName);
                    colInfo.put("dataType", rs.getInt("DATA_TYPE"));
                    colInfo.put("typeName", rs.getString("TYPE_NAME"));
                    colInfo.put("columnSize", rs.getInt("COLUMN_SIZE"));
                    colInfo.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    colInfo.put("defaultValue", rs.getString("COLUMN_DEF"));
                    colInfo.put("isPrimaryKey", primaryKeys.contains(columnName));
                    colInfo.put("isAutoIncrement", "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")));
                    colInfo.put("remarks", rs.getString("REMARKS"));
                    colInfo.put("ordinalPosition", rs.getInt("ORDINAL_POSITION"));
                    columns.add(colInfo);
                }
            }

            log.info("describeTable 返回 {} 个列", columns.size());
            return columns;
        } catch (SQLException e) {
            log.error("describeTable 执行失败: {}", e.getMessage());
            throw new RuntimeException("获取表结构失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具：查看索引 ====================

    /**
     * 获取表的索引信息。
     */
    @Tool(description = """
            获取指定表的所有索引信息。
            返回：索引名、列名、是否唯一索引、排序方向等。""")
    public List<Map<String, Object>> listIndexes(
            @ToolParam(description = "数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle") String type,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口号（0 表示使用默认端口）") int port,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "表名") String table,
            @ToolParam(description = "模式名称（可选）", required = false) String schema) {

        log.info("MCP Tool: listIndexes - type={}, database={}, table={}", type, database, table);
        try (Connection conn = connectionFactory.getConnection(type, host, port, database, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schemaPattern = (schema != null && !schema.isBlank()) ? schema : null;

            List<Map<String, Object>> indexes = new ArrayList<>();
            try (ResultSet rs = metaData.getIndexInfo(catalog, schemaPattern, table, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;
                    Map<String, Object> idxInfo = new LinkedHashMap<>();
                    idxInfo.put("indexName", indexName);
                    idxInfo.put("columnName", rs.getString("COLUMN_NAME"));
                    idxInfo.put("nonUnique", rs.getBoolean("NON_UNIQUE"));
                    idxInfo.put("type", rs.getShort("TYPE"));
                    idxInfo.put("ordinalPosition", rs.getShort("ORDINAL_POSITION"));
                    idxInfo.put("ascending", rs.getString("ASC_OR_DESC"));
                    indexes.add(idxInfo);
                }
            }
            return indexes;
        } catch (SQLException e) {
            log.error("listIndexes 执行失败: {}", e.getMessage());
            throw new RuntimeException("获取索引信息失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具：只读查询 ====================

    /**
     * 执行 SELECT 只读查询，以结构化方式返回结果。
     */
    @Tool(description = """
            执行只读 SQL 查询（SELECT / WITH / SHOW / DESCRIBE / EXPLAIN）。
            返回 JSON 数组格式的查询结果，包含列名和数据行。默认最多返回100行。
            如需执行写操作，请使用 executeUpdate 工具。""")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle") String type,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口号（0 表示使用默认端口）") int port,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "SQL 查询语句，仅支持 SELECT / WITH / SHOW / DESCRIBE / EXPLAIN") String sql,
            @ToolParam(description = "返回行数上限，默认100，最大1000", required = false) Integer limit) {

        log.info("MCP Tool: executeQuery - type={}, database={}, sql={}", type, database, sql);

        // 安全检查：仅允许只读 SQL
        if (!READ_ONLY_PATTERN.matcher(sql).matches()) {
            throw new IllegalArgumentException(
                    "仅允许执行只读查询（SELECT/WITH/SHOW/DESCRIBE/EXPLAIN）。如需写操作请使用 executeUpdate。");
        }

        int maxRows = (limit != null && limit > 0) ? Math.min(limit, 1000) : 100;

        try (Connection conn = connectionFactory.getConnection(type, host, port, database, username, password);
             Statement stmt = conn.createStatement()) {

            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(30);

            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(rsmd.getColumnLabel(i));
                }

                List<List<Object>> rows = new ArrayList<>();
                int rowCount = 0;
                while (rs.next() && rowCount < maxRows) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                    rowCount++;
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("rowCount", rowCount);
                result.put("sql", sql);
                result.put("type", type);
                result.put("database", database);

                log.info("executeQuery 返回 {} 行, {} 列", rowCount, columnCount);
                return result;
            }
        } catch (SQLException e) {
            log.error("executeQuery 执行失败: {}", e.getMessage());
            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具：写操作 ====================

    /**
     * 执行 INSERT / UPDATE / DELETE / DDL 语句。
     */
    @Tool(description = """
            执行写操作 SQL（INSERT / UPDATE / DELETE / DDL）。
            返回影响行数。注意：此操作会修改数据，请谨慎使用。
            不支持 SELECT 查询，SELECT 请使用 executeQuery 工具。""")
    public Map<String, Object> executeUpdate(
            @ToolParam(description = "数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle") String type,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口号（0 表示使用默认端口）") int port,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "SQL 写操作语句（INSERT / UPDATE / DELETE / CREATE / ALTER / DROP 等）") String sql) {

        log.info("MCP Tool: executeUpdate - type={}, database={}, sql={}", type, database, sql);

        // 禁止 SELECT 查询
        if (READ_ONLY_PATTERN.matcher(sql).matches()) {
            throw new IllegalArgumentException(
                    "executeUpdate 不支持只读查询。请使用 executeQuery 工具执行 SELECT 语句。");
        }

        try (Connection conn = connectionFactory.getConnection(type, host, port, database, username, password);
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(30);

            boolean isResultSet = stmt.execute(sql);
            int updateCount = stmt.getUpdateCount();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("affectedRows", updateCount);
            result.put("sql", sql);
            result.put("type", type);
            result.put("database", database);
            result.put("success", true);

            log.info("executeUpdate 影响 {} 行", updateCount);
            return result;
        } catch (SQLException e) {
            log.error("executeUpdate 执行失败: {}", e.getMessage());
            throw new RuntimeException("写操作执行失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具：数据库元信息 ====================

    /**
     * 获取数据库的元数据信息（数据库产品名、版本、驱动等）。
     */
    @Tool(description = """
            获取数据库服务器的元数据信息。
            返回：数据库产品名、版本、JDBC 驱动名、是否支持事务/批处理等。""")
    public Map<String, Object> getDatabaseInfo(
            @ToolParam(description = "数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle") String type,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口号（0 表示使用默认端口）") int port,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password) {

        log.info("MCP Tool: getDatabaseInfo - type={}, database={}", type, database);
        try (Connection conn = connectionFactory.getConnection(type, host, port, database, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("databaseProductName", metaData.getDatabaseProductName());
            info.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            info.put("driverName", metaData.getDriverName());
            info.put("driverVersion", metaData.getDriverVersion());
            info.put("url", connectionFactory.buildJdbcUrl(type, host, port, database));
            info.put("userName", metaData.getUserName());
            info.put("supportsTransactions", metaData.supportsTransactions());
            info.put("supportsBatchUpdates", metaData.supportsBatchUpdates());
            info.put("maxConnections", metaData.getMaxConnections());
            info.put("identifierQuoteString", metaData.getIdentifierQuoteString());

            return info;
        } catch (SQLException e) {
            log.error("getDatabaseInfo 执行失败: {}", e.getMessage());
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
    }
}
