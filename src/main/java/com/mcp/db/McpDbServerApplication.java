package com.mcp.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP 数据库服务 - 启动类。
 * <p>
 * 该服务作为 MCP（Model Context Protocol）Server 运行，
 * 向 AI 模型暴露数据库操作能力：查询表、查看表结构、执行 SQL 等。
 * <p>
 * 支持数据库类型：MySQL、PostgreSQL、H2、SQLite、SQL Server、Oracle。
 */
@SpringBootApplication
public class McpDbServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpDbServerApplication.class, args);
    }
}
