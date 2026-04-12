<template>
  <div class="credential-management-container">
    <!-- 搜索栏 -->
    <SearchBar
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作按钮 -->
    <div class="table-operations">
      <el-button type="primary" :icon="Plus" @click="handleCreate">新建凭证</el-button>
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
        <el-table-column prop="name" label="凭证名称" min-width="150" />
        <el-table-column prop="appId" label="应用ID" width="150" />
        <el-table-column prop="apiKey" label="API Key" min-width="250">
          <template #default="{ row }">
            <div class="api-key-cell">
              <code class="api-key-value">{{ row.showKey ? row.apiKey : maskString(row.apiKey) }}</code>
              <el-button type="primary" link @click="toggleKeyShow(row)">
                {{ row.showKey ? '隐藏' : '显示' }}
              </el-button>
              <el-button type="primary" link @click="copyKey(row.apiKey)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="额度" width="200" align="center">
          <template #default="{ row }">
            <div class="quota-cell">
              <div class="quota-item">
                <span class="quota-label">今日:</span>
                <span class="quota-value">{{ row.todayCalls || 0 }}</span>
              </div>
              <div class="quota-item">
                <span class="quota-label">本月:</span>
                <span class="quota-value">{{ row.monthlyCalls || 0 }}</span>
              </div>
              <div class="quota-item">
                <span class="quota-label">总计:</span>
                <span class="quota-value">{{ row.totalCalls || 0 }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimitQps" label="QPS限制" width="100" align="center" />
        <el-table-column prop="dailyQuota" label="日额度" width="100" align="center">
          <template #default="{ row }">
            {{ row.dailyQuota < 0 ? '不限' : row.dailyQuota }}
          </template>
        </el-table-column>
        <el-table-column prop="monthlyQuota" label="月额度" width="100" align="center">
          <template #default="{ row }">
            {{ row.monthlyQuota < 0 ? '不限' : row.monthlyQuota }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireTime" label="过期时间" width="160">
          <template #default="{ row }">
            {{ row.expireTime || '永不过期' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link @click="handleResetSecret(row)">重置密钥</el-button>
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

    <!-- 新建/编辑凭证对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="凭证名称" prop="name">
          <el-input v-model="form.name" placeholder="如：某某公司-生产环境" />
        </el-form-item>
        <el-form-item label="应用ID" prop="appId">
          <el-input v-model="form.appId" placeholder="唯一的应用标识，如：partner_prod_001">
            <template #tip>
              <span class="form-tip">应用ID是凭证的唯一标识，创建后不可修改</span>
            </template>
          </el-input>
        </el-form-item>
        <el-divider content-position="left">流量控制</el-divider>
        <el-form-item label="QPS限制" prop="rateLimitQps">
          <el-input-number v-model="form.rateLimitQps" :min="1" :max="1000" />
          <span class="form-tip">每秒最大请求数</span>
        </el-form-item>
        <el-form-item label="每日额度" prop="dailyQuota">
          <el-input-number v-model="form.dailyQuota" :min="-1" :max="999999999" />
          <span class="form-tip">-1表示无限制</span>
        </el-form-item>
        <el-form-item label="每月额度" prop="monthlyQuota">
          <el-input-number v-model="form.monthlyQuota" :min="-1" :max="999999999" />
          <span class="form-tip">-1表示无限制</span>
        </el-form-item>
        <el-divider content-position="left">有效期</el-divider>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="过期时间" prop="expireTime">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="不选择则永不过期"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密钥对话框 -->
    <el-dialog
      v-model="resetSecretDialogVisible"
      title="重置密钥"
      width="500px"
    >
      <el-alert type="warning" :closable="false" style="margin-bottom: 20px;">
        <template #title>
          <strong>重要提示</strong>
        </template>
        <template #default>
          重置后旧的密钥将立即失效，请及时更新您的应用配置。
        </template>
      </el-alert>
      <div v-if="newSecret" class="new-secret-display">
        <div class="secret-label">新的密钥</div>
        <div class="secret-value">
          <code>{{ newSecret }}</code>
          <el-button type="primary" link @click="copyKey(newSecret)">复制</el-button>
        </div>
        <el-alert type="info" :closable="false" style="margin-top: 10px;">
          请妥善保存此密钥，仅在此处显示一次！
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="resetSecretDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 凭证创建成功对话框 -->
    <el-dialog
      v-model="createSuccessDialogVisible"
      title="凭证创建成功"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-alert type="success" :closable="false" style="margin-bottom: 20px;">
        请妥善保存以下凭证信息，创建后无法再次查看完整密钥！
      </el-alert>
      <div class="credential-info">
        <div class="info-row">
          <span class="info-label">API Key:</span>
          <code class="info-value">{{ createdCredential?.apiKey }}</code>
          <el-button type="primary" link @click="copyKey(createdCredential?.apiKey)">复制</el-button>
        </div>
        <div class="info-row">
          <span class="info-label">API Secret:</span>
          <code class="info-value">{{ createdCredential?.apiSecret }}</code>
          <el-button type="primary" link @click="copyKey(createdCredential?.apiSecret)">复制</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="handleCreateSuccessConfirm">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import SearchBar from '@/components/SearchBar.vue'
import {
  getCredentialList,
  getCredentialDetail,
  createCredential,
  updateCredential,
  deleteCredential,
  updateCredentialStatus,
  resetCredentialSecret
} from '@/api/credential'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const resetSecretDialogVisible = ref(false)
const createSuccessDialogVisible = ref(false)
const formRef = ref(null)
const isEdit = ref(false)
const tableData = ref([])
const newSecret = ref('')
const createdCredential = ref(null)
const currentEditId = ref(null)

const searchFields = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '凭证名称/应用ID/API Key' },
  { prop: 'status', label: '状态', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 }
  ]}
]

const searchForm = reactive({
  keyword: '',
  status: ''
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

const dialogTitle = computed(() => isEdit.value ? '编辑凭证' : '新建凭证')

const form = reactive({
  name: '',
  appId: '',
  rateLimitQps: 10,
  dailyQuota: -1,
  monthlyQuota: -1,
  status: 1,
  expireTime: null
})

const rules = {
  name: [
    { required: true, message: '请输入凭证名称', trigger: 'blur' }
  ],
  appId: [
    { required: true, message: '请输入应用ID', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '应用ID只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  rateLimitQps: [
    { required: true, message: '请设置QPS限制', trigger: 'blur' }
  ]
}

async function fetchData() {
  loading.value = true
  try {
    const params = {
      keyword: searchForm.keyword || undefined,
      page: pagination.current,
      size: pagination.pageSize
    }
    const res = await getCredentialList(params)
    if (res.code === 200) {
      // 后端返回: { total, records, current, size, pages }
      const data = res.data || res
      tableData.value = data.records || data.content || data.list || []
      pagination.total = data.total || 0
    }
  } catch (error) {
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

function handleSearch(formData) {
  Object.assign(searchForm, formData)
  pagination.current = 1
  fetchData()
}

function handleReset() {
  Object.keys(searchForm).forEach(key => {
    searchForm[key] = key === 'status' ? '' : ''
  })
  pagination.current = 1
  fetchData()
}

function handleCreate() {
  isEdit.value = false
  currentEditId.value = null
  Object.assign(form, {
    name: '',
    appId: '',
    rateLimitQps: 10,
    dailyQuota: -1,
    monthlyQuota: -1,
    status: 1,
    expireTime: null
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  currentEditId.value = row.id
  // 深拷贝，避免双向绑定影响原数据
  Object.assign(form, {
    name: row.name,
    appId: row.appId,
    rateLimitQps: row.rateLimitQps || 10,
    dailyQuota: row.dailyQuota ?? -1,
    monthlyQuota: row.monthlyQuota ?? -1,
    status: row.status,
    expireTime: row.expireTime
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const submitData = { ...form }

      if (isEdit.value) {
        await updateCredential(currentEditId.value, submitData)
        ElMessage.success('保存成功')
      } else {
        const res = await createCredential(submitData)
        if (res.code === 200) {
          createdCredential.value = res.data
          createSuccessDialogVisible.value = true
        }
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      if (error.message) {
        ElMessage.error(error.message)
      } else {
        ElMessage.error(isEdit.value ? '保存失败' : '创建失败')
      }
    } finally {
      submitting.value = false
    }
  })
}

function handleCreateSuccessConfirm() {
  createSuccessDialogVisible.value = false
  createdCredential.value = null
}

function toggleKeyShow(row) {
  row.showKey = !row.showKey
}

function maskString(str) {
  if (!str) return ''
  if (str.length <= 8) return '****'
  return str.substring(0, 4) + '••••••••' + str.substring(str.length - 4)
}

async function copyKey(key) {
  if (!key) return
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleResetSecret(row) {
  try {
    await ElMessageBox.confirm(
      `确定要重置凭证「${row.name}」的密钥吗？重置后旧密钥将立即失效。`,
      '警告',
      {
        confirmButtonText: '确定重置',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await resetCredentialSecret(row.id)
    if (res.code === 200) {
      newSecret.value = res.data.apiSecret
      resetSecretDialogVisible.value = true
    }
  } catch {
    // 用户取消
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除凭证「${row.name}」吗？删除后无法恢复。`,
      '提示',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await deleteCredential(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchData()
    }
  } catch {
    // 用户取消
  }
}

function handleSizeChange(size) {
  pagination.pageSize = size
  fetchData()
}

function handleCurrentChange(page) {
  pagination.current = page
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.credential-management-container {
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

  .api-key-cell {
    display: flex;
    align-items: center;
    gap: 8px;

    .api-key-value {
      font-family: 'Monaco', 'Menlo', monospace;
      font-size: 12px;
      color: #409eff;
      background: #f5f7fa;
      padding: 2px 6px;
      border-radius: 4px;
    }
  }

  .quota-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 12px;

    .quota-item {
      display: flex;
      justify-content: center;
      gap: 4px;
    }

    .quota-label {
      color: #909399;
    }

    .quota-value {
      color: #303133;
      font-weight: 500;
    }
  }

  .form-tip {
    margin-left: 8px;
    font-size: 12px;
    color: #909399;
  }

  .new-secret-display {
    .secret-label {
      font-size: 14px;
      color: #606266;
      margin-bottom: 8px;
    }

    .secret-value {
      display: flex;
      align-items: center;
      gap: 10px;
      background: #f5f7fa;
      padding: 12px;
      border-radius: 4px;

      code {
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 14px;
        color: #409eff;
        word-break: break-all;
      }
    }
  }

  .credential-info {
    background: #f5f7fa;
    padding: 20px;
    border-radius: 8px;

    .info-row {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      margin-bottom: 16px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    .info-label {
      font-weight: 600;
      color: #303133;
      width: 80px;
      flex-shrink: 0;
    }

    .info-value {
      font-family: 'Monaco', 'Menlo', monospace;
      font-size: 13px;
      color: #409eff;
      word-break: break-all;
      flex: 1;
      background: #fff;
      padding: 6px 10px;
      border-radius: 4px;
      border: 1px solid #dcdfe6;
    }
  }
}

:deep(.el-dialog__body) {
  padding-top: 20px;
}
</style>
