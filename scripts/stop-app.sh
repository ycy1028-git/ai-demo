#!/bin/bash

# AI能力中台 - 后端停止脚本（仅停止后端，不管理中间件）
# 用法: ./stop-app.sh

set -e

RED='\033[31m'
GREEN='\033[32m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${PROJECT_DIR}/app.pid"

echo "======================================"
echo "  AI能力中台 - 后端停止"
echo "======================================"

# 停止后端进程
if [ -f "${PID_FILE}" ]; then
    PID=$(cat "${PID_FILE}")
    if kill -0 "${PID}" 2>/dev/null; then
        log_info "停止后端，PID: ${PID}"
        kill "${PID}" 2>/dev/null || true
        rm -f "${PID_FILE}"
    else
        log_info "后端未运行"
        rm -f "${PID_FILE}"
    fi
else
    log_info "后端未运行"
fi

# 停止端口占用进程
RUNNING_PID=$(lsof -ti:8080 2>/dev/null || true)
if [ -n "${RUNNING_PID}" ]; then
    log_info "停止端口 8080 上的进程: ${RUNNING_PID}"
    kill "${RUNNING_PID}" 2>/dev/null || true
fi

log_info "后端已停止"
