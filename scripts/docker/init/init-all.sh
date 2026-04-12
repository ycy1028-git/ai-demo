#!/bin/bash

# ================================================
# AI能力中台 - 一键初始化脚本
# 说明: 初始化所有中间件数据（MySQL + ES + MinIO）
# ================================================

set -e

# 颜色定义
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
BLUE='\033[34m'
CYAN='\033[36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $(date '+%H:%M:%S') $1"; }
log_ok()    { echo -e "${GREEN}[  OK ]${NC} $(date '+%H:%M:%S') $1"; }

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ================================================
# 头部信息
# ================================================
print_header() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║      AI能力中台 - 初始化向导 v2.0           ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
    echo ""
}

# ================================================
# 环境检查
# ================================================
check_environment() {
    log_step ">>> 环境检查..."

    # 检查 Docker
    if ! docker info &> /dev/null; then
        log_error "Docker 未运行，请先启动 Docker Desktop"
        exit 1
    fi
    log_ok "Docker 运行正常"

    # 检查中间件容器状态
    local all_running=true

    # 检查各个容器（使用精确匹配）
    check_container() {
        local svc=$1
        local container=$2
        if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
            echo "  ${svc}: $(docker ps --filter "name=${container}" --format '{{.Status}}' | cut -d' ' -f1)"
            return 0
        else
            echo "  ${svc}: ${RED}未运行${NC}"
            return 1
        fi
    }

    check_container "mysql" "ai-platform-mysql"
    check_container "redis" "ai-platform-redis"
    check_container "elasticsearch" "ai-platform-es"
    check_container "minio" "ai-platform-minio"

    if [ "$all_running" = false ]; then
        log_warn "部分中间件未运行，请先执行 ../start-middleware.sh"
        exit 1
    fi

    echo ""
}

# ================================================
# 选择初始化模式
# ================================================
select_mode() {
    local mode="all"

    if [ -n "$1" ]; then
        mode="$1"
    else
        echo "请选择初始化模式:"
        echo ""
        echo "  ${GREEN}1${NC}) 完整初始化     (MySQL + ES + MinIO)"
        echo "  ${GREEN}2${NC}) 仅初始化 MySQL"
        echo "  ${GREEN}3${NC}) 仅初始化 Elasticsearch"
        echo "  ${GREEN}4${NC}) 仅初始化 MinIO"
        echo "  ${GREEN}5${NC}) 仅验证数据"
        echo "  ${GREEN}6${NC}) 重置所有数据"
        echo ""
        read -p "请输入选项 [1]: " choice
        case "${choice}" in
            2) mode="mysql" ;;
            3) mode="elasticsearch" ;;
            4) mode="minio" ;;
            5) mode="verify" ;;
            6) mode="reset" ;;
            *) mode="all" ;;
        esac
    fi

    echo "${mode}"
}

# ================================================
# 完整初始化
# ================================================
init_all() {
    log_step ">>> 步骤1: 初始化 MySQL..."
    bash "${SCRIPT_DIR}/init-mysql.sh" init

    echo ""
    log_step ">>> 步骤2: 初始化 Elasticsearch..."
    bash "${SCRIPT_DIR}/init-elasticsearch.sh" create

    echo ""
    log_step ">>> 步骤3: 初始化 MinIO..."
    bash "${SCRIPT_DIR}/init-minio.sh" init

    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║            初始化完成！                          ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}【默认账号】${NC}"
    echo "  用户名: ${GREEN}admin${NC}"
    echo "  密码:   ${GREEN}admin123${NC}"
    echo ""
    echo -e "${YELLOW}【API凭证】${NC}"
    echo "  Key:    ${GREEN}ak_admin_001${NC}"
    echo "  Secret: ${GREEN}sk_admin_secret_001_abcdef1234567890${NC}"
    echo ""
    echo -e "${YELLOW}【服务地址】${NC}"
    echo "  后台管理:     ${GREEN}http://localhost:8080${NC}"
    echo "  MinIO控制台:  ${GREEN}http://localhost:9001${NC}"
    echo "  Elasticsearch: ${GREEN}http://localhost:9200${NC}"
    echo ""
}

# ================================================
# 重置所有数据
# ================================================
reset_all() {
    echo ""
    log_warn ">>> 警告：即将重置所有数据！"
    echo ""
    echo "这将执行以下操作:"
    echo "  1. 删除 MySQL 数据库"
    echo "  2. 删除 ES 所有索引"
    echo "  3. 删除 MinIO 所有存储桶"
    echo ""
    read -p "确认继续? (输入 'yes' 确认): " confirm

    if [ "${confirm}" != "yes" ]; then
        log_info "操作已取消"
        exit 0
    fi

    echo ""
    log_step ">>> 步骤1: 重置 MySQL..."
    bash "${SCRIPT_DIR}/init-mysql.sh" reset

    echo ""
    log_step ">>> 步骤2: 重置 Elasticsearch..."
    bash "${SCRIPT_DIR}/init-elasticsearch.sh" delete
    bash "${SCRIPT_DIR}/init-elasticsearch.sh" create

    echo ""
    log_step ">>> 步骤3: 重置 MinIO..."
    bash "${SCRIPT_DIR}/init-minio.sh" reset

    echo ""
    log_ok "所有数据已重置完成"
}

# ================================================
# 验证模式
# ================================================
verify_all() {
    log_step ">>> 验证 MySQL..."
    bash "${SCRIPT_DIR}/init-mysql.sh" verify

    echo ""
    log_step ">>> 验证 Elasticsearch..."
    bash "${SCRIPT_DIR}/init-elasticsearch.sh" list

    echo ""
    log_step ">>> 验证 MinIO..."
    bash "${SCRIPT_DIR}/init-minio.sh" buckets
}

# ================================================
# 主函数
# ================================================
main() {
    print_header
    check_environment

    local init_mode=$(select_mode "$1")

    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  初始化模式: ${init_mode}${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""

    case "${init_mode}" in
        all)
            init_all
            ;;
        mysql)
            bash "${SCRIPT_DIR}/init-mysql.sh" init
            bash "${SCRIPT_DIR}/init-mysql.sh" verify
            ;;
        elasticsearch|es)
            bash "${SCRIPT_DIR}/init-elasticsearch.sh" create
            bash "${SCRIPT_DIR}/init-elasticsearch.sh" list
            ;;
        minio)
            bash "${SCRIPT_DIR}/init-minio.sh" init
            bash "${SCRIPT_DIR}/init-minio.sh" buckets
            ;;
        verify)
            verify_all
            ;;
        reset)
            reset_all
            ;;
        *)
            echo "用法: $0 {all|mysql|elasticsearch|minio|verify|reset}"
            echo ""
            echo "  all          - 完整初始化（默认）"
            echo "  mysql        - 仅初始化 MySQL"
            echo "  elasticsearch - 仅初始化 ES"
            echo "  minio        - 仅初始化 MinIO"
            echo "  verify       - 验证现有数据"
            echo "  reset        - 重置所有数据"
            exit 1
            ;;
    esac
}

main "$@"
