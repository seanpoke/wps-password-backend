# 服务器部署指南 - 外部配置文件

## 📋 配置加载优先级

Spring Boot 按以下优先级加载配置（从高到低）：

1. **命令行参数**：`java -jar app.jar --busi.outer-ip=xxx`
2. **环境变量**：`BUSI_OUTER_IP=xxx`
3. **外部配置文件**：`config/application.yml`（通过 `SPRING_CONFIG_LOCATION` 指定）
4. **jar包内配置文件**：`application.yml`

## 🚀 部署方式

### 方式一：使用外部配置文件（推荐）

#### 1. 准备目录结构

```bash
# 在服务器上创建目录
mkdir -p /opt/wps-password-backend/config
mkdir -p /opt/wps-password-backend/logs

# 上传 jar 包和配置文件
cp wps-password-backend-1.0.0.jar /opt/wps-password-backend/
cp config/application-prod.yml.example /opt/wps-password-backend/config/application-prod.yml
```

#### 2. 编辑外部配置文件

```bash
vi /opt/wps-password-backend/config/application-prod.yml
```

修改以下内容：
```yaml
spring:
  datasource:
    url: jdbc:mysql://your-mysql-host:3306/doc_auth_system?...
    username: your-username
    password: your-password
  
  data:
    redis:
      host: your-redis-host
      password: your-redis-password

busi:
  outer-ip: your-server-ip
```

#### 3. 启动应用

```bash
cd /opt/wps-password-backend

# 方式A：使用默认的外部配置路径
java -jar wps-password-backend-1.0.0.jar

# 方式B：手动指定外部配置文件
java -Dspring.config.location=file:./config/application-prod.yml \
     -jar wps-password-backend-1.0.0.jar

# 方式C：后台运行
nohup java -jar wps-password-backend-1.0.0.jar > logs/app.log 2>&1 &
```

### 方式二：使用环境变量覆盖

如果不想使用外部配置文件，可以直接通过环境变量覆盖：

```bash
# 设置环境变量
export BUSI_OUTER_IP=your-mysql-host
export SPRING_DATASOURCE_PASSWORD=your-password
export SPRING_REDIS_PASSWORD=your-redis-password

# 启动应用（会自动读取环境变量）
java -jar wps-password-backend-1.0.0.jar
```

### 方式三：使用命令行参数

```bash
java -jar wps-password-backend-1.0.0.jar \
  --busi.outer-ip=your-mysql-host \
  --spring.datasource.password=your-password \
  --spring.data.redis.password=your-redis-password
```

## 🔧 Docker Compose 部署

### 1. 准备配置文件

```bash
# 在项目根目录创建 config 目录
mkdir -p config

# 复制示例配置
cp config/application-prod.yml.example config/application-prod.yml

# 编辑配置
vi config/application-prod.yml
```

### 2. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f wps-app

# 停止服务
docker-compose down
```

### 3. 动态更新配置

```bash
# 1. 修改配置文件
vi config/application-prod.yml

# 2. 重启应用容器
docker-compose restart wps-app

# 或者重新构建并启动
docker-compose up -d --build
```

## 📝 配置文件说明

### 必需配置项

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `busi.outer-ip` | 外部服务IP地址 | `192.168.1.100` |
| `spring.datasource.url` | MySQL连接地址 | `jdbc:mysql://host:3306/db` |
| `spring.datasource.username` | MySQL用户名 | `root` |
| `spring.datasource.password` | MySQL密码 | `password` |
| `spring.data.redis.host` | Redis主机 | `192.168.1.100` |
| `spring.data.redis.password` | Redis密码 | `password` |

### 可选配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 应用端口 | `8080` |
| `springdoc.swagger-ui.enabled` | 是否启用Swagger | `true` |
| `spring.jpa.show-sql` | 是否显示SQL | `false` |

## 🔍 验证配置是否生效

### 1. 查看启动日志

```bash
# 查看应用日志
tail -f logs/app.log

# 或查看Docker日志
docker-compose logs -f wps-app
```

查找类似输出：
```
The following profiles are active: prod
Loading external configuration from: file:/app/config/application-prod.yml
```

### 2. 检查运行时配置

```bash
# 访问actuator端点（需要添加spring-boot-starter-actuator依赖）
curl http://localhost:8080/actuator/env | grep busi.outer-ip
```

### 3. 测试数据库连接

```bash
# 查看应用是否正常启动
curl http://localhost:8080/swagger-ui.html
```

## ⚠️ 注意事项

1. **配置文件位置**：
   - Docker环境：`/app/config/application.yml`
   - 直接运行：`./config/application.yml` 或通过 `-Dspring.config.location` 指定

2. **权限问题**：
   ```bash
   # 确保配置文件可读
   chmod 644 config/application-prod.yml
   
   # 如果使用Docker，确保文件所有者正确
   chown -R 1000:1000 config/
   ```

3. **敏感信息保护**：
   ```bash
   # 不要将包含密码的配置文件提交到Git
   echo "config/application-prod.yml" >> .gitignore
   
   # 或使用环境变量管理敏感信息
   export DB_PASSWORD=secret
   ```

4. **配置热更新**：
   - Spring Boot 不支持运行时自动重新加载配置文件
   - 修改配置后需要重启应用

5. **多环境配置**：
   ```bash
   # 开发环境
   java -Dspring.profiles.active=dev -jar app.jar
   
   # 生产环境
   java -Dspring.profiles.active=prod -jar app.jar
   ```

## 🐛 故障排查

### 问题1：配置未生效

```bash
# 检查配置文件路径是否正确
ls -la config/application-prod.yml

# 检查文件格式是否正确
cat config/application-prod.yml

# 查看启动日志中的配置加载信息
grep -i "config" logs/app.log
```

### 问题2：数据库连接失败

```bash
# 测试MySQL连接
mysql -h your-host -u root -p

# 检查防火墙
telnet your-host 3306

# 查看应用日志
tail -100 logs/app.log | grep -i "datasource"
```

### 问题3：Redis连接失败

```bash
# 测试Redis连接
redis-cli -h your-host -a your-password ping

# 检查防火墙
telnet your-host 6379
```

## 📊 最佳实践

1. **使用环境变量管理敏感信息**
   ```bash
   export SPRING_DATASOURCE_PASSWORD=$(vault read -field=password secret/db)
   java -jar app.jar
   ```

2. **配置文件版本管理**
   ```bash
   # 只提交模板文件
   git add config/application-prod.yml.example
   
   # 忽略实际配置文件
   echo "config/application-prod.yml" >> .gitignore
   ```

3. **定期备份配置**
   ```bash
   cp config/application-prod.yml config/application-prod.yml.bak.$(date +%Y%m%d)
   ```

4. **使用配置中心（高级）**
   - Spring Cloud Config
   - Apollo
   - Nacos
