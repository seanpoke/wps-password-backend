# ---- 阶段1：构建前端（Vite 输出到 /app/webroot，与后端 static-locations 对应）----
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 阶段2：构建后端 Jar 包 ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
# 复制 pom.xml 并下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- 阶段3：运行 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
LABEL maintainer="Sean"
LABEL description="wps插件后端服务（含前端静态资源）"

# 创建非 root 用户运行应用（安全最佳实践）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从 builder 阶段复制后端 jar 与前端的 webroot 静态资源
COPY --from=builder /app/target/wps-password-backend-1.0.0.jar app.jar
COPY --from=frontend-builder /app/webroot /app/webroot

# 创建日志目录并修正所有者
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 暴露应用端口（实际端口映射在 docker-compose.yml 中定义）
EXPOSE 8081

ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:NewRatio=1 -XX:SurvivorRatio=8 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=4m -XX:ConcGCThreads=2 -XX:G1ReservePercent=10 -XX:InitiatingHeapOccupancyPercent=45 -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# 启动应用
# Spring Boot 会按以下优先级加载配置：
# 1. 命令行参数  2. SPRING_CONFIG_LOCATION 指定的外部文件  3. jar 包内的 application.yml
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
