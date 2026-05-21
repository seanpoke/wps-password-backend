# WPS Password Backend

WPS Password Backend 是一个基于 Spring Boot 的文档安全管理系统后端服务。它主要为 WPS 插件提供文档密码管理、用户身份认证（LDAP）、细粒度的文档权限控制以及操作日志记录功能。系统采用 ECC 加密算法确保密码传输与存储的安全性，并支持多版本密钥管理。

## 主要功能

- **用户认证**：集成 LDAP 进行企业级用户身份验证，支持 Token 机制维持登录状态。
- **文档权限管理**：基于 LDAP 组织架构（部门/用户）实现文档的细粒度授权，支持所有权与共享权限区分。
- **密码安全管理**：采用 ECC (ECIES) 椭圆曲线加密算法对文档密码进行端到端加密，支持密钥轮换与多版本管理。
- **操作审计**：异步记录文档密码修改、访问等关键操作日志，支持溯源与密码找回辅助。
- **配置动态刷新**：支持通过 API 动态更新 LDAP、缓存及系统配置，无需重启服务。

## 技术栈

- **核心框架**: Java 17, Spring Boot 3.2.0
- **数据存储**: MySQL 8.0 (JPA/Hibernate), Redis 7.2 (会话与缓存)
- **目录服务**: Spring Data LDAP (Active Directory/OpenLDAP)
- **加密算法**: BouncyCastle (ECC/ECIES, AES-CBC)
- **工具库**: Lombok, SpringDoc OpenAPI (Swagger UI)
- **构建与部署**: Maven 3.9.6, Docker, Docker Compose

## 项目结构

```text
src/main/java/com/docauth/
├── config/             # 配置类 (Async, LDAP, Swagger, WebMvc)
├── context/            # 用户上下文管理 (ThreadLocal)
├── controller/         # RESTful API 控制器 (Account, Config, Doc)
├── dto/                # 数据传输对象 (Request/Response)
├── entity/             # JPA 实体类 (DocInfo, DocShareRel, etc.)
├── interceptor/        # Token 拦截器 (鉴权与心跳保活)
├── repository/         # 数据访问层 (JPA Repositories)
├── service/            # 业务逻辑层 (Account, Doc, Ldap, Config)
└── util/               # 工具类 (EccUtil, RedisUtil)
```

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 7.0+
- Docker & Docker Compose (推荐)

## 快速开始

### 1. 使用 Docker Compose 部署（推荐）

项目根目录下提供了完整的 `docker-compose.yml`，一键启动应用、MySQL 和 Redis：

```bash
docker-compose up -d
```

启动后，应用将运行在 `http://localhost:8081`。

### 2. 本地开发运行

#### 数据库初始化
执行 `ci/sql/init.sql` 脚本创建数据库、表结构及初始配置。

#### 配置文件
修改 `src/main/resources/application-dev.yml` 中的数据库和 Redis 连接信息。

#### 启动应用
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. API 文档
启动应用后，访问 Swagger UI 查看在线接口文档：
[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

## 核心业务流程

### 1. 登录与鉴权
1. 客户端调用 `/account/login` 传入账号密码。
2. 服务端通过 LDAP 校验身份，生成 UUID 作为 Token 并存入 Redis（有效期 72 小时）。
3. 后续请求需在 Header 中携带 `token`，由 `TokenInterceptor` 自动校验并刷新过期时间。

### 2. 文档授权与访问
1. **获取所有者**：调用 `/doc/owner`，若文档不存在则自动创建并绑定当前用户为所有者。
2. **更新权限**：所有者调用 `/doc/auth/update`，传入授权的 LDAP DN（部门或用户）。
3. **获取密码**：被授权用户调用 `/doc/password`，传入 ECC 公钥加密后的密码密文。
4. **权限校验**：服务端根据 LDAP 树形关系判断当前用户是否在授权节点的子路径下。

### 3. 密钥管理
- 系统维护一个 `config_secret_key` 表，支持多版本密钥。
- 客户端通过 `/config/latest-key` 获取最新公钥用于加密。
- 服务端根据请求中的 `keyVersion` 匹配对应的私钥进行解密。

## 数据库设计

| 表名 | 说明 | 关键字段 |
| :--- | :--- | :--- |
| `doc_info` | 文档基本信息 | uid, account (所有者), file_name |
| `doc_share_rel` | 文档授权关系 | uid, type (0部门/1用户), dn (LDAP路径) |
| `doc_password_log` | 密码操作日志 | uid, before/after_password, possible_password (JSON) |
| `config_secret_key` | ECC 密钥配置 | public_key, private_key, key_version, order_num |
| `doc_config` | 系统动态配置 | type, key, value (LDAP/Cache/System配置) |

## 安全特性

- **ECC 加密**：使用 NIST P-256 曲线，相比 RSA 具有更高的安全性与更短的密文长度。
- **临时模式 (isTemp)**：特定接口支持跳过文件存在性与所有者校验，适用于特殊业务场景。
- **异步日志**：密码解密与日志入库采用异步处理，避免阻塞主业务流程。
- **动态配置**：敏感配置（如 LDAP 密码）存储在数据库中，支持运行时刷新。

## 常见问题

**Q: 如何更换 LDAP 服务器地址？**
A: 调用 `PUT /config/ldap` 接口更新 `url` 字段，或直接在数据库 `doc_config` 表中修改后调用 `/config/refresh`。

**Q: 密钥如何轮换？**
A: 向 `config_secret_key` 表插入新密钥并设置更大的 `order_num`。客户端下次调用 `/config/latest-key` 时将自动获取新公钥。

**Q: 为什么解密失败？**
A: 请检查 `keyVersion` 是否与加密时使用的公钥版本一致。系统支持多版本密钥共存以兼容历史数据。

## 许可证

本项目仅供内部使用。
