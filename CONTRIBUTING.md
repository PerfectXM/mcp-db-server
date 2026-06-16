# 贡献指南

感谢你对 mcp-db-server 的关注！我们欢迎任何形式的贡献。

## 如何贡献

### 报告问题

如果你发现了 Bug 或有功能建议，请通过 GitHub Issues 提交。

提交 Issue 时请包含：

- **问题描述**：清晰描述发生了什么或期望什么
- **复现步骤**：如何触发该问题
- **环境信息**：JDK 版本、操作系统、数据库类型及版本
- **日志**：相关的错误日志或截图

### 提交代码

1. **Fork** 本项目
2. 创建你的特性分支：`git checkout -b feature/my-new-feature`
3. 编写代码，确保符合项目代码风格
4. 添加必要的测试
5. 提交你的改动：`git commit -m 'feat: 添加某功能'`
6. 推送到分支：`git push origin feature/my-new-feature`
7. 提交 **Pull Request**

### 提交信息规范

我们使用 [约定式提交](https://www.conventionalcommits.org/zh-hans/) 规范：

- `feat:` 新功能
- `fix:` 修复 Bug
- `docs:` 文档变更
- `style:` 代码格式调整（不影响功能）
- `refactor:` 代码重构
- `perf:` 性能优化
- `test:` 添加或修改测试
- `chore:` 构建过程或辅助工具的变动

### 代码风格

- 遵循 Java 标准命名规范（camelCase）
- 使用 Lombok 简化代码（`@Slf4j`、`@Data` 等）
- 注释使用中文，面向国内开发者
- 日志使用英文（便于国际化检索）

### 添加新数据库支持

如需添加新的数据库类型，请按以下步骤操作：

1. 在 `ConnectionFactory.java` 中：
   - `DEFAULT_PORTS` 添加默认端口映射
   - `DRIVER_CLASSES` 添加 JDBC 驱动类映射
   - `buildJdbcUrl()` 添加 JDBC URL 构建逻辑
2. 在 `pom.xml` 中添加对应的 JDBC 驱动依赖
3. 更新 `README.md` 中"支持的数据库"表格

### 开发环境配置

```bash
# 克隆项目
git clone https://github.com/your-org/mcp-db-server.git
cd mcp-db-server

# 编译
./mvnw compile

# 运行测试
./mvnw test

# 启动
./mvnw spring-boot:run
```

## 行为准则

- 尊重所有贡献者
- 使用友好和包容的语言
- 接受建设性批评
- 关注对社区最有利的事情

## License

贡献的代码将采用与项目相同的 [MIT License](LICENSE)。
