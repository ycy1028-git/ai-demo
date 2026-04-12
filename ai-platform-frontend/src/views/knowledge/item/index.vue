<template>
  <div class="knowledge-item-container">
    <!-- 搜索栏 -->
    <SearchBar
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作按钮 -->
    <div class="table-operations">
      <el-button type="primary" :icon="Plus" @click="handleCreate">添加知识</el-button>
      <el-button :icon="Upload" @click="uploadDialogVisible = true">批量上传</el-button>
      <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
    </div>

    <!-- 数据表格 -->
    <el-card>
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sourceType" label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceType === 'document' ? 'primary' : 'info'">
              {{ row.sourceType === 'document' ? '文档' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="vectorStatus" label="向量化" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getVectorStatusType(row.vectorStatus)">
              {{ getVectorStatusText(row.vectorStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.minioPath"
              type="primary"
              link
              @click="handlePreview(row)"
            >
              预览
            </el-button>
            <el-button
              v-if="row.minioPath"
              type="primary"
              link
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 批量上传对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="批量上传文档"
      width="600px"
    >
      <el-form label-width="100px">
        <el-form-item label="选择知识库">
          <el-select v-model="uploadForm.baseId" placeholder="请选择知识库" style="width: 100%">
            <el-option
              v-for="item in baseList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="10"
            :on-exceed="handleExceed"
            :file-list="fileList"
            drag
            multiple
            accept=".txt,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.html,.md"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 txt/pdf/doc/docx/xls/xlsx/ppt/pptx/html/md 格式，单文件不超过100MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">开始上传</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="知识详情"
      width="800px"
    >
      <div class="knowledge-detail" v-if="currentRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题">{{ currentRow.title }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <el-tag size="small" :type="currentRow.sourceType === 'document' ? 'primary' : 'info'">
              {{ currentRow.sourceType === 'document' ? '文档' : '手动' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentRow.status === 1 ? 'success' : 'info'">
              {{ currentRow.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="向量化">
            <el-tag size="small" :type="getVectorStatusType(currentRow.vectorStatus)">
              {{ getVectorStatusText(currentRow.vectorStatus) }}
            </el-tag>
            <span v-if="currentRow.vectorChunks" style="margin-left: 8px;">
              ({{ currentRow.vectorChunks }} 个分块)
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentRow.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="关联文件" v-if="currentRow.minioPath">
            <el-button type="primary" link size="small" @click="handlePreview(currentRow)">
              预览
            </el-button>
            <el-button type="primary" link size="small" @click="handleDownload(currentRow)">
              下载
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">
            <div class="content-box">{{ currentRow.content }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewDialogVisible"
      title="文件预览"
      width="900px"
      top="5vh"
    >
      <div class="preview-container">
        <iframe
          v-if="previewUrl"
          :src="previewUrl"
          class="preview-iframe"
          frameborder="0"
        />
        <div v-else class="preview-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在加载预览...</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Upload, UploadFilled, Loading } from '@element-plus/icons-vue'
import SearchBar from '@/components/SearchBar.vue'
import { getKnowledgeItemList, deleteKnowledgeItem, uploadDocument, getKnowledgeBaseList } from '@/api/knowledge'
import axios from 'axios'

const route = useRoute()
const router = useRouter()

// 加载状态
const loading = ref(false)
const uploading = ref(false)
const uploadDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const currentRow = ref(null)
const uploadRef = ref(null)
const fileList = ref([])

// 预览
const previewUrl = ref('')

// 搜索字段配置
const searchFields = [
  { prop: 'title', label: '标题', type: 'input', placeholder: '请输入标题' },
  { prop: 'sourceType', label: '来源', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '文档', value: 'document' },
    { label: '手动', value: 'manual' }
  ]},
  { prop: 'status', label: '状态', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 }
  ]}
]

// 表单数据
const searchForm = reactive({
  title: '',
  sourceType: '',
  status: ''
})

// 知识库列表
const baseList = ref([])

// 加载知识库列表
async function loadBaseList() {
  try {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    if (res.code === 200 && res.data) {
      baseList.value = (res.data.list || res.data.content || []).map(item => ({
        id: item.id,
        name: item.name
      }))
    }
  } catch (error) {
    console.error('获取知识库列表失败:', error)
  }
}

// 上传表单
const uploadForm = reactive({
  baseId: ''
})

// 表格数据
const tableData = ref([])

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 获取向量化状态类型
function getVectorStatusType(status) {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}

// 获取向量化状态文本
function getVectorStatusText(status) {
  const map = { 0: '未处理', 1: '处理中', 2: '已完成', 3: '失败' }
  return map[status] || '未知'
}

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const params = {
      kbId: route.query.baseId || undefined,
      keyword: searchForm.title || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      page: pagination.current,
      size: pagination.pageSize
    }
    const res = await getKnowledgeItemList(params)
    if (res.code === 200) {
      // 后端返回: { total, records, current, size, pages }
      // 经过 Result 包装后: { code, message, data: { total, records, ... } }
      const data = res.data || res
      const list = data.records || data.content || []
      tableData.value = list.map(item => ({
        id: item.id,
        title: item.title,
        content: item.content || '',
        summary: item.summary || '',
        tags: item.tags || [],
        sourceType: item.sourceType || 'manual',
        minioPath: item.minioPath || item.ossPath || null,
        originalFileName: item.originalFileName || item.fileName || null,
        fileType: item.fileType || null,
        status: item.status,
        vectorStatus: item.vectorStatus || 0,
        vectorChunks: item.vectorChunks || 0,
        createdAt: formatDateTime(item.createdAt || item.createTime)
      }))
      pagination.total = data.total || list.length
    }
  } catch (error) {
    console.error('获取数据失败:', error)
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

// 格式化日期时间
function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 搜索
function handleSearch(formData) {
  Object.assign(searchForm, formData)
  pagination.current = 1
  fetchData()
}

// 重置
function handleReset() {
  Object.keys(searchForm).forEach(key => {
    searchForm[key] = ''
  })
  pagination.current = 1
  fetchData()
}

// 新建
function handleCreate() {
  router.push(`/knowledge/item/create?baseId=${route.query.baseId || ''}`)
}

// 编辑
function handleEdit(row) {
  router.push(`/knowledge/item/edit/${row.id}`)
}

// 查看
function handleView(row) {
  currentRow.value = row
  viewDialogVisible.value = true
}

// 预览
async function handlePreview(row) {
  try {
    loading.value = true
    previewUrl.value = 'https://www.w3.org/WAI/WCAG21/Techniques/pdf/img/table-word.jpg'
    previewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取预览链接失败')
  } finally {
    loading.value = false
  }
}

// 下载
async function handleDownload(row) {
  try {
    loading.value = true
    ElMessage.success(`开始下载: ${row.originalFileName || row.title}`)
  } catch (error) {
    ElMessage.error('获取下载链接失败')
  } finally {
    loading.value = false
  }
}

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除知识「${row.title}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteKnowledgeItem(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

// 分页大小改变
function handleSizeChange(size) {
  pagination.pageSize = size
  fetchData()
}

// 分页页码改变
function handleCurrentChange(page) {
  pagination.current = page
  fetchData()
}

// 文件超出限制
function handleExceed() {
  ElMessage.warning('最多上传10个文件')
}

// 上传文件
async function handleUpload() {
  if (!uploadForm.baseId) {
    ElMessage.warning('请选择知识库')
    return
  }

  const uploadInstance = uploadRef.value
  if (!uploadInstance || !uploadInstance.uploadFiles || uploadInstance.uploadFiles.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('kbId', uploadForm.baseId)
    uploadInstance.uploadFiles.forEach(file => {
      formData.append('files', file.raw)
    })
    await uploadDocument(formData)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    uploadRef.value.clearFiles()
    fetchData()
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  // 如果有baseId参数，自动选中
  if (route.query.baseId) {
    uploadForm.baseId = route.query.baseId
  }
  loadBaseList()
  fetchData()
})
</script>

<style lang="scss" scoped>
.knowledge-item-container {
  .table-operations {
    margin-bottom: 16px;
    display: flex;
    gap: 10px;
  }

  .pagination-wrapper {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .content-box {
    max-height: 200px;
    overflow-y: auto;
    padding: 8px;
    background-color: #f5f7fa;
    border-radius: 4px;
    white-space: pre-wrap;
    font-size: 14px;
    line-height: 1.6;
  }

  .preview-container {
    width: 100%;
    height: 70vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f5f7fa;

    .preview-iframe {
      width: 100%;
      height: 100%;
      border-radius: 4px;
    }

    .preview-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      color: #909399;

      .el-icon {
        font-size: 32px;
      }
    }
  }
}
</style>
