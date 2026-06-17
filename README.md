# mcp-db-server

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.4.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MCP](https://img.shields.io/badge/MCP-1.1.7-purple.svg)](https://modelcontextprotocol.io/)

基于 **Spring AI MCP Server** 的通用数据库 MCP 服务。**完全无状态**——数据库连接信息由 AI 客户端在每次调用时传入，服务端不存储任何连接配置或数据。

支持 **MySQL、PostgreSQL、H2、SQLite、SQL Server、Oracle** 六大数据库。

---

## 目录

- [特性](#特性)
- [设计理念](#设计理念)
- [支持的数据库](#支持的数据库)
- [快速开始](#快速开始)
  - [Maven 本地运行](#maven-本地运行)
  - [Docker 部署](#docker-部署)
  - [JAR 包部署](#jar-包部署)
- [MCP 工具接口](#mcp-工具接口)
- [集成到 AI 客户端](#集成到-ai-客户端)
- [项目结构](#项目结构)
- [开发指南](#开发指南)
- [License](#license)

---

## 特性

- 🔌 **MCP 协议**：基于 Spring AI MCP Server，SSE 传输协议，无缝对接 Claude Desktop、Cursor 等 AI 客户端
- 🗄️ **多数据库支持**：MySQL、PostgreSQL、H2、SQLite、SQL Server、Oracle
- 🔓 **完全无状态**：不存储任何连接配置或数据，连接信息由客户端在每次调用时传入
- 🚀 **HikariCP 连接池**：相同连接参数自动复用短期缓存（5分钟 TTL），兼顾性能与安全
- 🐳 **Docker 部署**：提供 Dockerfile 和 docker-compose.yml
- 🛠️ **6 个 MCP 工具**：表查询、表结构、索引、SQL 查询、SQL 写操作、数据库元信息

---

## 设计理念

```
┌──────────────┐    连接参数(每调)    ┌──────────────────┐     JDBC     ┌────────────┐
│  AI 客户端    │ ──────────────────▶ │  mcp-db-server   │ ───────────▶ │  数据库     │
│ (Claude等)   │ ◀────────────────── │  (无状态服务)      │ ◀─────────── │ (MySQL等)  │
└──────────────┘    查询结果           └──────────────────┘              └────────────┘
```

- 服务端**不持久化**任何数据库连接信息
- AI 客户端每次调用工具时，将 `type`、`host`、`port`、`database`、`username`、`password` 作为参数传入
- 服务端在内存中短期缓存连接池（5 分钟 TTL），相同参数自动复用，过期自动释放
- 权限由数据库用户自身的 GRANT 决定，服务端不做额外拦截

---

## 支持的数据库

| 数据库       | 类型标识     | 默认端口 | 状态   |
| ------------ | ------------ | -------- | ------ |
| MySQL        | `mysql`      | 3306     | ✅ 稳定 |
| PostgreSQL   | `postgresql` | 5432     | ✅ 稳定 |
| H2           | `h2`         | -        | ✅ 稳定 |
| SQLite       | `sqlite`     | -        | ✅ 稳定 |
| SQL Server   | `sqlserver`  | 1433     | ✅ 稳定 |
| Oracle       | `oracle`     | 1521     | ✅ 稳定 |

---

## 快速开始

### 环境要求

- **JDK** 17+ （推荐 JDK 21）
- **Maven** 3.6+ （或使用项目内置的 Maven Wrapper）
- **Docker** （可选）

### Maven 本地运行

```bash
# Windows (PowerShell)
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

服务启动后，MCP SSE 端点为 `http://localhost:8088/sse`。

### Docker 部署

```bash
# 构建镜像
docker build -t mcp-db-server .

# 运行容器
docker run -d -p 8088:8088 --name mcp-db-server mcp-db-server
```


### JAR 包部署

```bash
./mvnw clean package -DskipTests
java -jar target/mcp-db-server-1.0.0.jar
```

---

## MCP 工具接口

每个工具都需要 AI 客户端传入完整的数据库连接参数。

### 公共连接参数（每个工具方法都包含）

| 参数       | 类型   | 必填 | 说明                              |
| ---------- | ------ | ---- | --------------------------------- |
| `type`     | String | ✅   | 数据库类型：mysql, postgresql, h2, sqlite, sqlserver, oracle |
| `host`     | String | ✅   | 数据库主机地址                    |
| `port`     | int    | ✅   | 端口号（传 0 使用该类型默认端口） |
| `database` | String | ✅   | 数据库名称                        |
| `username` | String | ✅   | 数据库用户名                      |
| `password` | String | ✅   | 数据库密码                        |

### 工具列表

| 工具名称          | 描述                                                  | 特有参数              |
| ----------------- | ----------------------------------------------------- | --------------------- |
| `listTables`      | 列出数据库中的所有表                                  | `schema`（可选）      |
| `describeTable`   | 获取指定表的列信息（字段、类型、主键、自增等）        | `table`, `schema`（可选） |
| `listIndexes`     | 获取指定表的索引信息                                  | `table`, `schema`（可选） |
| `executeQuery`    | 执行只读 SQL（SELECT/SHOW/DESCRIBE/EXPLAIN）          | `sql`, `limit`（可选，默认100） |
| `executeUpdate`   | 执行写操作（INSERT/UPDATE/DELETE/DDL）                | `sql`                 |
| `getDatabaseInfo` | 获取数据库元数据（产品名、版本、驱动、特性等）        | —                     |

### 示例：AI 调用 listTables

当 AI 客户端需要列出本地 MySQL 数据库的表时，它会调用：

```
Tool: listTables
  type     = "mysql"
  host     = "localhost"
  port     = 3306
  database = "mydb"
  username = "root"
  password = "****"
```

---

## 集成到 AI 客户端

### Claude Desktop (claude_desktop_config.json)

```json
{
  "mcpServers": {
    "mcp-db-server": {
      "type": "sse",
      "url": "http://localhost:8088/sse"
    }
  }
}
```

### Cursor

```json
{
  "mcpServers": {
    "mcp-db-server": {
      "transport": "sse",
      "url": "http://localhost:8088/sse"
    }
  }
}
```

### Trae

在 Trae 的 MCP 设置中添加：

```json
{
  "mcpServers": {
    "mcp-db-server": {
      "transport": "sse",
      "url": "http://localhost:8088/sse"
    }
  }
}
```

> **注意**：不同客户端使用的 SSE 端点路径可能不同。本项目配置的 SSE 端点为 `/sse`，所有客户端统一使用 `http://localhost:8088/sse` 连接。

---

## 项目结构

```
mcp-db-server/
├── src/main/java/com/mcp/db/
│   ├── McpDbServerApplication.java      # 启动类
│   ├── config/
│   │   └── ConnectionFactory.java        # 动态连接工厂（含连接池缓存）
│   └── tool/
│       └── DatabaseTools.java            # MCP 工具方法（@Tool 注解）
├── src/main/resources/
│   └── application.yml                   # 应用配置（仅服务端口和 MCP 设置）
├── sql/init/
│   └── 01-init-demo.sql                  # MySQL 示例初始化数据
├── Dockerfile                            # Docker 镜像构建
├── docker-compose.yml                    # 一键部署（含 MySQL 示例）
├── .env.example                          # 环境变量模板
├── pom.xml                               # Maven 依赖管理
└── LICENSE
```

---

## 开发指南

### IDE 配置

推荐使用 **IntelliJ IDEA**。

### 构建

```bash
# 编译
./mvnw compile

# 运行测试
./mvnw test

# 打包
./mvnw clean package -DskipTests
```

### 添加新数据库支持

1. 在 `ConnectionFactory.java` 中：
   - `DEFAULT_PORTS` 添加默认端口映射
   - `DRIVER_CLASSES` 添加 JDBC 驱动类映射
   - `buildJdbcUrl()` 添加 JDBC URL 构建逻辑
2. 在 `pom.xml` 中添加对应的 JDBC 驱动依赖
3. 更新本文档中"支持的数据库"表格

欢迎提交 PR！

---

## 贡献

请参阅 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何参与贡献。

---

## License

本项目基于 [MIT License](LICENSE) 开源。
