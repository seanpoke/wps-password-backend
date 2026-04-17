# 多阶段构建 - 第一阶段：构建
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /app

# 复制pom.xml并下载依赖（利用Docker缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 执行Maven构建，跳过测试
RUN mvn clean package -DskipTests -B

# 多阶段构建 - 第二阶段：运行
FROM eclipse-temurin:17-jre-alpine

# 设置维护者信息
LABEL maintainer="Sean"
LABEL description="wps插件后端服务"

# 设置工作目录
WORKDIR /app

# 创建非root用户运行应用（安全最佳实践）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从builder阶段复制构建好的jar包
COPY --from=builder /app/target/wps-password-backend-1.0.0.jar app.jar

# 修改文件所有者
RUN chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露应用端口
EXPOSE 8080

# JVM参数优化
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
