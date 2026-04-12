<template>
  <div class="knowledge-base-container">
    <!-- 搜索栏 -->
    <SearchBar
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作按钮 -->
    <div class="table-operations">
      <el-button type="primary" :icon="Plus" @click="handleCreate">新建知识库</el-button>
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
        <el-table-column prop="name" label="知识库名称" min-width="150" />
        <el-table-column prop="code" label="编码" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sceneDescription" label="业务场景" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.sceneDescription || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="documentCount" label="文档数量" width="100" align="center" />
        <el-table-column prop="priority" label="优先级" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.priority > 0 ? 'warning' : 'info'" size="small">
              {{ row.priority || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleManage(row)">知识</el-button>
            <el-button type="primary" link @click="handleDocuments(row)">文档</el-button>
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

    <!-- 删除确认对话框 -->
    <el-dialog
      v-model="deleteDialogVisible"
      title="确认删除"
      width="400px"
    >
      <p>确定要删除知识库「{{ currentRow?.name }}」吗？</p>
      <p style="color: #f56c6c; font-size: 12px; margin-top: 8px;">删除后无法恢复，请谨慎操作</p>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="confirmDelete">确定删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import SearchBar from '@/components/SearchBar.vue'
import { getKnowledgeBaseList, deleteKnowledgeBase } from '@/api/knowledge'

const router = useRouter()

// 加载状态
const loading = ref(false)
const deleteLoading = ref(false)
const deleteDialogVisible = ref(false)
const currentRow = ref(null)

// 搜索字段配置
const searchFields = [
  { prop: 'name', label: '知识库名称', type: 'input', placeholder: '请输入知识库名称' },
  { prop: 'status', label: '状态', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 }
  ]}
]

// 表单数据
const searchForm = reactive({
  name: '',
  status: ''
})

// 表格数据
const tableData = ref([])

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const params = {
      keyword: searchForm.name || undefined,
      type: undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      page: pagination.current,
      size: pagination.pageSize
    }
    const res = await getKnowledgeBaseList(params)
    if (res.code === 200) {
      // 后端返回: { total, records, current, size, pages }
      // 经过 Result 包装后: { code, message, data: { total, records, ... } }
      const data = res.data || res
      const list = data.records || data.content || []
      // 转换字段名以匹配前端表格
      tableData.value = list.map(item => ({
        id: item.id,
        name: item.name,
        code: item.code,
        description: item.description,
        sceneDescription: item.sceneDescription,
        documentCount: item.documentCount || item.itemCount || 0,
        priority: item.priority || 0,
        status: item.status,
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
function handleSearch(form) {
  Object.assign(searchForm, form)
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
  router.push('/knowledge/base/create')
}

// 编辑
function handleEdit(row) {
  router.push(`/knowledge/base/edit/${row.id}`)
}

// 管理
function handleManage(row) {
  router.push({ path: '/knowledge/item', query: { baseId: row.id } })
}

// 文档管理
function handleDocuments(row) {
  router.push({ path: '/knowledge/item', query: { baseId: row.id, tab: 'document' } })
}

// 删除
function handleDelete(row) {
  currentRow.value = row
  deleteDialogVisible.value = true
}

// 确认删除
async function confirmDelete() {
  deleteLoading.value = true
  try {
    await deleteKnowledgeBase(currentRow.value.id)
    ElMessage.success('删除成功')
    deleteDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
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

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.knowledge-base-container {
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

  .oss-path {
    font-family: monospace;
    font-size: 12px;
    color: #606266;
  }

  .text-muted {
    color: #c0c4cc;
    font-size: 12px;
  }
}
</style>
