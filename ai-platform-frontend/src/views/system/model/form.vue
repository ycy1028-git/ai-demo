<template>
  <div class="model-config-form-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
          <span>{{ isEdit ? '编辑模型配置' : '创建模型配置' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        class="form-container"
      >
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="模型名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入模型名称，如：DeepSeek Chat"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="提供商" prop="provider">
          <el-select
            v-model="form.provider"
            placeholder="请选择模型提供商"
            @change="handleProviderChange"
          >
            <el-option label="通义千问 (Qwen)" value="qwen" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="智谱GLM (Zhipu)" value="zhipu" />
            <el-option label="OpenAI" value="openai" />
          </el-select>
        </el-form-item>

        <el-form-item label="模型标识" prop="modelName">
          <el-input
            v-model="form.modelName"
            :placeholder="modelNamePlaceholder"
            maxlength="100"
            show-word-limit
          >
            <template #prefix>
              <el-icon><Cpu /></el-icon>
            </template>
          </el-input>
          <div class="form-tip">
            输入模型的标识符，如：
            <el-tag size="small" effect="plain" style="margin: 0 2px;">deepseek-chat</el-tag>
            <el-tag size="small" effect="plain" style="margin: 0 2px;">qwen-plus</el-tag>
            <el-tag size="small" effect="plain" style="margin: 0 2px;">glm-4</el-tag>
            <el-tag size="small" effect="plain" style="margin: 0 2px;">gpt-4o-mini</el-tag>
          </div>
        </el-form-item>

        <el-form-item label="Embedding模型" prop="embeddingModelName">
          <el-input
            v-model="form.embeddingModelName"
            :placeholder="embeddingModelNamePlaceholder"
            maxlength="100"
            show-word-limit
          >
            <template #prefix>
              <el-icon><Cpu /></el-icon>
            </template>
          </el-input>
          <div class="form-tip">
            如果该配置用于向量化，请填写 Embedding 模型标识，如：
            <el-tag size="small" effect="plain" style="margin: 0 2px;">text-embedding-3-large</el-tag>
            <el-tag size="small" effect="plain" style="margin: 0 2px;">text-embedding-3-small</el-tag>
            <el-tag size="small" effect="plain" style="margin: 0 2px;">text-embedding-v2</el-tag>
          </div>
        </el-form-item>

        <el-form-item label="模型描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入模型描述，用于说明该模型的适用场景"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-divider content-position="left">API配置</el-divider>

        <el-form-item label="API地址" prop="apiUrl">
          <el-input
            v-model="form.apiUrl"
            :placeholder="apiUrlPlaceholder"
            maxlength="500"
            show-word-limit
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-input>
          <div class="form-tip">
            留空将使用提供商的默认地址。如果使用代理或自定义端点，请填写完整URL
          </div>
        </el-form-item>

        <el-form-item label="Embedding接口" prop="embeddingApiUrl">
          <el-input
            v-model="form.embeddingApiUrl"
            :placeholder="embeddingApiUrlPlaceholder"
            maxlength="500"
            show-word-limit
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-input>
          <div class="form-tip">
            向量化接口可与对话接口不同，留空将根据上方 API 地址自动补齐 <code>/embeddings</code>
          </div>
        </el-form-item>

        <el-form-item label="API密钥" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            :placeholder="isEdit ? '留空则保持原密钥' : '请输入API密钥'"
            maxlength="255"
            show-password
            show-word-limit
          >
            <template #prefix>
              <el-icon><Key /></el-icon>
            </template>
          </el-input>
          <div class="form-tip">
            API密钥将加密存储，请确保输入正确
          </div>
        </el-form-item>

        <el-divider content-position="left">模型参数</el-divider>

        <el-form-item label="温度参数">
          <div class="slider-wrapper">
            <el-slider
              v-model="form.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :show-tooltip="true"
              :format-tooltip="val => val.toFixed(2)"
              style="flex: 1;"
            />
            <el-input-number
              v-model="form.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              size="small"
              style="width: 90px; margin-left: 16px;"
            />
          </div>
          <div class="form-tip">
            控制输出的随机性。较低值（如0.2）产生更确定性的回答；较高值（如1.0）产生更随机、创造性的回答
          </div>
        </el-form-item>

        <el-form-item label="最大Token数">
          <el-input-number
            v-model="form.maxTokens"
            :min="1"
            :max="128000"
            :step="100"
          />
          <div class="form-tip">
            单次请求允许的最大Token数（包括输入和输出）。建议根据实际需求设置，过大可能增加响应时间和成本
          </div>
        </el-form-item>

        <el-divider content-position="left">其他配置</el-divider>

        <el-form-item label="排序权重">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="999"
            :step="1"
          />
          <div class="form-tip">
            数值越小排序越靠前，用于在列表中调整模型的显示顺序
          </div>
        </el-form-item>

        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio :label="true">启用</el-radio>
            <el-radio :label="false">禁用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="设为默认">
          <el-switch
            v-model="form.isDefault"
            active-text="是"
            inactive-text="否"
          />
          <div class="form-tip">
            默认模型将用于没有指定模型的场景，如知识库未配置模型时的默认调用
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
          <el-button @click="handleBack">取消</el-button>
          <el-button v-if="isEdit" type="warning" :loading="testing" @click="handleTest">
            测试连接
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Cpu, Link, Key } from '@element-plus/icons-vue'
import { getModelConfigById, createModelConfig, updateModelConfig, testModelConnection } from '@/api/modelConfig'

const route = useRoute()
const router = useRouter()

// 是否为编辑模式
const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const submitting = ref(false)
const testing = ref(false)

// 表单数据
const form = reactive({
  name: '',
  provider: '',
  apiUrl: '',
  apiKey: '',
  modelName: '',
  embeddingApiUrl: '',
  embeddingModelName: '',
  temperature: 0.7,
  maxTokens: 2000,
  enabled: true,
  isDefault: false,
  sortOrder: 0,
  description: ''
})

// 根据提供商显示不同的提示
const providerConfigs = {
  qwen: {
    modelNamePlaceholder: '如：qwen-plus、qwen-turbo、qwen-max',
    apiUrlPlaceholder: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    embeddingModelPlaceholder: '如：text-embedding-v2',
    embeddingApiUrlPlaceholder: 'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings'
  },
  deepseek: {
    modelNamePlaceholder: '如：deepseek-chat、deepseek-coder',
    apiUrlPlaceholder: 'https://api.deepseek.com/v1',
    embeddingModelPlaceholder: '如：deepseek-embedding',
    embeddingApiUrlPlaceholder: 'https://api.deepseek.com/v1/embeddings'
  },
  zhipu: {
    modelNamePlaceholder: '如：glm-4、glm-4-flash、glm-4-plus',
    apiUrlPlaceholder: 'https://open.bigmodel.cn/api/paas/v4',
    embeddingModelPlaceholder: '如：embedding-2、text-embedding',
    embeddingApiUrlPlaceholder: 'https://open.bigmodel.cn/api/paas/v4/embeddings'
  },
  openai: {
    modelNamePlaceholder: '如：gpt-4o-mini、gpt-4o、gpt-3.5-turbo',
    apiUrlPlaceholder: 'https://api.openai.com/v1',
    embeddingModelPlaceholder: '如：text-embedding-3-large、text-embedding-3-small',
    embeddingApiUrlPlaceholder: 'https://api.openai.com/v1/embeddings'
  }
}

const modelNamePlaceholder = computed(() => {
  return providerConfigs[form.provider]?.modelNamePlaceholder || '请先选择提供商'
})

const apiUrlPlaceholder = computed(() => {
  return providerConfigs[form.provider]?.apiUrlPlaceholder || '留空使用默认地址'
})

const embeddingModelNamePlaceholder = computed(() => {
  return providerConfigs[form.provider]?.embeddingModelPlaceholder || '请输入Embedding模型标识，如：text-embedding-3-large'
})

const embeddingApiUrlPlaceholder = computed(() => {
  return providerConfigs[form.provider]?.embeddingApiUrlPlaceholder || '留空使用默认Embeddings地址'
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入模型名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  provider: [
    { required: true, message: '请选择提供商', trigger: 'change' }
  ],
  modelName: [
    { required: true, message: '请输入模型标识', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  apiKey: [
    { required: true, message: '请输入API密钥', trigger: 'blur' }
  ]
}

// 提供商变化时清空API地址，让用户重新输入或使用默认值
function handleProviderChange() {
  form.apiUrl = ''
  form.embeddingApiUrl = ''
}

// 获取详情
async function fetchDetail() {
  if (!isEdit.value) return

  try {
    const res = await getModelConfigById(route.params.id)
    const data = res.data || res
      Object.assign(form, {
        name: data.name,
        provider: data.provider,
        apiUrl: data.apiUrl || '',
        embeddingApiUrl: data.embeddingApiUrl || '',
        modelName: data.modelName,
      embeddingModelName: data.embeddingModelName || '',
      temperature: data.temperature || 0.7,
      maxTokens: data.maxTokens || 2000,
      enabled: data.enabled,
      isDefault: data.isDefault,
      sortOrder: data.sortOrder || 0,
      description: data.description || '',
      apiKey: ''  // 编辑时不清空密钥
    })
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
      const submitData = { ...form }

      // 如果是编辑且密钥为空，移除密钥字段（保持原密钥）
      if (isEdit.value && !submitData.apiKey) {
        delete submitData.apiKey
      }

      if (isEdit.value) {
        await updateModelConfig(route.params.id, submitData)
        ElMessage.success('保存成功')
      } else {
        await createModelConfig(submitData)
        ElMessage.success('创建成功')
      }
      router.push('/system/model')
    } catch (error) {
      ElMessage.error(isEdit.value ? '保存失败' : '创建失败')
    } finally {
      submitting.value = false
    }
  })
}

// 返回列表
function handleBack() {
  router.push('/system/model')
}

// 测试连接
async function handleTest() {
  if (!route.params.id) {
    ElMessage.warning('请先保存后再测试连接')
    return
  }
  testing.value = true
  try {
    ElMessage.info('正在测试连接...')
    const res = await testModelConnection(route.params.id)
    ElMessage.success(res.data || '连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败，请检查配置')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style lang="scss" scoped>
.model-config-form-container {
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
    line-height: 1.5;
  }

  .slider-wrapper {
    display: flex;
    align-items: center;
    width: 100%;
  }

  :deep(.el-divider--horizontal) {
    margin: 24px 0 16px;
  }
}
</style>
