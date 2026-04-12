#!/bin/bash

# AI能力中台 - 一键部署脚本
# 用法: ./all-in-one.sh {start|stop|restart|status}

set -e

RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
BLUE='\033[34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }

# 路径配置
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
BACKEND_DIR="${PROJECT_DIR}/ai-platform-backend"
CONTAINER_BASE="/Users/ycy/work/docker-container"
LOG_DIR="${PROJECT_DIR}/logs"
PID_FILE="${PROJECT_DIR}/app.pid"

# JDK 配置
JAVA_HOME="/Users/ycy/work/java/openjdk/jdk-17.0.18+8/Contents/Home"
MAVEN_HOME="/Users/ycy/work/java/apache-maven-3.9.14"

# 中间件健康检查
wait_for_mysql() {
    for i in {1..30}; do
        docker exec ai-platform-mysql mysqladmin ping -h localhost -uroot -proot123 &> /dev/null && return 0
        sleep 2
    done
    return 1
}

wait_for_redis() {
    for i in {1..10}; do
        docker exec ai-platform-redis redis-cli ping &> /dev/null && return 0
        sleep 1
    done
    return 1
}

wait_for_es() {
    for i in {1..30}; do
        curl -sf http://localhost:9200 &> /dev/null && return 0
        sleep 3
    done
    return 1
}

wait_for_minio() {
    for i in {1..10}; do
        docker exec ai-platform-minio mc ready local &> /dev/null && return 0
        sleep 2
    done
    return 1
}

# 一键启动
do_start() {
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}  AI能力中台 - 一键启动${NC}"
    echo -e "${BLUE}======================================${NC}"

    # 1. 启动中间件
    log_info ">>> [1/4] 启动中间件..."
    cd "${SCRIPT_DIR}/docker"
    docker-compose up -d

    log_info "等待中间件就绪..."
    wait_for_mysql && log_info "  MySQL ✓" || log_warn "  MySQL 超时"
    wait_for_redis && log_info "  Redis ✓" || log_warn "  Redis 超时"
    wait_for_es && log_info "  Elasticsearch ✓" || log_warn "  Elasticsearch 超时"
    wait_for_minio && log_info "  MinIO ✓" || log_warn "  MinIO 超时"

    # 2. 打包后端
    log_info ">>> [2/4] 打包后端..."
    mkdir -p "${LOG_DIR}"
    cd "${BACKEND_DIR}"
    export JAVA_HOME="${JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"

    if ! mvn clean package -DskipTests -q; then
        log_error "后端打包失败"
        exit 1
    fi
    log_info "后端打包完成"

    # 3. 停止旧进程
    if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
        log_warn "停止旧进程..."
        kill "$(cat "${PID_FILE}")" 2>/dev/null || true
        rm -f "${PID_FILE}"
        sleep 2
    fi

    # 4. 启动后端
    log_info ">>> [3/4] 启动后端..."
    JAR_FILE="${BACKEND_DIR}/target/ai-platform-1.0.0.jar"
    nohup "${JAVA_HOME}/bin/java" \
        -Xms512m -Xmx1024m \
        -jar "${JAR_FILE}" \
        --spring.profiles.active=dev \
        > "${LOG_DIR}/backend.log" 2>&1 &

    echo $! > "${PID_FILE}"
    log_info "后端已启动，PID: $(cat "${PID_FILE}")"

    # 等待后端就绪
    log_info ">>> [4/4] 等待后端就绪..."
    for i in {1..30}; do
        curl -sf http://localhost:8080/actuator/health &> /dev/null && {
            log_info "后端服务就绪 ✓"
            break
        }
        echo -n "."
        sleep 2
    done

    echo ""
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}  启动完成${NC}"
    echo -e "${BLUE}======================================${NC}"
    echo ""
    echo "【中间件】"
    echo "  MySQL:         localhost:3306"
    echo "  Redis:         localhost:6379"
    echo "  Elasticsearch: localhost:9200"
    echo "  MinIO:         localhost:9000 / 9001"
    echo ""
    echo "【应用】"
    echo "  后端 API:      http://localhost:8080"
    echo ""
    echo "【日志】"
    echo "  ${LOG_DIR}/backend.log"
    echo ""
}

# 一键停止
do_stop() {
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}  AI能力中台 - 一键停止${NC}"
    echo -e "${BLUE}======================================${NC}"

    # 停止后端
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}")
        if kill -0 "${PID}" 2>/dev/null; then
            log_info "停止后端，PID: ${PID}"
            kill "${PID}" 2>/dev/null || true
            rm -f "${PID_FILE}"
        fi
    fi

    # 停止残留
    pkill -f "ai-platform-1.0.0.jar" 2>/dev/null || true

    # 停止中间件
    log_info "停止中间件..."
    cd "${SCRIPT_DIR}/docker"
    docker-compose down

    log_info "=== 全部停止 ==="
}

# 重启
do_restart() {
    do_stop
    sleep 2
    do_start
}

# 状态
do_status() {
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}  AI能力中台 - 状态检查${NC}"
    echo -e "${BLUE}======================================${NC}"
    echo ""

    echo "【中间件容器】"
    docker ps --filter "name=ai-platform-" --format "  {{.Names}}: {{.Status}}" 2>/dev/null || echo "  无运行中的容器"

    echo ""
    echo "【后端进程】"
    if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
        echo -e "  ${GREEN}运行中${NC}，PID: $(cat "${PID_FILE}")"
    else
        echo -e "  ${RED}未运行${NC}"
    fi

    echo ""
    echo "【端口占用】"
    if lsof -ti:8080 &> /dev/null; then
        echo -e "  8080: ${GREEN}已占用${NC}"
    else
        echo -e "  8080: ${RED}空闲${NC}"
    fi
}

# 主逻辑
case "$1" in
    start)   do_start ;;
    stop)    do_stop ;;
    restart) do_restart ;;
    status)  do_status ;;
    *)
        echo ""
        echo -e "${BLUE}AI能力中台 - 部署脚本${NC}"
        echo ""
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "  start   - 一键启动（中间件 + 后端）"
        echo "  stop    - 一键停止（后端 + 中间件）"
        echo "  restart - 重启所有服务"
        echo "  status  - 查看服务状态"
        echo ""
        exit 1
        ;;
esac
