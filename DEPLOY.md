# Windows → Linux 部署指南

## 📦 镜像来源选择

本项目支持两种镜像获取方式：

### 方案 A：内网镜像传输（适合内网环境）
- ✅ 不依赖外网
- ✅ 完全可控
- 📖 详见下方「内网部署流程」

### 方案 B：Docker Hub 拉取（推荐用于有外网环境）⭐
- ✅ 自动化推送
- ✅ 国内访问较快
- ✅ 版本管理清晰
- 📖 详见 [DOCKER_HUB_SETUP.md](DOCKER_HUB_SETUP.md)

**镜像地址：** `seanpoke/wps-password-backend:latest`

---

## 🚀 内网部署流程

```
Windows 本地                    Linux 服务器
    │                              │
    ├─ Maven 构建                  │
    ├─ Docker 构建镜像             │
    ├─ 导出为 tar.gz               │
    └────── SCP 传输 ────────────→├─ 导入镜像
                                   ├─ 启动服务
                                   └─ 完成 ✅
```

---

## 🚀 快速部署

### 步骤 1：Windows 本地 - 构建并传输

```powershell
# 首次使用，允许执行脚本
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned

# 一键构建并传输到 Linux 服务器
.\deploy-to-linux.ps1 -ServerIP "你的服务器IP" -ServerUser "root"
```

**示例：**
```powershell
.\deploy-to-linux.ps1 -ServerIP "192.168.1.100" -ServerUser "root" -DeployDir "/opt/deploy"
```

**自动完成：**
- ✅ Maven 构建 JAR 包
- ✅ Docker 构建镜像
- ✅ 导出并压缩（生成 `.tar.gz`）
- ✅ SCP 传输到服务器

---

### 步骤 2：Linux 服务器 - 导入并启动

SSH 登录服务器后执行：

```bash
# 进入部署目录
cd /opt/deploy

# 运行部署脚本
chmod +x linux-import-and-deploy.sh
./linux-import-and-deploy.sh
```

**自动完成：**
- ✅ 解压镜像文件
- ✅ 导入 Docker 镜像
- ✅ 启动所有服务（App + MySQL + Redis）
- ✅ 健康检查

---

## 🛠️ 常用命令

### Windows PowerShell

```powershell
# 构建并传输
.\deploy-to-linux.ps1 -ServerIP "192.168.1.100" -ServerUser "root"

# 仅本地构建（不传输）
.\build-local.ps1
```

### Linux Bash

```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f wps-app

# 重启服务
docker-compose restart wps-app

# 停止服务
docker-compose down

# 清理旧镜像
docker image prune -a
```

---

## 🔐 SSH 免密登录配置（推荐）

```powershell
# Windows 生成密钥
ssh-keygen -t ed25519 -C "your_email@example.com"

# 复制公钥到服务器
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@192.168.1.100 "cat >> ~/.ssh/authorized_keys"
```

---

## ❓ 故障排查

### 问题 1：SCP 传输失败
```powershell
# 测试 SSH 连接
ssh root@192.168.1.100
```

### 问题 2：服务启动失败
```bash
# 查看详细日志
docker-compose logs wps-app

# 检查容器状态
docker-compose ps
```

### 问题 3：端口被占用
```bash
# 检查端口占用
netstat -tlnp | grep 8080
```

---

## 📊 预期时间

| 步骤 | 耗时 |
|------|------|
| Maven 构建 | 2-3 分钟 |
| Docker 构建 | 1-2 分钟 |
| 压缩 + 传输 | 2-5 分钟 |
| 导入 + 启动 | 1-2 分钟 |
| **总计** | **6-12 分钟** |

---

## 📁 相关文件

- `deploy-to-linux.ps1` - Windows 端部署脚本（内网传输）
- `build-local.ps1` - Windows 本地构建脚本
- `linux-import-and-deploy.sh` - Linux 服务器部署脚本
- `docker-compose.yml` - Docker Compose 配置
- `.github/workflows/docker-publish.yml` - GitHub Actions CI/CD 配置

---

## 🔗 相关文档

- [DOCKER_HUB_SETUP.md](DOCKER_HUB_SETUP.md) - Docker Hub 自动推送配置指南
- [DOCKER.md](DOCKER.md) - Docker 构建说明
