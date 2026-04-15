<template>
  <div class="user-management-container">
    <!-- 搜索栏 -->
    <SearchBar
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作按钮 -->
    <div class="table-operations">
      <el-button type="primary" :icon="Plus" @click="handleCreate">新建用户</el-button>
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
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="realName" label="姓名" width="150" />
        <el-table-column prop="email" label="邮箱" width="200" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="roleName" label="角色" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.roleName === '系统管理员' ? 'danger' : 'primary'">
              {{ row.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="180" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleResetPwd(row)">重置密码</el-button>
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

    <!-- 新建/编辑用户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import SearchBar from '@/components/SearchBar.vue'
import { getAllRoles, getUserList, createUser, updateUser, deleteUser, resetUserPassword } from '@/api/user'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const isEdit = ref(false)
const roleOptions = ref([])

const searchFields = [
  { prop: 'username', label: '用户名', type: 'input', placeholder: '请输入用户名' },
  { prop: 'status', label: '状态', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 }
  ]}
]

const searchForm = reactive({
  username: '',
  status: ''
})

const tableData = ref([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

const dialogTitle = computed(() => isEdit.value ? '编辑用户' : '新建用户')

const form = reactive({
  username: '',
  realName: '',
  email: '',
  phone: '',
  roleId: '',
  status: 1,
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  roleId: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const roleNameMap = computed(() => {
  const map = {}
  roleOptions.value.forEach(role => {
    map[role.id] = role.name
  })
  return map
})

async function loadRoles() {
  const res = await getAllRoles()
  const data = res?.data || res
  const list = Array.isArray(data) ? data : []
  roleOptions.value = list.filter(item => item.status === 1)
}

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const params = {
      keyword: searchForm.username || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      page: pagination.current,
      size: pagination.pageSize
    }
    const res = await getUserList(params)
    if (res.code === 200) {
      // 后端返回: { total, records, current, size, pages }
      const data = res.data || res
      const list = data.records || data.content || []
      tableData.value = list.map(item => ({
        id: item.id,
        username: item.username,
        realName: item.realName || item.nickname || '',
        email: item.email || '',
        phone: item.phone || '',
        roleId: item.roleId || item.role_id || '',
        roleName: roleNameMap.value[item.roleId || item.role_id] || '未配置',
        status: item.status,
        lastLoginTime: formatDateTime(item.lastLoginTime || item.last_login_time || null),
        createTime: formatDateTime(item.createdAt || item.createTime || item.create_time)
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

function handleSearch(formData) {
  Object.assign(searchForm, formData)
  pagination.current = 1
  fetchData()
}

function handleReset() {
  Object.keys(searchForm).forEach(key => {
    searchForm[key] = ''
  })
  pagination.current = 1
  fetchData()
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    username: '',
    realName: '',
    email: '',
    phone: '',
    roleId: roleOptions.value[0]?.id || '',
    status: 1,
    password: '123456'
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    email: row.email,
    phone: row.phone,
    roleId: row.roleId,
    status: row.status,
    password: ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        await updateUser(form.id, {
          username: form.username,
          realName: form.realName,
          email: form.email,
          phone: form.phone,
          roleId: form.roleId,
          status: form.status
        })
        ElMessage.success('保存成功')
      } else {
        await createUser({
          username: form.username,
          realName: form.realName,
          email: form.email,
          phone: form.phone,
          roleId: form.roleId,
          status: form.status,
          password: form.password || '123456'
        })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error('操作失败:', error)
      ElMessage.error(isEdit.value ? '保存失败' : '创建失败')
    } finally {
      submitting.value = false
    }
  })
}

async function handleResetPwd(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码（至少6位）', `重置密码：${row.username}`, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPattern: /^.{6,}$/,
      inputErrorMessage: '密码至少6位'
    })
    await ElMessageBox.confirm(`确认将用户「${row.username}」密码重置为你输入的新密码吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await resetUserPassword(row.id, value)
    ElMessage.success('密码重置成功')
  } catch (error) {
    // 用户取消
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.username}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    console.error('删除失败:', error)
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
  loadRoles().then(fetchData)
})
</script>

<style lang="scss" scoped>
.user-management-container {
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
}
</style>
