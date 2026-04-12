#!/bin/bash

# ================================================
# AI能力中台 - Elasticsearch 索引初始化脚本
# 说明: 创建知识库所需的 ES 索引
# ================================================

set -e

ES_HOST="${ES_HOST:-localhost:9200}"
ES_USER="${ES_USER:-}"
ES_PASS="${ES_PASS:-}"

# 颜色定义
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $(date '+%H:%M:%S') $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $1"; }
log_ok()    { echo -e "${GREEN}[  OK ]${NC} $(date '+%H:%M:%S') $1"; }

# ES 请求头
if [ -n "${ES_USER}" ] && [ -n "${ES_PASS}" ]; then
    AUTH="-u ${ES_USER}:${ES_PASS}"
else
    AUTH=""
fi

# ================================================
# 检查 ES 健康状态
# ================================================
check_es_health() {
    log_info "检查 Elasticsearch 状态..."

    for i in {1..30}; do
        if curl -sf ${AUTH} "http://${ES_HOST}/_cluster/health" > /dev/null 2>&1; then
            local health=$(curl -s ${AUTH} "http://${ES_HOST}/_cluster/health" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            log_ok "ES 健康状态: ${health}"
            return 0
        fi
        echo -n "."
        sleep 2
    done

    log_error "ES 连接超时"
    return 1
}

# ================================================
# 创建知识库向量索引
# ================================================
create_kb_index() {
    local index_name=$1
    log_info "创建索引: ${index_name}"

    # 检查索引是否存在
    if curl -sf ${AUTH} "http://${ES_HOST}/${index_name}" > /dev/null 2>&1; then
        log_warn "索引 ${index_name} 已存在，跳过创建"
        return 0
    fi

    # 创建索引（使用标准分词器）
    curl -X PUT ${AUTH} "http://${ES_HOST}/${index_name}" -H 'Content-Type: application/json' -d '
    {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
          "analyzer": {
            "default": {
              "type": "standard"
            }
          }
        }
      },
      "mappings": {
        "properties": {
          "f_id": { "type": "keyword" },
          "f_kb_id": { "type": "keyword" },
          "f_kb_code": { "type": "keyword" },
          "f_item_id": { "type": "keyword" },
          "f_title": {
            "type": "text",
            "analyzer": "standard"
          },
          "f_content": {
            "type": "text",
            "analyzer": "standard"
          },
          "f_summary": {
            "type": "text",
            "analyzer": "standard"
          },
          "f_tags": { "type": "keyword" },
          "f_chunk_index": { "type": "integer" },
          "f_total_chunks": { "type": "integer" },
          "f_source_type": { "type": "keyword" },
          "f_file_type": { "type": "keyword" },
          "f_vector": {
            "type": "dense_vector",
            "dims": 768,
            "index": true,
            "similarity": "cosine"
          },
          "f_create_time": { "type": "date" },
          "f_update_time": { "type": "date" }
        }
      }
    }'

    if [ $? -eq 0 ]; then
        log_ok "索引 ${index_name} 创建成功"
    else
        log_error "索引 ${index_name} 创建失败"
        return 1
    fi
}

# ================================================
# 创建所有知识库索引
# ================================================
create_all_indexes() {
    log_info ">>> 创建所有知识库索引..."

    local indexes=(
        "kb_kb_customer"
        "kb_kb_search"
        "kb_kb_hr"
        "kb_kb_finance"
        "kb_kb_training"
        "kb_kb_customer_source"
    )

    for kb in "${indexes[@]}"; do
        create_kb_index "${kb}"
    done

    log_ok "=== 所有索引创建完成 ==="
}

# ================================================
# 删除索引
# ================================================
delete_index() {
    local index_name=$1

    # 检查索引是否存在
    if ! curl -sf ${AUTH} "http://${ES_HOST}/${index_name}" > /dev/null 2>&1; then
        log_warn "索引 ${index_name} 不存在，跳过"
        return 0
    fi

    log_info "删除索引: ${index_name}"
    curl -X DELETE ${AUTH} "http://${ES_HOST}/${index_name}" > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        log_ok "索引 ${index_name} 删除成功"
    else
        log_error "索引 ${index_name} 删除失败"
    fi
}

# ================================================
# 删除所有知识库索引
# ================================================
delete_all_indexes() {
    log_warn ">>> 删除所有知识库索引..."

    local indexes=(
        "kb_kb_customer"
        "kb_kb_search"
        "kb_kb_hr"
        "kb_kb_finance"
        "kb_kb_training"
        "kb_kb_customer_source"
    )

    for kb in "${indexes[@]}"; do
        delete_index "${kb}"
    done

    log_ok "=== 所有索引删除完成 ==="
}

# ================================================
# 列出所有索引
# ================================================
list_indexes() {
    log_info "当前索引列表:"
    curl -s ${AUTH} "http://${ES_HOST}/_cat/indices/kb_*?v" || log_warn "未找到知识库索引"
}

# ================================================
# 获取索引详情
# ================================================
get_index_info() {
    local index_name=$1
    log_info "索引 ${index_name} 详情:"
    curl -s ${AUTH} "http://${ES_HOST}/${index_name}/_mapping?pretty"
}

# ================================================
# 主函数
# ================================================
echo "======================================"
echo "  AI能力中台 - ES索引管理"
echo "======================================"

case "$1" in
    create)
        check_es_health
        create_all_indexes
        ;;
    delete)
        delete_all_indexes
        ;;
    list)
        list_indexes
        ;;
    health)
        check_es_health
        ;;
    info)
        if [ -n "$2" ]; then
            get_index_info "$2"
        else
            echo "用法: $0 info <index_name>"
        fi
        ;;
    *)
        echo "用法: $0 {create|delete|list|health|info}"
        echo ""
        echo "  create  - 创建所有知识库索引"
        echo "  delete  - 删除所有知识库索引"
        echo "  list    - 列出所有知识库索引"
        echo "  health  - 检查 ES 健康状态"
        echo "  info    - 查看指定索引详情"
        ;;
esac
