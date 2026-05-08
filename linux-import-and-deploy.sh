#!/bin/bash
# Linux 服务器端 - 自动导入镜像并部署

set -e

DEPLOY_DIR="/opt/deploy"
TAR_FILE="wps-password-backend.tar"
GZ_FILE="wps-password-backend.tar.gz"
COMPOSE_FILE="docker-compose.yml"

echo "========================================="
echo "  Linux 服务器部署脚本"
echo "========================================="
echo ""

cd $DEPLOY_DIR

# 1. 检查文件
echo "[1/4] 检查文件..."
if [ ! -f "$GZ_FILE" ] && [ ! -f "$TAR_FILE" ]; then
    echo "❌ 未找到镜像文件"
    echo "   请先从 Windows 传输文件到: $DEPLOY_DIR"
    echo "   示例命令:"
    echo "   scp wps-password-backend.tar.gz root@server:$DEPLOY_DIR/"
    exit 1
fi
echo "✅ 文件存在"

# 2. 解压（如果需要）
if [ -f "$GZ_FILE" ]; then
    echo ""
    echo "[2/4] 解压文件..."
    gunzip -f $GZ_FILE
    echo "✅ 解压完成"
else
    echo ""
    echo "[2/4] 跳过解压（已是 tar 格式）"
fi

# 3. 导入镜像
echo ""
echo "[3/4] 导入 Docker 镜像..."
docker load -i $TAR_FILE
if [ $? -ne 0 ]; then
    echo "❌ 镜像导入失败"
    exit 1
fi
echo "✅ 镜像导入成功"

# 验证镜像
echo ""
echo "已导入的镜像:"
docker images | grep wps-password-backend

# 4. 启动服务
echo ""
echo "[4/4] 启动服务..."

# 检查 docker-compose.yml 是否存在
if [ ! -f "$COMPOSE_FILE" ]; then
    echo "⚠️  未找到 docker-compose.yml，创建默认配置..."
    cat > $COMPOSE_FILE <<'EOF'
version: '3.8'

services:
  wps-app:
    image: wps-password-backend:latest
    container_name: wps_app_container
    restart: always
    ports:
      - "8080:8080"
    depends_on:
      - mysql8
      - redis
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - BUSI_OUTER_IP=mysql8
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql8:3306/mydb?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
      - SPRING_DATASOURCE_USERNAME=testuser
      - SPRING_DATASOURCE_PASSWORD=test123456
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
      - SPRING_REDIS_PASSWORD=sean1234
    volumes:
      - ./config:/app/config
      - ./logs:/app/logs
    networks:
      - wps_network

  mysql8:
    image: mysql:8.0
    container_name: mysql8_container
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: root123456
      MYSQL_DATABASE: mydb
      MYSQL_USER: testuser
      MYSQL_PASSWORD: test123456
      TZ: Asia/Shanghai
    ports:
      - "3316:3306"
    volumes:
      - mysql8_data:/var/lib/mysql
    networks:
      - wps_network

  redis:
    image: redis:7.2
    container_name: redis_container
    restart: always
    environment:
      TZ: Asia/Shanghai
      REDIS_PASSWORD: sean1234
    ports:
      - "6479:6379"
    volumes:
      - redis_data:/data
    networks:
      - wps_network
    command: redis-server --requirepass sean1234

volumes:
  mysql8_data:
  redis_data:

networks:
  wps_network:
    driver: bridge
EOF
    echo "✅ 已创建 docker-compose.yml"
    echo "   ⚠️  请根据实际情况修改配置"
fi

# 停止旧容器
echo "   停止旧容器..."
docker-compose down 2>/dev/null || true

# 启动新容器
echo "   启动新容器..."
docker-compose up -d

if [ $? -ne 0 ]; then
    echo "❌ 服务启动失败"
    exit 1
fi

echo "✅ 服务启动成功"

# 显示状态
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
echo ""

# 等待服务启动
echo "等待服务启动..."
sleep 5

# 检查服务健康状态
if docker-compose ps | grep -q "Up"; then
    echo "✅ 服务运行正常"
else
    echo "⚠️  服务可能存在问题，请检查日志"
    docker-compose logs --tail=20 wps-app
fi
