<template>
  <div class="flow-template-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <div class="page-title">
          <el-icon :size="22"><Operation /></el-icon>
          <span>流程模板管理</span>
        </div>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="handleCreate">
          <el-icon :size="16"><Plus /></el-icon>
          新建模板
        </el-button>
      </div>
    </div>

    <!-- 搜索筛选 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索模板名称或编码"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="searchStatus" placeholder="状态" clearable class="status-select">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <div class="table-container">
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        border
        :header-cell-style="{ background: 'var(--el-fill-color-light)', color: 'var(--el-text-color-regular)' }"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="templateName" label="模板名称" min-width="150" />
        <el-table-column prop="templateCode" label="模板编码" width="180" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isFallback === 1" type="danger" size="small">兜底</el-tag>
            <el-tag v-else-if="row.isDynamic === 1" type="success" size="small">动态</el-tag>
            <el-tag v-else type="info" size="small">固定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">
            {{ row.version || 1 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleDesign(row)">编排</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, row)">
              <el-button type="primary" link>
                更多
                <el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="toggle">
                    {{ row.status === 1 ? '禁用' : '启用' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="publish" :disabled="row.status === 1">
                    发布
                  </el-dropdown-item>
                  <el-dropdown-item command="copy">
                    复制
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided style="color: var(--el-color-danger)">
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="formData.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="formData.templateCode" placeholder="请输入模板编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="2"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="匹配关键词" prop="matchPattern">
          <el-input
            v-model="formData.matchPattern"
            placeholder="用于快速匹配的关键词，如：报销|请假|订单"
          />
        </el-form-item>
        <el-form-item label="匹配提示词" prop="matchPrompt">
          <el-input
            v-model="formData.matchPrompt"
            type="textarea"
            :rows="3"
            placeholder="AI 用于识别该模板的提示词，描述该模板处理的业务场景"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="formData.priority" :min="0" :max="999" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模板类型" prop="isDynamic">
              <el-radio-group v-model="formData.isDynamic">
                <el-radio :label="0">固定模板</el-radio>
                <el-radio :label="1">动态模板</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="兜底模板" prop="isFallback">
              <el-switch v-model="formData.isFallback" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Operation, Plus, Search, ArrowDown } from '@element-plus/icons-vue'
import { 
  getFlowTemplateList, 
  createFlowTemplate, 
  updateFlowTemplate, 
  deleteFlowTemplate, 
  updateFlowTemplateStatus,
  publishFlowTemplate
} from '@/api/flow'

const router = useRouter()

// 表格数据
const loading = ref(false)
const tableData = ref([])
const searchKeyword = ref('')
const searchStatus = ref(null)

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新建模板')
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

// 表单数据
const formData = reactive({
  id: null,
  templateName: '',
  templateCode: '',
  description: '',
  matchPattern: '',
  matchPrompt: '',
  priority: 0,
  isDynamic: 1,
  isFallback: 0,
  status: 1
})

// 表单验证规则
const formRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  templateCode: [
    { required: true, message: '请输入模板编码', trigger: 'blur' },
    { pattern: /^[a-z_]+$/, message: '编码只能包含小写字母和下划线', trigger: 'blur' }
  ]
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchKeyword.value || undefined,
      status: searchStatus.value !== null ? searchStatus.value : undefined
    }
    const res = await getFlowTemplateList(params)
    // 后端返回: { total, records, current, size, pages }
    const data = res.data || res
    tableData.value = data.records || data.content || []
    pagination.total = data.total || data.totalElements || 0
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 搜索
function handleSearch() {
  pagination.page = 1
  loadData()
}

// 重置
function handleReset() {
  searchKeyword.value = ''
  searchStatus.value = null
  handleSearch()
}

// 分页
function handleSizeChange() {
  pagination.page = 1
  loadData()
}

function handlePageChange() {
  loadData()
}

// 新建
function handleCreate() {
  isEdit.value = false
  dialogTitle.value = '新建模板'
  resetForm()
  dialogVisible.value = true
}

// 编辑
function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '编辑模板'
  Object.assign(formData, {
    id: row.id,
    templateName: row.templateName,
    templateCode: row.templateCode,
    description: row.description,
    matchPattern: row.matchPattern,
    matchPrompt: row.matchPrompt,
    priority: row.priority || 0,
    isDynamic: row.isDynamic || 1,
    isFallback: row.isFallback || 0,
    status: row.status
  })
  dialogVisible.value = true
}

// 编排
function handleDesign(row) {
  router.push(`/flow/editor/${row.id}`)
}

// 更多操作
async function handleCommand(command, row) {
  switch (command) {
    case 'toggle':
      await handleToggleStatus(row)
      break
    case 'publish':
      await handlePublish(row)
      break
    case 'copy':
      await handleCopy(row)
      break
    case 'delete':
      await handleDelete(row)
      break
  }
}

// 切换状态
async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${action}该模板吗？`, '提示', { type: 'warning' })
    await updateFlowTemplateStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`${action}失败`)
    }
  }
}

// 发布
async function handlePublish(row) {
  try {
    await ElMessageBox.confirm('确定要发布该模板吗？发布后将启用该模板。', '提示', { type: 'warning' })
    await publishFlowTemplate(row.id)
    ElMessage.success('发布成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('发布失败')
    }
  }
}

// 复制
async function handleCopy(row) {
  try {
    await ElMessageBox.confirm(`确定要复制模板"${row.templateName}"吗？`, '提示', { type: 'info' })
    await createFlowTemplate({
      templateName: row.templateName + '_副本',
      templateCode: row.templateCode + '_copy',
      description: row.description,
      matchPattern: row.matchPattern,
      matchPrompt: row.matchPrompt,
      priority: row.priority,
      isDynamic: row.isDynamic,
      isFallback: 0,
      status: 0
    })
    ElMessage.success('复制成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('复制失败')
    }
  }
}

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定要删除该模板吗？删除后将无法恢复。', '警告', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteFlowTemplate(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 重置表单
function resetForm() {
  Object.assign(formData, {
    id: null,
    templateName: '',
    templateCode: '',
    description: '',
    matchPattern: '',
    matchPrompt: '',
    priority: 0,
    isDynamic: 1,
    isFallback: 0,
    status: 1
  })
  formRef.value?.clearValidate()
}

// 提交
async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateFlowTemplate(formData)
      ElMessage.success('更新成功')
    } else {
      await createFlowTemplate(formData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style lang="scss" scoped>
.flow-template-container {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;

  .header-left {
    .page-title {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 18px;
      font-weight: 600;
      color: var(--el-text-color-primary);

      .el-icon {
        color: var(--el-color-primary);
      }
    }
  }
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;

  .search-input {
    width: 240px;
  }

  .status-select {
    width: 120px;
  }
}

.table-container {
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 16px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 16px 20px;
  margin-right: 0;
}

:deep(.el-dialog__body) {
  padding: 24px 20px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 16px 20px;
}
</style>
