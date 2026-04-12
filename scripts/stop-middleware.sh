#!/bin/bash

# AI能力中台 - 中间件停止脚本
# 用法: ./stop-middleware.sh [all|mysql|redis|es|minio]

set -e

RED='\033[31m'
GREEN='\033[32m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }

DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)/docker"

stop_mysql() {
    log_info "停止 MySQL..."
    docker stop ai-platform-mysql 2>/dev/null && docker rm ai-platform-mysql 2>/dev/null || true
}

stop_redis() {
    log_info "停止 Redis..."
    docker stop ai-platform-redis 2>/dev/null && docker rm ai-platform-redis 2>/dev/null || true
}

stop_es() {
    log_info "停止 Elasticsearch..."
    docker stop ai-platform-es 2>/dev/null && docker rm ai-platform-es 2>/dev/null || true
}

stop_minio() {
    log_info "停止 MinIO..."
    docker stop ai-platform-minio 2>/dev/null && docker rm ai-platform-minio 2>/dev/null || true
}

stop_all() {
    log_info ">>> 停止所有中间件..."
    cd "${DOCKER_DIR}"
    docker-compose down
    log_info "=== 所有中间件已停止 ==="
}

echo "======================================"
echo "  AI能力中台 - 中间件停止"
echo "======================================"

case "$1" in
    mysql)       stop_mysql ;;
    redis)       stop_redis ;;
    es|elasticsearch) stop_es ;;
    minio)       stop_minio ;;
    all|"")      stop_all ;;
    *)
        echo "用法: $0 {all|mysql|redis|es|minio}"
        exit 1
        ;;
esac
