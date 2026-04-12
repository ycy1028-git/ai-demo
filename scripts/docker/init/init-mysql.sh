#!/bin/bash

# ================================================
# AI能力中台 - MySQL 数据库初始化脚本
# 说明: 创建数据库和导入初始数据
# ================================================

set -e

# 配置
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root123}"
DB_NAME="${DB_NAME:-ai_platform}"

# 颜色定义
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }
log_ok()    { echo -e "${GREEN}[  OK ]${NC} $(date '+%H:%M:%S') $1"; }

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# MySQL 命令
MYSQL_CMD="mysql -h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USER}"
if [ -n "${MYSQL_PASS}" ]; then
    MYSQL_CMD="${MYSQL_CMD} -p${MYSQL_PASS}"
fi

# ================================================
# 检查 MySQL 连接
# ================================================
check_mysql() {
    log_info "检查 MySQL 连接..."

    for i in {1..30}; do
        if ${MYSQL_CMD} -e "SELECT 1" > /dev/null 2>&1; then
            log_ok "MySQL 连接成功"
            return 0
        fi
        echo -n "."
        sleep 2
    done

    log_error "MySQL 连接超时"
    return 1
}

# ================================================
# 创建数据库
# ================================================
create_database() {
    log_info "创建数据库: ${DB_NAME}"

    ${MYSQL_CMD} -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

    if [ $? -eq 0 ]; then
        log_ok "数据库 ${DB_NAME} 创建成功"
    else
        log_error "数据库 ${DB_NAME} 创建失败"
        return 1
    fi
}

# ================================================
# 执行 SQL 脚本
# ================================================
execute_sql() {
    local sql_file=$1
    local description=$2

    if [ ! -f "${sql_file}" ]; then
        log_error "SQL 文件不存在: ${sql_file}"
        return 1
    fi

    log_info "执行: ${description}"
    ${MYSQL_CMD} "${DB_NAME}" < "${sql_file}"

    if [ $? -eq 0 ]; then
        log_ok "${description} 完成"
    else
        log_error "${description} 失败"
        return 1
    fi
}

# ================================================
# 检查数据库表是否存在
# ================================================
check_tables() {
    local table_count=$(${MYSQL_CMD} -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';" 2>/dev/null)
    echo "${table_count:-0}"
}

# ================================================
# 初始化所有数据
# ================================================
init_all() {
    log_info ">>> 开始初始化数据库..."

    check_mysql
    create_database

    # 检查是否已有数据
    local table_count=$(check_tables)
    if [ "${table_count}" -gt 0 ]; then
        log_warn "数据库已存在 ${table_count} 张表"
        read -p "是否跳过数据初始化? (yes/no) [no]: " skip_init
        if [ "${skip_init}" = "yes" ]; then
            log_info "跳过数据初始化"
            return 0
        fi
    fi

    # 执行初始化脚本
    execute_sql "${SCRIPT_DIR}/init-data.sql" "初始化基础数据"

    log_info "=== 数据库初始化完成 ==="
}

# ================================================
# 验证数据
# ================================================
verify_data() {
    log_info ">>> 验证数据..."

    echo ""
    echo -e "${YELLOW}--- 系统用户表 (t_sys_user) ---${NC}"
    ${MYSQL_CMD} -e "SELECT f_id, f_username, f_real_name, f_email, f_status FROM ${DB_NAME}.t_sys_user;" 2>/dev/null || log_warn "用户表为空或不存在"

    echo ""
    echo -e "${YELLOW}--- API凭证表 (t_sys_api_credential) ---${NC}"
    ${MYSQL_CMD} -e "SELECT f_id, f_name, f_credential_key, f_status FROM ${DB_NAME}.t_sys_api_credential;" 2>/dev/null || log_warn "API凭证表为空或不存在"

    echo ""
    echo -e "${YELLOW}--- AI模型配置表 (t_ai_model_config) ---${NC}"
    ${MYSQL_CMD} -e "SELECT f_id, f_name, f_provider, f_is_default, f_status FROM ${DB_NAME}.t_ai_model_config;" 2>/dev/null || log_warn "AI模型配置表为空或不存在"

    echo ""
    echo -e "${YELLOW}--- 知识库表 (t_kb_knowledge_base) ---${NC}"
    ${MYSQL_CMD} -e "SELECT f_id, f_name, f_code, f_es_index, f_status FROM ${DB_NAME}.t_kb_knowledge_base;" 2>/dev/null || log_warn "知识库表为空或不存在"

    echo ""
    echo -e "${YELLOW}--- 知识条目表 (t_kb_knowledge_item) ---${NC}"
    ${MYSQL_CMD} -e "SELECT f_id, f_kb_id, f_title, f_status FROM ${DB_NAME}.t_kb_knowledge_item LIMIT 5;" 2>/dev/null || log_warn "知识条目表为空或不存在"

    echo ""
    echo -e "${YELLOW}--- 文档表 (t_kb_document) ---${NC}"
    ${MYSQL_CMD} -e "SELECT COUNT(*) as total FROM ${DB_NAME}.t_kb_document;" 2>/dev/null || log_warn "文档表为空或不存在"
}

# ================================================
# 重置数据库
# ================================================
reset_database() {
    log_warn ">>> 重置数据库..."

    read -p "确认删除所有数据并重新初始化? (输入 'yes' 确认): " confirm
    if [ "${confirm}" != "yes" ]; then
        log_info "取消操作"
        return 0
    fi

    log_info "删除数据库..."
    ${MYSQL_CMD} -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`;"

    init_all
    verify_data

    log_ok "数据库重置完成"
}

# ================================================
# 主函数
# ================================================
echo "======================================"
echo "  AI能力中台 - MySQL初始化"
echo "======================================"

case "$1" in
    init)
        init_all
        ;;
    verify)
        check_mysql
        verify_data
        ;;
    reset)
        check_mysql
        reset_database
        ;;
    *)
        echo "用法: $0 {init|verify|reset}"
        echo ""
        echo "  init    - 初始化数据库（创建数据库 + 导入数据）"
        echo "  verify  - 验证数据完整性"
        echo "  reset   - 重置数据库（删除所有数据后重新初始化）"
        ;;
esac
