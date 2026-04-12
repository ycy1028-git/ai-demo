#!/bin/bash

# AI能力中台 - 中间件启动脚本
# 用法: ./start-middleware.sh [all|mysql|redis|es|minio]

set -e

# 颜色定义
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }

# 固定镜像版本
readonly MYSQL_IMAGE="mysql:8.0"
readonly REDIS_IMAGE="redis:7-alpine"
readonly ELASTICSEARCH_IMAGE="elasticsearch:8.11.0"
readonly MINIO_IMAGE="minio/minio:RELEASE.2023-09-04T19-19-48Z"

# 路径配置
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/docker"
CONTAINER_BASE="/Users/ycy/work/docker-container"

# 初始化存储目录
init_storage() {
    log_info "初始化存储目录..."
    mkdir -p "${CONTAINER_BASE}"/{mysql,redis,elasticsearch,minio}/{data,conf,logs}
}

# 健康检查函数
wait_for_mysql() {
    log_info "等待 MySQL 就绪..."
    for i in {1..30}; do
        if docker exec ai-platform-mysql mysqladmin ping -h localhost -uroot -proot123 &> /dev/null; then
            log_info "MySQL 已就绪"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    log_error "MySQL 启动超时"
    return 1
}

wait_for_redis() {
    log_info "等待 Redis 就绪..."
    for i in {1..10}; do
        if docker exec ai-platform-redis redis-cli ping &> /dev/null; then
            log_info "Redis 已就绪"
            return 0
        fi
        sleep 1
    done
    log_error "Redis 启动超时"
    return 1
}

wait_for_es() {
    log_info "等待 Elasticsearch 就绪..."
    for i in {1..30}; do
        if curl -sf http://localhost:9200 &> /dev/null; then
            log_info "Elasticsearch 已就绪"
            return 0
        fi
        echo -n "."
        sleep 3
    done
    log_error "Elasticsearch 启动超时"
    return 1
}

wait_for_minio() {
    log_info "等待 MinIO 就绪..."
    for i in {1..10}; do
        if docker exec ai-platform-minio mc ready local &> /dev/null; then
            docker exec ai-platform-minio mc alias set local http://localhost:9000 minioadmin minioadmin123 2>/dev/null || true
            docker exec ai-platform-minio mc mb local/ai-platform --ignore-existing 2>/dev/null || true
            docker exec ai-platform-minio mc anonymous set download local/ai-platform 2>/dev/null || true
            log_info "MinIO 已就绪，Bucket 已初始化"
            return 0
        fi
        sleep 2
    done
    log_error "MinIO 启动超时"
    return 1
}

# 启动所有
start_all() {
    log_info ">>> 启动所有中间件..."
    init_storage

    cd "${DOCKER_DIR}"
    docker-compose up -d

    wait_for_mysql
    wait_for_redis
    wait_for_es
    wait_for_minio

    log_info "=== 所有中间件启动完成 ==="
}

# 启动单个
start_mysql() {
    init_storage
    cd "${DOCKER_DIR}"
    docker-compose up -d mysql
    wait_for_mysql
}

start_redis() {
    init_storage
    cd "${DOCKER_DIR}"
    docker-compose up -d redis
    wait_for_redis
}

start_es() {
    init_storage
    cd "${DOCKER_DIR}"
    docker-compose up -d elasticsearch
    wait_for_es
}

start_minio() {
    init_storage
    cd "${DOCKER_DIR}"
    docker-compose up -d minio
    wait_for_minio
}

echo "======================================"
echo "  AI能力中台 - 中间件启动"
echo "======================================"

# 检查 Docker
if ! docker info &> /dev/null; then
    log_error "Docker 未运行，请先启动 Docker Desktop"
    exit 1
fi

case "$1" in
    mysql)       start_mysql ;;
    redis)       start_redis ;;
    es)          start_es ;;
    elasticsearch) start_es ;;
    minio)       start_minio ;;
    all|"")      start_all ;;
    *)
        echo "用法: $0 {all|mysql|redis|es|minio}"
        exit 1
        ;;
esac

echo ""
echo "【服务地址】"
echo "  MySQL:         localhost:3306"
echo "  Redis:         localhost:6379"
echo "  Elasticsearch: localhost:9200"
echo "  MinIO:         localhost:9000"
echo "  MinIO Console: localhost:9001"
echo ""
echo "【存储位置】"
echo "  ${CONTAINER_BASE}"
echo ""
echo "【版本信息】"
echo "  MySQL:         ${MYSQL_IMAGE}"
echo "  Redis:         ${REDIS_IMAGE}"
echo "  Elasticsearch: ${ELASTICSEARCH_IMAGE}"
echo "  MinIO:         ${MINIO_IMAGE}"
