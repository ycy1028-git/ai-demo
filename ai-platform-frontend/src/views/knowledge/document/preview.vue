<template>
  <div class="document-preview-container" v-loading="loading">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="left">
            <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
            <span class="title">文档预览</span>
          </div>
          <div class="right">
            <el-button v-if="downloadUrl" type="primary" @click="openDownload">下载文件</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border class="meta-box">
        <el-descriptions-item label="文件名">{{ fileName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ fileType || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="previewUrl && useIframePreview" class="iframe-wrapper">
        <iframe :src="previewUrl" class="preview-iframe" frameborder="0" />
      </div>

      <div v-else class="text-preview-wrapper">
        <div class="text-title">文本预览</div>
        <div class="text-content">{{ previewText || '该文件暂不支持在线渲染，请使用下载查看。' }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getDocumentDetail,
  getDocumentDownloadUrl,
  getDocumentPreviewUrl,
  getKnowledgeItemDetail,
  getKnowledgeItemDownloadInfo,
  getKnowledgeItemPreviewInfo
} from '@/api/knowledge'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const fileName = ref('')
const fileType = ref('')
const previewUrl = ref('')
const downloadUrl = ref('')
const previewText = ref('')

const iframeTypes = ['pdf', 'txt', 'md', 'html', 'htm', 'xml', 'json', 'csv', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']
const useIframePreview = computed(() => iframeTypes.includes((fileType.value || '').toLowerCase()))

function unwrap(res) {
  return res?.data ?? res
}

function handleBack() {
  if (route.query.itemId) {
    router.push(`/knowledge/item/detail/${route.query.itemId}`)
    return
  }
  router.push('/knowledge/item')
}

function openDownload() {
  if (downloadUrl.value) {
    window.open(downloadUrl.value, '_blank')
  }
}

async function loadByItemId(itemId) {
  const [detailRes, previewRes, downloadRes] = await Promise.all([
    getKnowledgeItemDetail(itemId),
    getKnowledgeItemPreviewInfo(itemId),
    getKnowledgeItemDownloadInfo(itemId)
  ])

  const detail = unwrap(detailRes) || {}
  const preview = unwrap(previewRes) || {}
  const download = unwrap(downloadRes) || {}

  fileName.value = preview.fileName || detail.originalFileName || detail.title || ''
  fileType.value = preview.fileType || detail.fileType || ''
  previewUrl.value = preview.previewUrl || ''
  downloadUrl.value = preview.downloadUrl || download.downloadUrl || ''
  previewText.value = detail.content || ''

  if ((!previewText.value || !previewText.value.trim()) && detail.sourceDocId) {
    const docRes = await getDocumentDetail(detail.sourceDocId)
    const doc = unwrap(docRes) || {}
    previewText.value = doc.extractText || ''
  }
}

async function loadByDocId(docId) {
  const [docRes, previewRes, downloadRes] = await Promise.all([
    getDocumentDetail(docId),
    getDocumentPreviewUrl(docId),
    getDocumentDownloadUrl(docId)
  ])

  const doc = unwrap(docRes) || {}
  fileName.value = doc.originalName || doc.name || ''
  fileType.value = doc.fileType || ''
  previewUrl.value = unwrap(previewRes) || ''
  downloadUrl.value = unwrap(downloadRes) || ''
  previewText.value = doc.extractText || ''
}

onMounted(async () => {
  loading.value = true
  try {
    const { itemId, docId } = route.query
    if (itemId) {
      await loadByItemId(itemId)
      return
    }
    if (docId) {
      await loadByDocId(docId)
      return
    }
    ElMessage.warning('缺少预览参数')
    router.push('/knowledge/item')
  } catch (error) {
    ElMessage.error(error?.message || '文档预览加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.document-preview-container {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .meta-box {
    margin-bottom: 16px;
  }

  .iframe-wrapper {
    height: 72vh;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    overflow: hidden;

    .preview-iframe {
      width: 100%;
      height: 100%;
    }
  }

  .text-preview-wrapper {
    border: 1px solid #ebeef5;
    border-radius: 6px;
    background: #fafbfc;

    .text-title {
      padding: 12px 14px;
      border-bottom: 1px solid #ebeef5;
      font-weight: 600;
    }

    .text-content {
      white-space: pre-wrap;
      max-height: 70vh;
      overflow-y: auto;
      padding: 14px;
      line-height: 1.7;
      color: #303133;
    }
  }
}
</style>
