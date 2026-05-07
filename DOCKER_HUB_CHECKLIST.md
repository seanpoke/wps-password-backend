# Docker Hub 配置检查清单

## ✅ 配置步骤清单

### 第一步：创建 Docker Hub Access Token
- [ ] 登录 Docker Hub (https://hub.docker.com)
- [ ] 进入 Account Settings → Security
- [ ] 点击 "New Access Token"
- [ ] 设置权限为 "Read & Write"
- [ ] 复制生成的 Token（只显示一次！）

### 第二步：配置 GitHub Secrets
- [ ] 进入 GitHub 仓库 → Settings → Secrets and variables → Actions
- [ ] 添加 Secret: `DOCKERHUB_USERNAME` = 你的 Docker Hub 用户名
- [ ] 添加 Secret: `DOCKERHUB_TOKEN` = 刚才创建的 Access Token

### 第三步：测试工作流
- [ ] 推送代码到 master 分支
- [ ] 或创建版本标签：`git tag v1.0.0 && git push origin v1.0.0`
- [ ] 查看 GitHub Actions 执行状态
- [ ] 确认推送到 ghcr.io 成功
- [ ] 确认推送到 docker.io 成功

### 第四步：验证镜像
- [ ] 访问 Docker Hub 仓库页面
- [ ] 确认镜像标签已生成
- [ ] 尝试本地拉取：`docker pull seanpoke/wps-password-backend:latest`

---

## 🔍 验证命令

### 1. 检查 Secrets 是否配置正确
在 GitHub 仓库的 Settings → Secrets 中应该看到：
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

### 2. 测试拉取镜像
```bash
docker pull seanpoke/wps-password-backend:latest
docker images | grep wps-password-backend
```

### 3. 查看工作流日志
访问：`https://github.com/seanpoke/wps-password-backend/actions`

应该看到类似输出：
```
✓ Login to Docker Hub successful
✓ Login to GitHub Container Registry successful
✓ Build and push completed
✓ Pushed to ghcr.io/seanpoke/wps-password-backend:master
✓ Pushed to seanpoke/wps-password-backend:latest
```

---

## 📋 生成的镜像标签

推送后会自动生成以下标签：

### Docker Hub (docker.io)
- `seanpoke/wps-password-backend:latest` - 最新版本
- `seanpoke/wps-password-backend:master` - master 分支
- `seanpoke/wps-password-backend:v1.0.0` - 完整版本号（打 tag 时）
- `seanpoke/wps-password-backend:v1.0` - 主版本号（打 tag 时）
- `seanpoke/wps-password-backend:abc1234` - Commit SHA

### GitHub Container Registry (ghcr.io)
- `ghcr.io/seanpoke/wps-password-backend:master`
- `ghcr.io/seanpoke/wps-password-backend:v1.0.0`
- `ghcr.io/seanpoke/wps-password-backend:abc1234`

---

## ⚠️ 常见问题检查

### 问题 1：工作流失败，提示认证错误
**检查项：**
- [ ] DOCKERHUB_USERNAME 是否正确（区分大小写）
- [ ] DOCKERHUB_TOKEN 是否有效（未过期）
- [ ] Token 是否有 Read & Write 权限

**解决方案：**
重新生成 Token 并更新 GitHub Secret

### 问题 2：找不到仓库
**检查项：**
- [ ] Docker Hub 用户名是否正确
- [ ] 镜像名称格式是否为 `username/repository`

**解决方案：**
Docker Hub 会自动创建仓库，确保用户名正确即可

### 问题 3：推送超时
**原因：**
网络问题导致推送失败

**解决方案：**
- 重试工作流（在 GitHub Actions 页面点击 "Re-run jobs"）
- 或稍后再试

---

## 🎯 完成标志

当你看到以下内容时，说明配置成功：

✅ GitHub Actions 工作流绿色通过  
✅ Docker Hub 仓库中出现新镜像  
✅ 能够成功拉取镜像：`docker pull seanpoke/wps-password-backend:latest`  

---

## 📞 需要帮助？

查看详细文档：
- [DOCKER_HUB_SETUP.md](DOCKER_HUB_SETUP.md) - 完整配置指南
- [DEPLOY.md](DEPLOY.md) - 部署流程说明
