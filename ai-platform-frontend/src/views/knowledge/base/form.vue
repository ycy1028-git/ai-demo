<template>
  <div class="knowledge-base-form-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
          <span>{{ isEdit ? '编辑知识库' : '创建知识库' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
        class="form-container"
      >
        <el-form-item label="知识库名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入知识库名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="知识库编码" prop="code">
          <el-input
            v-model="form.code"
            placeholder="请输入知识库编码（唯一标识）"
            maxlength="50"
            show-word-limit
            :disabled="isEdit"
          />
          <div class="form-tip">唯一标识，用于API调用，建议使用英文</div>
        </el-form-item>

        <el-form-item label="文本索引名" prop="esIndex">
          <el-input
            v-model="form.esIndex"
            placeholder="留空则自动使用默认索引"
            maxlength="64"
            show-word-limit
          />
          <div class="form-tip">可指定现有 ES 索引名称，必须是 3-64 位小写字母、数字、- 或 _</div>
        </el-form-item>

        <el-form-item label="向量索引名" prop="vectorIndex">
          <el-input
            v-model="form.vectorIndex"
            placeholder="留空默认与文本索引一致"
            maxlength="64"
            show-word-limit
          />
          <div class="form-tip">可指向独立的向量索引，用于 kNN 检索</div>
        </el-form-item>

        <el-form-item label="MinIO 桶" prop="bucketName">
          <el-input
            v-model="form.bucketName"
            placeholder="留空使用平台默认桶，支持自定义"
            maxlength="63"
            show-word-limit
          />
          <div class="form-tip">支持 3-63 位小写字母、数字、点、短横线，创建时将自动初始化该桶</div>
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="业务场景">
          <el-input
            v-model="form.sceneDescription"
            type="textarea"
            :rows="3"
            placeholder="请输入业务场景描述，帮助AI理解知识库的业务范围，如：处理用户账号注册、登录、密码找回等账号相关问题"
            maxlength="500"
            show-word-limit
          />
          <div class="form-tip">描述知识库适用的业务场景，便于流程节点关联匹配</div>
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-input-number
            v-model="form.priority"
            :min="0"
            :max="100"
            :step="1"
          />
          <div class="form-tip">数值越大优先级越高，用于多个知识库匹配时的排序</div>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
          <el-button @click="handleBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getKnowledgeBaseDetail, createKnowledgeBase, updateKnowledgeBase } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const submitting = ref(false)
const loading = ref(false)

const form = reactive({
  name: '',
  code: '',
  description: '',
  status: 1,
  sceneDescription: '',
  priority: 0,
  esIndex: '',
  vectorIndex: '',
  bucketName: ''
})

const indexPattern = /^[a-z0-9][a-z0-9_-]{2,63}$/
const bucketPattern = /^[a-z0-9](?:[a-z0-9.-]{1,61}[a-z0-9])$/

const createOptionalValidator = (pattern, message) => {
  return (_, value, callback) => {
    if (!value || !value.trim()) {
      callback()
      return
    }
    if (pattern.test(value.trim())) {
      callback()
      return
    }
    callback(new Error(message))
  }
}

const rules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入知识库编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,48}$/, message: '编码需以字母开头，长度 3-50，仅含字母、数字、下划线', trigger: 'blur' }
  ],
  esIndex: [
    { validator: createOptionalValidator(indexPattern, '索引名需为 3-64 位小写字母、数字、- 或 _'), trigger: 'blur' }
  ],
  vectorIndex: [
    { validator: createOptionalValidator(indexPattern, '索引名需为 3-64 位小写字母、数字、- 或 _'), trigger: 'blur' }
  ],
  bucketName: [
    { validator: createOptionalValidator(bucketPattern, '桶名需为 3-63 位小写字母、数字、点或短横线，并以字母或数字结尾'), trigger: 'blur' }
  ]
}

const unwrap = (res) => res?.data ?? res

async function fetchDetail() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getKnowledgeBaseDetail(route.params.id)
    const data = unwrap(res) || {}
    Object.assign(form, {
      name: data.name || '',
      code: data.code || '',
      description: data.description || '',
      status: data.status ?? 1,
      sceneDescription: data.sceneDescription || '',
      priority: data.priority ?? 0,
      esIndex: data.esIndex || '',
      vectorIndex: data.vectorIndex || '',
      bucketName: data.bucketName || ''
    })
  } catch (error) {
    ElMessage.error('获取详情失败')
  } finally {
    loading.value = false
  }
}

const sanitizeOptional = (value) => {
  if (value === null || value === undefined) {
    return undefined
  }
  if (typeof value !== 'string') {
    return value
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
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
      name: form.name.trim(),
      code: form.code.trim(),
      description: form.description,
      status: form.status,
      sceneDescription: form.sceneDescription,
      priority: form.priority,
      esIndex: sanitizeOptional(form.esIndex),
      vectorIndex: sanitizeOptional(form.vectorIndex),
      bucketName: sanitizeOptional(form.bucketName)
    }

    if (isEdit.value) {
      await updateKnowledgeBase(route.params.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createKnowledgeBase(payload)
      ElMessage.success('创建成功')
    }
    router.push('/knowledge/base')
  } catch (error) {
    ElMessage.error(isEdit.value ? '保存失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

function handleBack() {
  router.push('/knowledge/base')
}

onMounted(() => {
  fetchDetail()
})
</script>

<style lang="scss" scoped>
.knowledge-base-form-container {
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
    max-width: 800px;
  }

  .form-tip {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
