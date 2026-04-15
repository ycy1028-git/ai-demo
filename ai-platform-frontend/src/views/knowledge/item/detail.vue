<template>
  <div class="knowledge-item-detail-container" v-loading="loading">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="left">
            <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
            <span class="title">知识详情</span>
          </div>
          <div class="right" v-if="detail.id">
            <el-button type="primary" plain @click="handleEdit">编辑</el-button>
            <el-button v-if="hasAttachment" type="primary" plain @click="handlePreview">文档预览</el-button>
            <el-button v-if="hasAttachment" @click="handleDownload">下载文档</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题">{{ detail.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="知识库">{{ baseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">
          <el-tag size="small" :type="detail.sourceType === 'document' ? 'primary' : 'info'">
            {{ detail.sourceType === 'document' ? '文档' : '手动' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="detail.status === 1 ? 'success' : 'info'">
            {{ detail.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="向量化状态">
          <el-tag size="small" :type="vectorStatusType(detail.vectorStatus)">
            {{ vectorStatusText(detail.vectorStatus) }}
          </el-tag>
          <span v-if="detail.vectorChunks" class="inline-hint">{{ detail.vectorChunks }} 个分块</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标签" :span="2">
          <div v-if="tags.length > 0" class="tag-list">
            <el-tag v-for="tag in tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="附件信息" :span="2">
          <span v-if="hasAttachment">{{ detail.originalFileName || detail.fileName || '已绑定附件' }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
          <div class="content-box">{{ detail.content || '-' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getKnowledgeItemDetail, getKnowledgeItemDownloadInfo, listAllKnowledgeBases } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref({})
const baseMap = ref({})

const baseName = computed(() => baseMap.value[detail.value.kbId] || '')
const hasAttachment = computed(() => !!(detail.value.sourceDocId || detail.value.minioPath))

const tags = computed(() => {
  const value = detail.value.tags
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch (e) {
      return value.split(',').map(t => t.trim()).filter(Boolean)
    }
  }
  return []
})

function unwrap(res) {
  return res?.data ?? res
}

function vectorStatusText(status) {
  const map = { 0: '未处理', 1: '处理中', 2: '已完成', 3: '失败' }
  return map[status] || '未知'
}

function vectorStatusType(status) {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}

async function loadBaseMap() {
  const res = await listAllKnowledgeBases()
  const list = unwrap(res) || []
  const map = {}
  list.forEach(item => {
    if (item?.id) map[item.id] = item.name
  })
  baseMap.value = map
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getKnowledgeItemDetail(route.params.id)
    detail.value = unwrap(res) || {}
  } catch (error) {
    ElMessage.error('获取知识详情失败')
  } finally {
    loading.value = false
  }
}

function handleBack() {
  router.push('/knowledge/item')
}

function handleEdit() {
  router.push(`/knowledge/item/edit/${route.params.id}`)
}

function handlePreview() {
  const query = detail.value.sourceDocId
    ? { docId: detail.value.sourceDocId, itemId: detail.value.id }
    : { itemId: detail.value.id }
  router.push({ path: '/knowledge/document/preview', query })
}

async function handleDownload() {
  try {
    const res = await getKnowledgeItemDownloadInfo(detail.value.id)
    const data = unwrap(res)
    if (data?.downloadUrl) {
      window.open(data.downloadUrl, '_blank')
      return
    }
    ElMessage.warning('暂无可下载附件')
  } catch (error) {
    ElMessage.error('下载链接获取失败')
  }
}

onMounted(async () => {
  await Promise.all([loadBaseMap(), loadDetail()])
})
</script>

<style scoped lang="scss">
.knowledge-item-detail-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .right {
      display: flex;
      gap: 8px;
    }

    .title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .inline-hint {
    margin-left: 8px;
    color: #909399;
    font-size: 12px;
  }

  .tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .content-box {
    max-height: 360px;
    overflow-y: auto;
    padding: 10px;
    border-radius: 4px;
    background: #f6f8fb;
    white-space: pre-wrap;
    line-height: 1.7;
  }
}
</style>
