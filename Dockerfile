# 使用多阶段构建
# Stage 1: 编译
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: 运行
FROM eclipse-temurin:21-jre-alpine AS runtime

# 创建非 root 用户
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

# 复制构建产物
COPY --from=build /app/target/mcp-db-server-*.jar app.jar

# 创建配置和数据目录
RUN mkdir -p /app/config /app/data && \
    chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 暴露端口
EXPOSE 8088

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8088/actuator/health || exit 1

# 启动
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

# 可选：指定外部配置文件
# CMD ["--spring.config.location=file:/app/config/application.yml"]
