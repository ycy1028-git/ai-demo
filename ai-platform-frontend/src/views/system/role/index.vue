<template>
  <div class="role-management-container">
    <SearchBar :fields="searchFields" @search="handleSearch" @reset="handleReset" />

    <div class="table-operations">
      <el-button type="primary" :icon="Plus" @click="handleCreate">新建角色</el-button>
      <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
    </div>

    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="name" label="角色名称" width="180" />
        <el-table-column prop="code" label="角色编码" width="140" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="菜单权限" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ formatPermissions(row.menuPermissions) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="builtIn" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.builtIn ? 'danger' : 'primary'">{{ row.builtIn ? '内置' : '自定义' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :disabled="row.builtIn" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="780px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit && form.builtIn" placeholder="如 MANAGER" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单权限" prop="menuPermissions">
          <el-checkbox-group v-model="form.menuPermissions" class="permission-grid">
            <el-checkbox v-for="item in menuOptions" :key="item.code" :label="item.code">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import SearchBar from '@/components/SearchBar.vue'
import { createRole, deleteRole, getMenuPermissionOptions, getRoleList, updateRole } from '@/api/user'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const searchFields = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '角色名或编码' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '启用', value: 1 },
      { label: '禁用', value: 0 }
    ]
  }
]

const searchForm = reactive({ keyword: '', status: '' })
const tableData = ref([])
const menuOptions = ref([])

const pagination = reactive({ current: 1, pageSize: 10, total: 0 })

const form = reactive({
  id: '',
  name: '',
  code: '',
  description: '',
  status: 1,
  builtIn: false,
  menuPermissions: []
})

const dialogTitle = computed(() => (isEdit.value ? '编辑角色' : '新建角色'))

const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  menuPermissions: [{ type: 'array', required: true, message: '请至少选择一个菜单权限', trigger: 'change' }]
}

function unwrap(res) {
  return res?.data ?? res
}

async function fetchMenuOptions() {
  const res = await getMenuPermissionOptions()
  const data = unwrap(res)
  menuOptions.value = Array.isArray(data) ? data : []
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getRoleList({
      keyword: searchForm.keyword || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      page: pagination.current,
      size: pagination.pageSize
    })
    const data = unwrap(res) || {}
    const records = data.records || data.content || []
    tableData.value = records
    pagination.total = data.total || records.length
  } finally {
    loading.value = false
  }
}

function formatPermissions(codes) {
  if (!Array.isArray(codes) || codes.length === 0) return '-'
  if (codes.includes('*')) return '全部菜单'
  const map = Object.fromEntries(menuOptions.value.map(item => [item.code, item.label]))
  return codes.map(code => map[code] || code).join('、')
}

function handleSearch(values) {
  Object.assign(searchForm, values)
  pagination.current = 1
  fetchData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  pagination.current = 1
  fetchData()
}

function resetForm() {
  Object.assign(form, {
    id: '',
    name: '',
    code: '',
    description: '',
    status: 1,
    builtIn: false,
    menuPermissions: ['dashboard']
  })
}

function handleCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    description: row.description || '',
    status: row.status,
    builtIn: !!row.builtIn,
    menuPermissions: Array.isArray(row.menuPermissions) ? row.menuPermissions : []
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        name: form.name,
        code: form.code,
        description: form.description,
        status: form.status,
        menuPermissions: form.menuPermissions
      }
      if (isEdit.value) {
        await updateRole(form.id, payload)
        ElMessage.success('角色更新成功')
      } else {
        await createRole(payload)
        ElMessage.success('角色创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error?.message) {
      ElMessage.error(error.message)
    }
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

onMounted(async () => {
  await fetchMenuOptions()
  await fetchData()
})
</script>

<style scoped lang="scss">
.role-management-container {
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

  .permission-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(200px, 1fr));
    gap: 8px 16px;
  }
}
</style>
