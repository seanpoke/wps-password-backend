#!/bin/bash
# Linux 服务器自动部署脚本

set -e

DEPLOY_DIR="/opt/wps-password-backend"
ENV_FILE="$DEPLOY_DIR/.env"

echo "========================================="
echo "  WPS Password Backend 部署脚本"
echo "========================================="
echo ""

# 检查是否以 root 运行
if [ "$EUID" -ne 0 ]; then
    echo "❌ 请使用 sudo 运行此脚本"
    exit 1
fi

# 1. 检查 Docker
echo "[1/6] 检查 Docker..."
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装"
    exit 1
fi
echo "✅ Docker 版本: $(docker --version)"

# 2. 检查 Docker Compose
echo ""
echo "[2/6] 检查 Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose 未安装"
    exit 1
fi
echo "✅ Docker Compose 版本: $(docker-compose --version)"

# 3. 检查部署目录
echo ""
echo "[3/6] 检查部署目录..."
if [ ! -d "$DEPLOY_DIR" ]; then
    echo "❌ 部署目录不存在: $DEPLOY_DIR"
    exit 1
fi
cd $DEPLOY_DIR
echo "✅ 当前目录: $(pwd)"

# 4. 检查 .env 文件
echo ""
echo "[4/6] 检查配置文件..."
if [ ! -f "$ENV_FILE" ]; then
    echo "⚠️  .env 文件不存在，从模板创建..."
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo "✅ 已创建 .env 文件"
        echo "⚠️  请编辑 .env 文件修改配置后再继续"
        exit 1
    else
        echo "❌ 找不到 .env.example 模板文件"
        exit 1
    fi
else
    echo "✅ .env 文件存在"
fi

# 5. 检查必要目录
echo ""
echo "[5/6] 检查必要目录..."
mkdir -p config logs mysql/conf mysql/init redis/conf

if [ ! -f "redis/conf/redis.conf" ] && [ -f "redis/conf/redis.conf.example" ]; then
    cp redis/conf/redis.conf.example redis/conf/redis.conf
    echo "✅ 已创建 Redis 配置文件"
fi

# 6. 启动服务
echo ""
echo "[6/6] 启动服务..."

# 拉取最新镜像（如果使用远程镜像）
if grep -q "ghcr.io\|docker.io" docker-compose.yml; then
    echo "   拉取最新镜像..."
    docker-compose pull
fi

# 停止旧容器
echo "   停止旧容器..."
docker-compose down 2>/dev/null || true

# 启动新容器
echo "   启动新容器..."
docker-compose up -d

if [ $? -ne 0 ]; then
    echo "❌ 服务启动失败"
    docker-compose logs --tail=50
    exit 1
fi

echo "✅ 服务启动成功"

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="
echo ""
echo "容器状态:"
docker-compose ps
echo ""
echo "查看日志: docker-compose logs -f wps-app"
echo "停止服务: docker-compose down"
echo "重启服务: docker-compose restart"
echo ""

# 检查健康状态
if docker-compose ps | grep -q "Up"; then
    echo "✅ 服务运行正常"
    
    # 测试应用健康接口
    if command -v curl &> /dev/null; then
        echo ""
        echo "测试应用健康检查..."
        HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health 2>/dev/null || echo "failed")
        if [ "$HEALTH_STATUS" = "200" ]; then
            echo "✅ 应用健康检查通过"
        else
            echo "⚠️  应用健康检查返回: $HEALTH_STATUS"
        fi
    fi
else
    echo "⚠️  服务可能存在问题，请检查日志"
    docker-compose logs --tail=20 wps-app
fi
