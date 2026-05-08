# Docker Hub 自动推送配置指南

## 📋 概述

GitHub Actions 工作流已配置为**同时推送到两个镜像仓库**：
1. **GitHub Container Registry (ghcr.io)** - 原有的镜像仓库
2. **Docker Hub (docker.io)** - 新增的镜像仓库

---

## 🔐 第一步：创建 Docker Hub Access Token

### 1. 登录 Docker Hub
访问 https://hub.docker.com 并登录你的账号

### 2. 创建 Access Token
1. 点击右上角头像 → **Account Settings**
2. 左侧菜单选择 **Security**
3. 点击 **New Access Token**
4. 填写信息：
   - **Access Token Name**: `github-actions`（或其他名称）
   - **Description**: `用于 GitHub Actions 自动推送镜像`
   - **Permissions**: 选择 **Read & Write**
5. 点击 **Generate**
6. **重要**：复制生成的 token（只显示一次，务必保存！）

---

## ⚙️ 第二步：配置 GitHub Secrets

### 在 GitHub 仓库中添加 Secrets

1. 进入你的 GitHub 仓库
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**
4. 添加以下两个 secrets：

#### Secret 1: DOCKERHUB_USERNAME
- **Name**: `DOCKERHUB_USERNAME`
- **Secret**: 你的 Docker Hub 用户名（例如：`seanpoke`）

#### Secret 2: DOCKERHUB_TOKEN
- **Name**: `DOCKERHUB_TOKEN`
- **Secret**: 刚才创建的 Docker Hub Access Token

---

## 🚀 第三步：触发自动构建

推送代码到 master 分支或创建版本标签：

```bash
# 方式 1：推送到 master 分支（生成 latest 标签）
git push origin master

# 方式 2：创建版本标签（生成版本标签）
git tag v1.0.0
git push origin v1.0.0
```

---

## 📦 生成的镜像标签

### GitHub Container Registry (ghcr.io)
```
ghcr.io/seanpoke/wps-password-backend:master
ghcr.io/seanpoke/wps-password-backend:v1.0.0
ghcr.io/seanpoke/wps-password-backend:abc1234  # commit sha
```

### Docker Hub (docker.io)
```
seanpoke/wps-password-backend:latest
seanpoke/wps-password-backend:master
seanpoke/wps-password-backend:v1.0.0
seanpoke/wps-password-backend:v1.0
seanpoke/wps-password-backend:abc1234  # commit sha
```

---

## 🎯 使用镜像

### 从 Docker Hub 拉取

```bash
# 拉取最新版本
docker pull seanpoke/wps-password-backend:latest

# 拉取指定版本
docker pull seanpoke/wps-password-backend:v1.0.0
```

### 修改 docker-compose.yml 使用 Docker Hub

如果你想让服务器从 Docker Hub 拉取（国内速度更快），修改 `docker-compose.yml`：

```yaml
services:
  wps-app:
    image: seanpoke/wps-password-backend:latest  # 改为 Docker Hub 地址
    # build: .  # 注释掉本地构建
    container_name: wps_app_container
    # ... 其他配置保持不变
```

或者保持当前配置（使用本地构建），在服务器上手动拉取：

```bash
# 在 Linux 服务器上
docker pull seanpoke/wps-password-backend:latest
docker tag seanpoke/wps-password-backend:latest wps-password-backend:latest
docker-compose up -d
```

---

## ✅ 验证推送是否成功

### 1. 查看 GitHub Actions 执行结果
访问：`https://github.com/seanpoke/wps-password-backend/actions`

### 2. 查看 Docker Hub 仓库
访问：`https://hub.docker.com/r/seanpoke/wps-password-backend/tags`

应该能看到自动推送的镜像标签。

---

## 🔧 故障排查

### 问题 1：认证失败

**错误信息**：`unauthorized: authentication required`

**解决方案**：
1. 检查 `DOCKERHUB_USERNAME` 和 `DOCKERHUB_TOKEN` 是否正确
2. 确认 Token 有 **Read & Write** 权限
3. 重新生成 Token 并更新 Secret

### 问题 2：找不到镜像

**错误信息**：`repository not found`

**解决方案**：
1. 确认 Docker Hub 用户名正确
2. 确保镜像名称格式为：`username/repository-name`
3. 如果仓库不存在，Docker Hub 会自动创建（需要账号权限）

### 问题 3：推送速度慢

**解决方案**：
- Docker Hub 在国内访问可能较慢，这是正常的
- 可以考虑使用阿里云 ACR 作为国内镜像仓库（需要额外配置）

---

## 📊 完整的 CI/CD 流程

```
开发者推送代码
       ↓
GitHub Actions 触发
       ↓
Maven 构建 JAR 包
       ↓
Docker 构建镜像
       ↓
       ├────→ 推送到 ghcr.io（GitHub Container Registry）
       └────→ 推送到 docker.io（Docker Hub）
       ↓
Linux 服务器拉取镜像
       ↓
部署完成 ✅
```

---

## 💡 最佳实践

### 1. 版本管理策略

```bash
# 开发阶段：推送到 master 分支
git push origin master
# 生成：latest, master 标签

# 发布版本：打标签
git tag v1.0.0
git push origin v1.0.0
# 生成：v1.0.0, v1.0, latest 标签
```

### 2. 服务器部署脚本优化

在 Linux 服务器上创建自动更新脚本 `/opt/deploy/update.sh`：

```bash
#!/bin/bash
set -e

echo "拉取最新镜像..."
docker pull seanpoke/wps-password-backend:latest

echo "重启服务..."
cd /opt/deploy
docker-compose down
docker-compose up -d

echo "清理旧镜像..."
docker image prune -f

echo "✅ 更新完成"
docker-compose ps
```

### 3. 定时更新（可选）

使用 cron 定时检查更新：

```bash
# 每天凌晨 2 点自动更新
0 2 * * * /opt/deploy/update.sh >> /var/log/wps-update.log 2>&1
```

---

## 🔗 相关资源

- [Docker Hub 官方文档](https://docs.docker.com/docker-hub/)
- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Docker Buildx 文档](https://docs.docker.com/buildx/)

---

## ❓ 常见问题

**Q: 为什么要同时推送到两个仓库？**
A: 
- ghcr.io：与 GitHub 集成好，适合开源项目
- Docker Hub：全球最流行的镜像仓库，国内访问相对较快

**Q: 可以只推送到 Docker Hub 吗？**
A: 可以，删除工作流中 GHCR 相关的步骤即可。

**Q: 如何测试工作流而不推送代码？**
A: 在 GitHub Actions 页面手动触发 workflow。

**Q: 镜像会保留多久？**
A: 
- Docker Hub：公共仓库无限期保留
- ghcr.io：根据 GitHub 存储配额管理
