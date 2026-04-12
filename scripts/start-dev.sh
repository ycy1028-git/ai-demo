#!/bin/bash

# AI能力中台 - 后端启动脚本（仅启动后端，不管理中间件）
# 用法: ./start-dev.sh

set -e

RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }

# 路径配置
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="${PROJECT_DIR}/ai-platform-backend"
LOG_DIR="${PROJECT_DIR}/logs"
PID_FILE="${PROJECT_DIR}/app.pid"

# JDK 配置
JAVA_HOME="/Users/ycy/work/java/openjdk/jdk-17.0.18+8/Contents/Home"
MAVEN_HOME="/Users/ycy/work/java/apache-maven-3.9.14"

mkdir -p "${LOG_DIR}"

echo "======================================"
echo "  AI能力中台 - 后端启动"
echo "======================================"

# 检查环境
log_info "检查环境..."
if [ ! -f "${JAVA_HOME}/bin/java" ]; then
    log_error "JDK 未找到: ${JAVA_HOME}"
    exit 1
fi

if [ ! -f "${MAVEN_HOME}/bin/mvn" ]; then
    log_error "Maven 未找到: ${MAVEN_HOME}"
    exit 1
fi

export JAVA_HOME="${JAVA_HOME}"
export PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"

# 检查中间件
log_info "检查中间件..."
for svc in mysql redis; do
    container="ai-platform-${svc}"
    if ! docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        log_warn "${svc} 未运行，请先执行: ./scripts/start-middleware.sh ${svc}"
    fi
done

# 停止已存在的进程
if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    log_warn "后端已在运行，PID: $(cat "${PID_FILE}")"
    log_warn "正在重启..."
    kill "$(cat "${PID_FILE}")" 2>/dev/null || true
    rm -f "${PID_FILE}"
    sleep 2
fi

# 检查残留 Java 进程
RUNNING_PID=$(lsof -ti:8080 2>/dev/null || true)
if [ -n "${RUNNING_PID}" ]; then
    log_warn "端口 8080 被占用，PID: ${RUNNING_PID}"
    log_warn "正在终止..."
    kill "${RUNNING_PID}" 2>/dev/null || true
    sleep 2
fi

# 打包后端
log_info ">>> 打包后端..."
cd "${BACKEND_DIR}"
if ! mvn clean package -DskipTests -q; then
    log_error "后端打包失败"
    exit 1
fi

JAR_FILE="${BACKEND_DIR}/target/ai-platform-1.0.0.jar"
if [ ! -f "${JAR_FILE}" ]; then
    log_error "JAR 文件未生成"
    exit 1
fi
log_info "打包完成"

# 启动后端
log_info ">>> 启动后端..."
nohup "${JAVA_HOME}/bin/java" \
    -Xms512m -Xmx1024m \
    -jar "${JAR_FILE}" \
    --spring.profiles.active=dev \
    > "${LOG_DIR}/backend.log" 2>&1 &

echo $! > "${PID_FILE}"
log_info "后端已启动，PID: $(cat "${PID_FILE}")"

# 等待后端就绪
log_info "等待后端服务就绪..."
for i in {1..30}; do
    if curl -sf http://localhost:8080/actuator/health &> /dev/null; then
        log_info "后端服务已就绪"
        break
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "======================================"
echo "  后端启动完成"
echo "======================================"
echo ""
echo "  后端 API: http://localhost:8080"
echo "  日志文件: ${LOG_DIR}/backend.log"
echo "  PID 文件: ${PID_FILE}"
echo ""
echo "======================================"
echo "  停止命令: ./scripts/stop-app.sh"
echo "======================================"
