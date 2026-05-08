# Docker Compose 配置优化说明

## 📋 主要改进

### 1. ✅ 修复的问题

#### 端口不一致
- **问题**: Dockerfile 健康检查使用 8080 端口，但应用实际运行在 8081 端口
- **解决**: 统一修改为 8081 端口

#### Redis 密码配置
- **问题**: Redis 官方镜像不支持通过环境变量设置密码
- **解决**: 通过 `command` 参数传递 `--requirepass` 选项

### 2. 🔧 新增功能

#### 环境变量管理
- 创建 `.env.example` 文件作为模板
- 所有敏感信息通过环境变量配置
- 使用默认值语法 `${VAR:-default}` 确保向后兼容

**使用方法**:
```bash
# 复制示例文件
cp .env.example .env

# 编辑 .env 文件修改配置
vim .env

# 启动服务（自动读取 .env）
docker-compose up -d
```

#### 资源限制
为每个容器添加了 CPU 和内存限制：

| 服务 | CPU 限制 | 内存限制 | 保留内存 |
|------|---------|---------|---------|
| wps-app | 1.0 core | 512MB | 256MB |
| mysql8 | - | 1GB | 512MB |
| redis | - | 256MB | 128MB |

#### 日志管理
配置了日志轮转，避免磁盘空间被占满：
- 单个日志文件最大: 10MB (Redis: 5MB)
- 保留文件数量: 3 个
- 总日志大小: 最多 30MB (Redis: 15MB)

#### 健康检查
- **MySQL**: 使用 `mysqladmin ping` 检查数据库是否就绪
- **Redis**: 使用 `redis-cli ping` 检查 Redis 是否响应
- **wps-app**: 依赖 MySQL 健康状态，确保数据库就绪后再启动

#### 挂载权限优化
- 配置文件挂载为只读 (`:ro`)，提高安全性
- 日志目录保持读写权限

### 3. 🚀 性能优化

#### Redis 镜像
- 从 `redis:7.2` 改为 `redis:7.2-alpine`
- 镜像体积更小，启动更快

#### 启动顺序
```
redis (立即启动)
  ↓
mysql (等待健康检查通过)
  ↓
wps-app (等待 MySQL 就绪)
```

## 📝 配置步骤

### 第一步：准备环境变量
```bash
cp .env.example .env
# 编辑 .env 文件，修改密码等敏感信息
```

### 第二步：准备配置文件
```bash
# Redis 配置
cp redis/conf/redis.conf.example redis/conf/redis.conf

# MySQL 配置（如果需要自定义）
mkdir -p mysql/conf mysql/init

# 应用配置
mkdir -p config logs
```

### 第三步：启动服务
```bash
# 首次启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 检查服务状态
docker-compose ps
```

## 🔍 验证部署

### 检查健康状态
```bash
# 查看所有容器状态
docker-compose ps

# 应该看到：
# wps_app_container    Up (healthy)
# mysql8_container     Up (healthy)  
# redis_container      Up (healthy)
```

### 测试连接
```bash
# 测试应用健康接口
curl http://localhost:8081/actuator/health

# 测试 Redis 连接
docker exec -it redis_container redis-cli -a sean1234 ping

# 测试 MySQL 连接
docker exec -it mysql8_container mysql -u testuser -ptest123456 mydb
```

## ⚙️ 常用命令

```bash
# 查看实时日志
docker-compose logs -f wps-app

# 重启单个服务
docker-compose restart wps-app

# 停止所有服务
docker-compose down

# 停止并删除数据卷（危险操作！）
docker-compose down -v

# 查看资源使用情况
docker stats

# 更新镜像后重新部署
docker-compose pull
docker-compose up -d
```

## 🛡️ 安全建议

### 生产环境必做
1. **修改默认密码**: 编辑 `.env` 文件，修改所有密码
2. **限制外部访问**: 如不需要外部访问数据库，移除 ports 映射
3. **使用 HTTPS**: 在生产环境配置反向代理（如 Nginx）+ SSL 证书
4. **定期备份**: 备份 MySQL 数据和 Redis 数据

### 可选：隐藏数据库端口
如果只有 wps-app 需要访问数据库和 Redis，可以注释掉端口映射：

```yaml
# mysql8:
#   ports:
#     - "${MYSQL_PORT:-3316}:3306"  # 注释掉这行

# redis:
#   ports:
#     - "${REDIS_PORT:-6479}:6379"  # 注释掉这行
```

## 📊 监控建议

### 日志查看
```bash
# 应用日志
tail -f logs/wps-password-backend-all.log

# Docker 日志
docker-compose logs --tail=100 wps-app
```

### 资源监控
```bash
# 实时监控容器资源使用
docker stats

# 查看具体容器
docker stats wps_app_container
```

## ❓ 故障排查

### 问题 1: 容器启动失败
```bash
# 查看详细日志
docker-compose logs wps-app

# 检查配置文件
docker-compose config
```

### 问题 2: 数据库连接失败
```bash
# 检查 MySQL 健康状态
docker inspect mysql8_container | grep Health

# 手动测试连接
docker exec -it mysql8_container mysql -u root -proot123456 -e "SHOW DATABASES;"
```

### 问题 3: Redis 认证失败
```bash
# 测试 Redis 连接
docker exec -it redis_container redis-cli -a sean1234 ping

# 检查 Redis 配置
docker exec -it redis_container cat /etc/redis/redis.conf
```

## 🔄 更新流程

当 GitHub 上有新镜像时：

```bash
# 1. 拉取最新镜像
docker-compose pull

# 2. 重新启动服务
docker-compose up -d

# 3. 清理旧镜像
docker image prune -f
```

## 📌 注意事项

1. **首次部署**: 确保创建了必要的目录和配置文件
2. **数据持久化**: MySQL 和 Redis 数据存储在 Docker volume 中，不会因容器删除而丢失
3. **配置修改**: 修改 `.env` 或配置文件后，需要重启相应服务
4. **端口冲突**: 确保宿主机上的 8081、3316、6479 端口未被占用
