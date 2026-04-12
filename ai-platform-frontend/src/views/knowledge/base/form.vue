<template>
  <div class="knowledge-base-form-container">
    <el-card>
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
          >
            <template #prefix>kb_</template>
          </el-input>
          <div class="form-tip">唯一标识，用于API调用，建议使用英文</div>
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
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 是否为编辑模式
const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const submitting = ref(false)

// 表单数据
const form = reactive({
  name: '',
  code: '',
  description: '',
  status: 1,
  sceneDescription: '',
  priority: 0
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入知识库编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '编码必须以小写字母开头，只包含小写字母、数字和下划线', trigger: 'blur' }
  ],
  esIndex: [
    { required: true, message: '请输入ES索引名称', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '索引名称必须以小写字母开头，只包含小写字母、数字和下划线', trigger: 'blur' }
  ]
}

// 获取详情
async function fetchDetail() {
  if (!isEdit.value) return

  try {
    // 实际项目中调用API
    // const res = await getKnowledgeBaseDetail(route.params.id)
    // Object.assign(form, res.data)

      // 模拟数据
      setTimeout(() => {
        Object.assign(form, {
          name: '产品帮助文档',
          code: 'product',
          description: '产品使用指南和常见问题解答',
          status: 1,
          sceneDescription: '处理用户账号注册、登录、密码找回等账号相关问题',
          priority: 10
        })
      }, 300)
  } catch (error) {
    ElMessage.error('获取详情失败')
  }
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 自动添加 kb_ 前缀
      const submitData = {
        ...form,
        esIndex: form.esIndex.startsWith('kb_') ? form.esIndex : 'kb_' + form.esIndex,
        code: form.code.startsWith('kb_') ? form.code.substring(3) : form.code
      }

      if (isEdit.value) {
        // 实际项目中调用API
        // await updateKnowledgeBase(route.params.id, submitData)
        ElMessage.success('保存成功')
      } else {
        // 实际项目中调用API
        // await createKnowledgeBase(submitData)
        ElMessage.success('创建成功')
      }
      router.push('/knowledge/base')
    } catch (error) {
      ElMessage.error(isEdit.value ? '保存失败' : '创建失败')
    } finally {
      submitting.value = false
    }
  })
}

// 返回列表
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
