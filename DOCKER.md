# Docker 部署指南

## 📦 文件说明

- `Dockerfile` - 应用镜像构建文件（多阶段构建优化）
- `docker-compose.yml` - Docker Compose编排文件（包含MySQL、Redis和应用）
- `.dockerignore` - Docker构建时忽略的文件列表

## 🚀 快速开始

### 方式一：使用 Docker Compose（推荐）

一键启动所有服务（MySQL + Redis + 应用）：

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f app

# 停止所有服务
docker-compose down

# 停止并删除数据卷（谨慎使用）
docker-compose down -v
```

### 方式二：单独构建和运行应用

如果已有MySQL和Redis环境：

```bash
# 1. 构建镜像
docker build -t wps-password-backend:1.0.0 .

# 2. 运行容器
docker run -d \
  --name doc-auth-app \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://your-mysql-host:3306/doc_auth_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=your-password \
  -e SPRING_DATA_REDIS_HOST=your-redis-host \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATA_REDIS_PASSWORD=your-redis-password \
  wps-password-backend:1.0.0

# 3. 查看日志
docker logs -f doc-auth-app

# 4. 停止容器
docker stop doc-auth-app
```

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| SPRING_DATASOURCE_URL | MySQL连接地址 | - |
| SPRING_DATASOURCE_USERNAME | MySQL用户名 | root |
| SPRING_DATASOURCE_PASSWORD | MySQL密码 | - |
| SPRING_DATA_REDIS_HOST | Redis主机 | - |
| SPRING_DATA_REDIS_PORT | Redis端口 | 6379 |
| SPRING_DATA_REDIS_PASSWORD | Redis密码 | - |
| SPRING_DATA_REDIS_DATABASE | Redis数据库索引 | 0 |
| JAVA_OPTS | JVM参数 | -Xms256m -Xmx512m ... |
| TZ | 时区 | Asia/Shanghai |

### 端口映射

- **8080**: 应用服务端口
- **3316**: MySQL端口（仅docker-compose）
- **6479**: Redis端口（仅docker-compose）

## 📊 健康检查

应用配置了健康检查，可以通过以下方式查看状态：

```bash
# 查看容器健康状态
docker inspect --format='{{.State.Health.Status}}' doc-auth-app

# 访问健康检查端点
curl http://localhost:8080/actuator/health
```

## 💾 数据持久化

使用docker-compose时，数据会自动持久化到Docker卷：

- `mysql-data`: MySQL数据
- `redis-data`: Redis数据
- `app-logs`: 应用日志

## 🔍 常用命令

```bash
# 查看所有运行中的容器
docker-compose ps

# 进入应用容器
docker exec -it doc-auth-app sh

# 查看应用日志
docker-compose logs -f app

# 重启应用
docker-compose restart app

# 重新构建并启动
docker-compose up -d --build

# 查看资源使用情况
docker stats
```

## ⚙️ 性能优化

Dockerfile已包含以下优化：

1. **多阶段构建**：减小最终镜像体积
2. **依赖缓存**：先复制pom.xml下载依赖，利用Docker层缓存
3. **JRE运行时**：使用轻量级的JRE而非JDK
4. **Alpine基础镜像**：进一步减小镜像大小
5. **非root用户**：提升安全性
6. **G1GC垃圾回收器**：优化JVM性能
7. **健康检查**：自动监控应用状态

## 🐛 故障排查

### 应用启动失败

```bash
# 查看详细日志
docker-compose logs app

# 检查依赖服务是否正常
docker-compose ps

# 检查网络连接
docker-compose exec app ping mysql
docker-compose exec app ping redis
```

### 数据库连接失败

```bash
# 测试MySQL连接
docker-compose exec mysql mysql -uroot -proot123456 -e "SHOW DATABASES;"
```

### Redis连接失败

```bash
# 测试Redis连接
docker-compose exec redis redis-cli -a sean1234 ping
```

## 📝 注意事项

1. 首次启动时，MySQL需要初始化，可能需要等待30-60秒
2. 确保宿主机端口8080、3316、6479未被占用
3. 生产环境请修改默认密码
4. 建议定期备份数据卷
