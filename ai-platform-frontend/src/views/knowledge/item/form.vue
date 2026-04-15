<template>
  <div class="knowledge-item-form-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
          <span>{{ isEdit ? '编辑知识' : '添加知识' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="form-container"
      >

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="请输入知识标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="知识库" prop="baseId">
          <el-select v-model="form.baseId" placeholder="请选择知识库" style="width: 100%">
            <el-option
              v-for="item in baseList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="请输入知识内容"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="标签">
          <div class="tag-input-wrapper">
            <el-input
              v-model="tagInput"
              placeholder="输入标签后按回车添加，支持分隔符自动拆分"
              @keydown.enter.prevent="handleAddTags"
              @blur="handleAddTags"
            >
              <template #append>
                <el-button :icon="Plus" @click="handleAddTags" />
              </template>
            </el-input>
            <div class="tag-hint">
              提示：多个标签可用
              <el-tag size="small" effect="plain" style="margin: 0 4px;">中文逗号</el-tag>
              <el-tag size="small" effect="plain" style="margin: 0 4px;">英文逗号</el-tag>
              <el-tag size="small" effect="plain" style="margin: 0 4px;">分号</el-tag>
              或
              <el-tag size="small" effect="plain" style="margin: 0 4px;">空格</el-tag>
              分隔，一次性录入
            </div>
            <div class="tag-list-wrapper">
              <el-tag
                v-for="(tag, index) in form.tags"
                :key="index"
                closable
                :disable-transitions="false"
                @close="handleRemoveTag(index)"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 文档上传区域 -->
        <el-form-item label="相关文档">
          <div class="upload-area">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :on-change="handleFileChange"
              :on-remove="handleUploadRemove"
              :file-list="fileList"
              :limit="1"
              :multiple="false"
              accept=".txt,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.html,.md"
              :disabled="uploadingAttachment"
            >
              <el-button type="primary" plain>
                <el-icon><Upload /></el-icon>
                选择文件
              </el-button>
              <template #tip>
                <div class="upload-tip">
                  支持 txt/pdf/doc/docx/xls/xlsx/ppt/pptx/html/md，单个文件不超过100MB
                </div>
                <div class="upload-tip">
                  保存后会将附件内容写入 ES 字段 file.content 并参与向量检索
                </div>
                <div v-if="attachment" class="attachment-actions">
                  <el-button type="primary" link size="small" @click="handlePreviewAttachment">预览附件</el-button>
                </div>
              </template>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '提交' }}
          </el-button>
          <el-button @click="handleBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Upload, Plus } from '@element-plus/icons-vue'
import { listAllKnowledgeBases, getKnowledgeItemDetail, createKnowledgeItem, updateKnowledgeItem, uploadDocument, deleteDocument } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const submitting = ref(false)
const loading = ref(false)
const uploadRef = ref(null)
const fileList = ref([])
const tagInput = ref('')
const uploadingAttachment = ref(false)
const attachment = ref(null)
const removedDocIds = ref([])

const baseList = ref([])

const form = reactive({
  baseId: '',
  title: '',
  content: '',
  tags: [],
  status: 1,
  sourceDocId: '',
  minioPath: '',
  originalFileName: '',
  fileType: ''
})

const rules = {
  baseId: [
    { required: true, message: '请选择知识库', trigger: 'change' }
  ],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 2, max: 200, message: '长度在 2 到 200 个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' }
  ]
}

const unwrap = (res) => res?.data ?? res

function parseTags(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) {
    return raw.map(String)
  }
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        return parsed.map(String)
      }
    } catch (e) {
      // fall through to split
    }
    return raw.split(',').map(t => t.trim()).filter(Boolean)
  }
  return []
}

async function fetchBaseList() {
  try {
    const res = await listAllKnowledgeBases()
    const data = unwrap(res)
    const list = Array.isArray(data) ? data : []
    baseList.value = list.map(item => ({ id: item.id, name: item.name, status: item.status }))
    if (!form.baseId) {
      const queryBaseId = route.query.baseId
      if (queryBaseId && baseList.value.some(item => item.id === queryBaseId)) {
        form.baseId = queryBaseId
      } else if (baseList.value.length > 0) {
        form.baseId = baseList.value[0].id
      }
    }
  } catch (error) {
    ElMessage.error('获取知识库列表失败')
  }
}

async function fetchDetail() {
  if (!isEdit.value) {
    if (route.query.baseId) {
      form.baseId = route.query.baseId
    }
    return
  }

  loading.value = true
  try {
    const res = await getKnowledgeItemDetail(route.params.id)
    const data = unwrap(res) || {}
    form.baseId = data.kbId || form.baseId
    form.title = data.title || ''
    form.content = data.content || ''
    form.tags = parseTags(data.tags)
    form.status = data.status ?? 1
    form.sourceDocId = data.sourceDocId || ''
    form.minioPath = data.minioPath || ''
    form.originalFileName = data.originalFileName || ''
    form.fileType = data.fileType || ''
    if (form.minioPath) {
      attachment.value = {
        docId: form.sourceDocId || null,
        fileName: form.originalFileName || data.title,
        fileType: form.fileType,
        minioPath: form.minioPath,
        isNew: false
      }
    } else {
      attachment.value = null
    }
    syncFileList()
  } catch (error) {
    ElMessage.error('获取详情失败')
  } finally {
    loading.value = false
  }
}

// 文件变化
async function handleFileChange(uploadFile) {
  if (!uploadFile || !uploadFile.raw) {
    return
  }
  if (!form.baseId) {
    ElMessage.warning('请先选择知识库')
    uploadRef.value?.clearFiles()
    fileList.value = []
    return
  }

  if (attachment.value) {
    await handleAttachmentRemove({ silent: true })
  }

  uploadingAttachment.value = true
  try {
    const formData = new FormData()
    formData.append('kbId', form.baseId)
    formData.append('file', uploadFile.raw)
    const res = await uploadDocument(formData)
    const doc = unwrap(res)
    if (!doc) {
      throw new Error('上传返回数据为空')
    }
    attachment.value = {
      docId: doc.id,
      fileName: doc.name || doc.originalName || uploadFile.name,
      fileType: doc.fileType,
      minioPath: doc.minioPath,
      isNew: true
    }
    form.sourceDocId = doc.id || ''
    form.minioPath = doc.minioPath || ''
    form.originalFileName = doc.name || doc.originalName || uploadFile.name
    form.fileType = doc.fileType || ''
    syncFileList()
    ElMessage.success('文件上传成功')
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('文件上传失败')
    fileList.value = []
  } finally {
    uploadingAttachment.value = false
    uploadRef.value?.clearFiles()
  }
}

async function handleUploadRemove() {
  await handleAttachmentRemove()
}

function handlePreviewAttachment() {
  if (!attachment.value?.docId) {
    ElMessage.warning('暂无可预览附件')
    return
  }
  const query = { docId: attachment.value.docId }
  if (isEdit.value && route.params.id) {
    query.itemId = route.params.id
  }
  router.push({ path: '/knowledge/document/preview', query })
}

// 移除标签
function handleRemoveTag(index) {
  form.tags.splice(index, 1)
}

/**
 * 添加标签：支持中英文逗号、分号、空格作为分隔符拆分输入
 * 拆分后自动去除首尾空白，忽略空标签
 */
function handleAddTags() {
  const raw = tagInput.value.trim()
  if (!raw) return

  // 支持的分隔符：中文逗号、英文逗号、分号、空格
  const parts = raw.split(/[,，;；\s]+/).map(t => t.trim()).filter(t => t.length > 0)

  parts.forEach(tag => {
    if (!form.tags.includes(tag)) {
      form.tags.push(tag)
    }
  })
  tagInput.value = ''
}

function normalizeTags(tags) {
  return (tags || [])
    .map(tag => (typeof tag === 'string' ? tag.trim() : `${tag}`).trim())
    .filter((tag, index, arr) => tag.length > 0 && arr.indexOf(tag) === index)
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  submitting.value = true
  try {
    const payload = {
      kbId: form.baseId,
      title: form.title.trim(),
      content: form.content,
      tags: normalizeTags(form.tags),
      status: form.status,
      sourceDocId: form.sourceDocId || null,
      minioPath: form.minioPath || null,
      originalFileName: form.originalFileName || null,
      fileType: form.fileType || null
    }

    let targetId = route.params.id
    if (isEdit.value) {
      const res = await updateKnowledgeItem(route.params.id, payload)
      targetId = (unwrap(res) || {}).id || route.params.id
      ElMessage.success('保存成功')
    } else {
      const res = await createKnowledgeItem(payload)
      targetId = (unwrap(res) || {}).id
      ElMessage.success('提交成功')
    }
    await flushRemovedDocuments()
    if (attachment.value) {
      attachment.value.isNew = false
    }
    router.push(targetId ? `/knowledge/item/detail/${targetId}` : '/knowledge/item')
  } catch (error) {
    ElMessage.error(isEdit.value ? '保存失败' : '提交失败')
  } finally {
    submitting.value = false
  }
}

// 返回列表
function handleBack() {
  router.push('/knowledge/item')
}

onMounted(async () => {
  await fetchBaseList()
  await fetchDetail()
  syncFileList()
})

watch(() => form.baseId, (newVal, oldVal) => {
  if (!oldVal || !newVal || newVal === oldVal) {
    return
  }
  if (attachment.value) {
    handleAttachmentRemove({ silent: true })
  }
})

function syncFileList() {
  if (attachment.value) {
    fileList.value = [{
      name: attachment.value.fileName || '附件',
      status: 'finished',
      uid: attachment.value.docId || `${Date.now()}`
    }]
  } else {
    fileList.value = []
  }
}

async function handleAttachmentRemove(options = {}) {
  const { silent = false } = options
  if (!attachment.value) {
    if (!silent) {
      ElMessage.info('暂无附件')
    }
    fileList.value = []
    return
  }
  const docId = attachment.value.docId
  if (docId) {
    if (attachment.value.isNew) {
      await deleteDocumentById(docId)
    } else if (!removedDocIds.value.includes(docId)) {
      removedDocIds.value = [...removedDocIds.value, docId]
    }
  }
  clearAttachmentState()
  if (!silent) {
    ElMessage.success('已移除附件')
  }
}

function clearAttachmentState() {
  attachment.value = null
  form.sourceDocId = ''
  form.minioPath = ''
  form.originalFileName = ''
  form.fileType = ''
  fileList.value = []
  uploadRef.value?.clearFiles()
}

async function deleteDocumentById(docId) {
  if (!docId) return
  try {
    await deleteDocument(docId)
  } catch (error) {
    console.warn('删除文档失败:', error)
  }
}

async function flushRemovedDocuments() {
  if (removedDocIds.value.length === 0) {
    return
  }
  const tasks = removedDocIds.value.map(id => deleteDocumentById(id))
  await Promise.all(tasks)
  removedDocIds.value = []
}
</script>

<style lang="scss" scoped>
.knowledge-item-form-container {
  .card-header {
    display: flex;
    align-items: center;
    gap: 16px;

    span {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .form-container {
    max-width: 900px;
  }

  .upload-area {
    width: 100%;
  }

  .upload-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
  }

  .tag-input-wrapper {
    width: 100%;

    .tag-hint {
      font-size: 12px;
      color: #909399;
      margin: 6px 0 8px;
      line-height: 1.4;
    }

    .tag-list-wrapper {
      min-height: 36px;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .el-tag {
        font-size: 13px;
      }
    }
  }
}
</style>
