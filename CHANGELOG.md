# Changelog

所有对本项目的显著变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-06-16

### 新增

- 基于 Spring AI MCP Server 的数据库 MCP 服务
- 支持 MySQL、PostgreSQL、H2、SQLite、SQL Server、Oracle 六大数据库
- 四级权限控制：READ_ONLY、READ_WRITE、DDL、FULL
- HikariCP 连接池管理，支持多数据源
- MCP 工具集：listDatabases、listTables、describeTable、listIndexes、executeQuery、executeUpdate、getDatabaseInfo
- SSE 传输协议支持
- Docker 容器化部署支持
- Docker Compose 一键部署（含 MySQL 示例）
