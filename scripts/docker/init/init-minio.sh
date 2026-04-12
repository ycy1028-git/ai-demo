#!/bin/bash

# ================================================
# AI能力中台 - MinIO 初始化脚本
# 说明: 创建存储桶和配置访问策略
# ================================================

set -e

# 配置
MINIO_HOST="${MINIO_HOST:-localhost:9000}"
MINIO_USER="${MINIO_USER:-minioadmin}"
MINIO_PASS="${MINIO_PASS:-minioadmin123}"

# 颜色定义
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }
log_ok()    { echo -e "${GREEN}[  OK ]${NC} $(date '+%H:%M:%S') $1"; }

# MinIO Client 配置
export MC_HOSTS_local="http://${MINIO_HOST}"

# ================================================
# 检查 MinIO 连接
# ================================================
check_minio() {
    log_info "检查 MinIO 连接..."

    for i in {1..30}; do
        if docker exec ai-platform-minio mc ready local > /dev/null 2>&1; then
            log_ok "MinIO 连接成功"
            return 0
        fi
        echo -n "."
        sleep 2
    done

    log_error "MinIO 连接超时"
    return 1
}

# ================================================
# 配置 MinIO Client
# ================================================
config_minio() {
    log_info "配置 MinIO Client..."

    docker exec ai-platform-minio mc alias set local "http://${MINIO_HOST}" "${MINIO_USER}" "${MINIO_PASS}" 2>/dev/null || true
}

# ================================================
# 创建存储桶
# ================================================
create_bucket() {
    local bucket_name=$1
    local policy=${2:-private}

    # 检查存储桶是否存在
    if docker exec ai-platform-minio mc ls "local/${bucket_name}" &> /dev/null; then
        log_warn "存储桶 ${bucket_name} 已存在，跳过创建"
        return 0
    fi

    log_info "创建存储桶: ${bucket_name}"

    # 创建存储桶
    docker exec ai-platform-minio mc mb "local/${bucket_name}" 2>/dev/null || true

    # 设置访问策略
    case "${policy}" in
        public)
            docker exec ai-platform-minio mc anonymous set download "local/${bucket_name}"
            ;;
        private)
            docker exec ai-platform-minio mc anonymous set none "local/${bucket_name}"
            ;;
    esac

    log_ok "存储桶 ${bucket_name} 创建完成 (${policy})"
}

# ================================================
# 创建所有存储桶
# ================================================
create_all_buckets() {
    log_info ">>> 创建所有存储桶..."

    # 知识库文档存储
    create_bucket "ai-platform-docs" "private"

    # 临时文件存储
    create_bucket "ai-platform-temp" "private"

    # 导出文件存储
    create_bucket "ai-platform-export" "public"

    # 头像存储
    create_bucket "ai-platform-avatars" "public"

    # 日志存储
    create_bucket "ai-platform-logs" "private"

    log_ok "=== 所有存储桶创建完成 ==="
}

# ================================================
# 设置生命周期策略
# ================================================
set_lifecycle() {
    local bucket_name=$1
    local days=${2:-30}

    log_info "设置生命周期策略: ${bucket_name} (保留 ${days} 天)"

    cat > /tmp/lifecycle-${bucket_name}.json << EOF
{
  "Rules": [
    {
      "ID": "cleanup-${bucket_name}",
      "Status": "Enabled",
      "Filter": {
        "Prefix": ""
      },
      "Expiration": {
        "Days": ${days}
      }
    }
  ]
}
EOF

    docker exec ai-platform-minio mc ilm import "local/${bucket_name}" < /tmp/lifecycle-${bucket_name}.json 2>/dev/null || true
    rm -f /tmp/lifecycle-${bucket_name}.json
}

# ================================================
# 配置 CORS
# ================================================
set_cors() {
    log_info "配置 CORS..."

    cat > /tmp/cors.json << 'EOF'
{
  "CORSRules": [
    {
      "AllowedOrigins": ["*"],
      "AllowedMethods": ["GET", "PUT", "POST", "DELETE", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag", "Content-Length"],
      "MaxAgeSeconds": 3600
    }
  ]
}
EOF

    docker exec ai-platform-minio mc cors set "local/ai-platform-docs" /tmp/cors.json 2>/dev/null || true
    docker exec ai-platform-minio mc cors set "local/ai-platform-avatars" /tmp/cors.json 2>/dev/null || true
    docker exec ai-platform-minio mc cors set "local/ai-platform-export" /tmp/cors.json 2>/dev/null || true

    rm -f /tmp/cors.json
    log_ok "CORS 配置完成"
}

# ================================================
# 删除存储桶
# ================================================
delete_bucket() {
    local bucket_name=$1

    # 检查存储桶是否存在
    if ! docker exec ai-platform-minio mc ls "local/${bucket_name}" &> /dev/null; then
        log_warn "存储桶 ${bucket_name} 不存在，跳过"
        return 0
    fi

    log_warn "删除存储桶: ${bucket_name}"

    # 清空存储桶内容
    docker exec ai-platform-minio mc rm -r --force "local/${bucket_name}" 2>/dev/null || true

    # 删除存储桶
    docker exec ai-platform-minio mc rb "local/${bucket_name}" 2>/dev/null || true

    log_ok "存储桶 ${bucket_name} 删除完成"
}

# ================================================
# 删除所有存储桶
# ================================================
delete_all_buckets() {
    log_warn ">>> 删除所有存储桶..."

    local buckets=(
        "ai-platform-docs"
        "ai-platform-temp"
        "ai-platform-export"
        "ai-platform-avatars"
        "ai-platform-logs"
    )

    for bucket in "${buckets[@]}"; do
        delete_bucket "${bucket}"
    done

    log_ok "=== 所有存储桶删除完成 ==="
}

# ================================================
# 列出存储桶
# ================================================
list_buckets() {
    log_info "当前存储桶列表:"
    docker exec ai-platform-minio mc ls local/ || log_warn "未找到存储桶"
}

# ================================================
# 列出存储桶使用情况
# ================================================
bucket_usage() {
    log_info "存储桶使用情况:"
    docker exec ai-platform-minio mc du -r local/ || log_warn "无法获取使用情况"
}

# ================================================
# 初始化所有
# ================================================
init_all() {
    check_minio
    config_minio
    create_all_buckets
    set_cors
    list_buckets

    log_ok "=== MinIO 初始化完成 ==="
}

# ================================================
# 重置 MinIO
# ================================================
reset_minio() {
    log_warn ">>> 重置 MinIO..."

    read -p "确认删除所有存储桶? (输入 'yes' 确认): " confirm
    if [ "${confirm}" != "yes" ]; then
        log_info "取消操作"
        return 0
    fi

    delete_all_buckets
    init_all

    log_ok "MinIO 重置完成"
}

# ================================================
# 主函数
# ================================================
echo "======================================"
echo "  AI能力中台 - MinIO初始化"
echo "======================================"

case "$1" in
    init)
        init_all
        ;;
    buckets)
        check_minio
        config_minio
        list_buckets
        ;;
    usage)
        check_minio
        config_minio
        bucket_usage
        ;;
    reset)
        check_minio
        config_minio
        reset_minio
        ;;
    *)
        echo "用法: $0 {init|buckets|usage|reset}"
        echo ""
        echo "  init    - 初始化存储桶（创建存储桶 + 配置策略）"
        echo "  buckets - 列出所有存储桶"
        echo "  usage   - 查看存储桶使用情况"
        echo "  reset   - 删除所有存储桶并重新创建"
        ;;
esac
