<template>
  <div class="model-config-container">
    <!-- 搜索栏 -->
    <SearchBar
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作按钮 -->
    <div class="table-operations">
      <el-button type="primary" :icon="Plus" @click="handleCreate">新建模型</el-button>
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
        <el-table-column prop="name" label="模型名称" min-width="150">
          <template #default="{ row }">
            <div class="model-name-cell">
              <span class="model-name">{{ row.name }}</span>
              <el-tag v-if="row.isDefault" type="success" size="small" effect="dark">默认</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="提供商" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getProviderTagType(row.provider)" size="small">
              {{ getProviderLabel(row.provider) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型标识" min-width="180">
          <template #default="{ row }">
            <code class="model-code">{{ row.modelName }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="apiUrl" label="API地址" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="api-url">{{ row.apiUrl || '使用默认地址' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="参数配置" width="160" align="center">
          <template #default="{ row }">
            <div class="param-cell">
              <span>温度: {{ row.temperature || 0.7 }}</span>
              <span>最大Token: {{ row.maxTokens || 2000 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="!row.isDefault"
              type="success"
              link
              :loading="settingDefaultId === row.id"
              @click="handleSetDefault(row)"
            >
              设为默认
            </el-button>
            <el-button
              type="warning"
              link
              :loading="testingId === row.id"
              @click="handleTest(row)"
            >
              测试
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

    <!-- 删除确认对话框 -->
    <el-dialog
      v-model="deleteDialogVisible"
      title="确认删除"
      width="400px"
    >
      <p>确定要删除模型配置「{{ currentRow?.name }}」吗？</p>
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
import { getModelConfigList, deleteModelConfig, setDefaultModel, testModelConnection } from '@/api/modelConfig'

const router = useRouter()

// 加载状态
const loading = ref(false)
const deleteLoading = ref(false)
const deleteDialogVisible = ref(false)
const currentRow = ref(null)
const settingDefaultId = ref(null)
const testingId = ref(null)

// 搜索字段配置
const searchFields = [
  { prop: 'name', label: '模型名称', type: 'input', placeholder: '请输入模型名称' },
  { prop: 'provider', label: '提供商', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '通义千问', value: 'qwen' },
    { label: 'DeepSeek', value: 'deepseek' },
    { label: '智谱GLM', value: 'zhipu' },
    { label: 'OpenAI', value: 'openai' }
  ]},
  { prop: 'enabled', label: '状态', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '启用', value: true },
    { label: '禁用', value: false }
  ]}
]

// 表单数据
const searchForm = reactive({
  name: '',
  provider: '',
  enabled: ''
})

// 表格数据
const tableData = ref([])

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 提供商映射
const providerMap = {
  qwen: { label: '通义千问', tagType: 'primary' },
  dashscope: { label: '通义千问', tagType: 'primary' },
  deepseek: { label: 'DeepSeek', tagType: 'warning' },
  zhipu: { label: '智谱GLM', tagType: 'danger' },
  openai: { label: 'OpenAI', tagType: 'success' }
}

function getProviderLabel(provider) {
  return providerMap[provider?.toLowerCase()]?.label || provider || '未知'
}

function getProviderTagType(provider) {
  return providerMap[provider?.toLowerCase()]?.tagType || 'info'
}

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      size: pagination.pageSize
    }
    // 添加搜索条件
    if (searchForm.name) {
      params.name = searchForm.name
    }
    if (searchForm.provider) {
      params.provider = searchForm.provider
    }
    if (searchForm.enabled !== '') {
      params.enabled = searchForm.enabled
    }

    const res = await getModelConfigList(params)
    const data = res.data || res

    // 后端返回分页格式
    tableData.value = data.records || []
    pagination.total = data.total || 0
  } catch (error) {
    console.error('获取数据失败', error)
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
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
    searchForm[key] = key === 'enabled' ? '' : ''
  })
  pagination.current = 1
  fetchData()
}

// 新建
function handleCreate() {
  router.push('/system/model/create')
}

// 编辑
function handleEdit(row) {
  router.push(`/system/model/edit/${row.id}`)
}

// 设置默认
async function handleSetDefault(row) {
  try {
    await ElMessageBox.confirm(
      `确定要将「${row.name}」设为默认模型吗？`,
      '设置默认模型',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    settingDefaultId.value = row.id
    console.log('设置默认模型, row.id:', row.id, 'type:', typeof row.id)
    const idStr = String(row.id)
    console.log('转换后的ID:', idStr)
    await setDefaultModel(idStr)
    ElMessage.success('设置成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('设置失败')
    }
  } finally {
    settingDefaultId.value = null
  }
}

// 测试连接
async function handleTest(row) {
  testingId.value = row.id
  try {
    ElMessage.info('正在测试连接...')
    console.log('测试连接, row.id:', row.id, 'type:', typeof row.id)
    const idStr = String(row.id)
    console.log('转换后的ID:', idStr)
    const res = await testModelConnection(idStr)
    ElMessage.success(res.data || '连接测试完成')
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    testingId.value = null
  }
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
    console.log('删除模型, currentRow.value.id:', currentRow.value.id, 'type:', typeof currentRow.value.id)
    const idStr = String(currentRow.value.id)
    console.log('转换后的ID:', idStr)
    await deleteModelConfig(idStr)
    ElMessage.success('删除成功')
    deleteDialogVisible.value = false
    fetchData()
  } catch (error) {
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
.model-config-container {
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

  .model-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;

    .model-name {
      font-weight: 500;
    }
  }

  .model-code {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 12px;
    color: #606266;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
  }

  .api-url {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 12px;
    color: #909399;
  }

  .param-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
