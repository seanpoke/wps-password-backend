# Windows 本地构建并传输到 Linux 服务器

param(
    [Parameter(Mandatory=$true)]
    [string]$ServerIP,
    
    [Parameter(Mandatory=$true)]
    [string]$ServerUser,
    
    [string]$DeployDir = "/opt/deploy"
)

$ErrorActionPreference = "Stop"
$IMAGE_NAME = "wps-password-backend:latest"
$TAR_FILE = "wps-password-backend.tar"
$COMPRESSED_FILE = "wps-password-backend.tar.gz"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Windows → Linux 部署工具" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "目标服务器: ${ServerUser}@${ServerIP}:${DeployDir}" -ForegroundColor Yellow
Write-Host ""

# 1. 检查 Docker
Write-Host "[1/6] 检查 Docker..." -ForegroundColor Yellow
try {
    docker info | Out-Null
    Write-Host "✅ Docker 正常" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker 未运行" -ForegroundColor Red
    exit 1
}

# 2. 检查 Maven
Write-Host ""
Write-Host "[2/6] 检查 Maven..." -ForegroundColor Yellow
try {
    mvn -version | Out-Null
    Write-Host "✅ Maven 正常" -ForegroundColor Green
} catch {
    Write-Host "❌ Maven 未安装" -ForegroundColor Red
    exit 1
}

# 3. Maven 构建
Write-Host ""
Write-Host "[3/6] Maven 构建..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Maven 构建失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Maven 构建成功" -ForegroundColor Green

# 4. Docker 构建
Write-Host ""
Write-Host "[4/6] Docker 构建镜像..." -ForegroundColor Yellow
docker build -t $IMAGE_NAME .
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Docker 构建失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Docker 镜像构建成功" -ForegroundColor Green

# 5. 导出镜像
Write-Host ""
Write-Host "[5/6] 导出镜像..." -ForegroundColor Yellow

# 删除旧文件
if (Test-Path $TAR_FILE) { Remove-Item $TAR_FILE }
if (Test-Path $COMPRESSED_FILE) { Remove-Item $COMPRESSED_FILE }

docker save -o $TAR_FILE $IMAGE_NAME
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 镜像导出失败" -ForegroundColor Red
    exit 1
}

Write-Host "   压缩文件..." -ForegroundColor Gray
Compress-Archive -Path $TAR_FILE -DestinationPath $COMPRESSED_FILE
Remove-Item $TAR_FILE

$FileSize = (Get-Item $COMPRESSED_FILE).Length / 1MB
Write-Host "✅ 镜像已导出: ${COMPRESSED_FILE} ($([math]::Round($FileSize, 2)) MB)" -ForegroundColor Green

# 6. 传输到服务器
Write-Host ""
Write-Host "[6/6] 传输到服务器..." -ForegroundColor Yellow

# 检查 scp 是否可用
try {
    scp -V 2>&1 | Out-Null
} catch {
    Write-Host "❌ SCP 不可用，请安装 OpenSSH 客户端" -ForegroundColor Red
    Write-Host "   Windows 10/11: 设置 → 应用 → 可选功能 → 添加 OpenSSH 客户端" -ForegroundColor Gray
    exit 1
}

# 创建远程目录
ssh ${ServerUser}@${ServerIP} "mkdir -p ${DeployDir}"

# 传输文件
scp $COMPRESSED_FILE "${ServerUser}@${ServerIP}:${DeployDir}/"
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 文件传输失败" -ForegroundColor Red
    exit 1
}

Write-Host "✅ 文件传输成功" -ForegroundColor Green

# 7. 显示后续步骤
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  传输完成！" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步：SSH 登录服务器执行以下命令：" -ForegroundColor Yellow
Write-Host ""
Write-Host "ssh ${ServerUser}@${ServerIP}" -ForegroundColor Gray
Write-Host "cd ${DeployDir}" -ForegroundColor Gray
Write-Host "gunzip ${COMPRESSED_FILE}" -ForegroundColor Gray
Write-Host "docker load -i wps-password-backend.tar" -ForegroundColor Gray
Write-Host "docker-compose up -d" -ForegroundColor Gray
Write-Host ""
Write-Host "或者运行自动化脚本（需先在服务器创建）：" -ForegroundColor Yellow
Write-Host ""
Write-Host "ssh ${ServerUser}@${ServerIP} '${DeployDir}/import-and-deploy.sh'" -ForegroundColor Gray
Write-Host ""
