<template>
  <div class="knowledge-item-form-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
          <span>{{ isEdit ? '编辑知识' : '添加知识' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="form-container"
      >

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="请输入知识标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="知识库" prop="baseId">
          <el-select v-model="form.baseId" placeholder="请选择知识库" style="width: 100%">
            <el-option
              v-for="item in baseList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="请输入知识内容"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="标签">
          <div class="tag-input-wrapper">
            <el-input
              v-model="tagInput"
              placeholder="输入标签后按回车添加，支持分隔符自动拆分"
              @keydown.enter.prevent="handleAddTags"
              @blur="handleAddTags"
            >
              <template #append>
                <el-button :icon="Plus" @click="handleAddTags" />
              </template>
            </el-input>
            <div class="tag-hint">
              提示：多个标签可用
              <el-tag size="small" effect="plain" style="margin: 0 4px;">中文逗号</el-tag>
              <el-tag size="small" effect="plain" style="margin: 0 4px;">英文逗号</el-tag>
              <el-tag size="small" effect="plain" style="margin: 0 4px;">分号</el-tag>
              或
              <el-tag size="small" effect="plain" style="margin: 0 4px;">空格</el-tag>
              分隔，一次性录入
            </div>
            <div class="tag-list-wrapper">
              <el-tag
                v-for="(tag, index) in form.tags"
                :key="index"
                closable
                :disable-transitions="false"
                @close="handleRemoveTag(index)"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 文档上传区域 -->
        <el-form-item label="相关文档">
          <div class="upload-area">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :file-list="fileList"
              multiple
              accept=".txt,.pdf,.doc,.docx"
            >
              <el-button type="primary" plain>
                <el-icon><Upload /></el-icon>
                选择文件
              </el-button>
              <template #tip>
                <div class="upload-tip">
                  支持 txt、pdf、doc、docx 格式，单个文件不超过10MB
                </div>
              </template>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '提交' }}
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
import { ArrowLeft, Upload, Plus } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 是否为编辑模式
const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const submitting = ref(false)
const uploadRef = ref(null)
const fileList = ref([])
const tagInput = ref('')
const tagList = ref([]) // 预留，可接入后端标签库

// 知识库列表
const baseList = ref([])

// 表单数据
const form = reactive({
  baseId: '',
  title: '',
  content: '',
  tags: [], // 标签列表（字符串数组）
  status: 1
})

// 表单验证规则
const rules = {
  baseId: [
    { required: true, message: '请选择知识库', trigger: 'change' }
  ],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 2, max: 200, message: '长度在 2 到 200 个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' }
  ]
}

// 获取详情
async function fetchDetail() {
  if (!isEdit.value) return

  try {
    // 模拟数据
    setTimeout(() => {
      Object.assign(form, {
        baseId: 1,
        title: '如何创建账号',
        content: '本文档介绍如何在平台上创建账号...',
        tags: ['入门', '账号'],
        status: 1
      })
    }, 300)
  } catch (error) {
    ElMessage.error('获取详情失败')
  }
}

// 文件变化
function handleFileChange(file, files) {
  fileList.value = files
}

// 移除文件
function handleFileRemove(file, files) {
  fileList.value = files
}

/**
 * 获取知识库列表
 */
async function fetchBaseList() {
  try {
    // const res = await axios.get('/api/kb/knowledge-base/list')
    // baseList.value = res.data

    // 模拟数据
    baseList.value = [
      { id: 1, name: '产品帮助文档' },
      { id: 2, name: '技术文档库' },
      { id: 3, name: '客服话术库' }
    ]
  } catch (error) {
    ElMessage.error('获取知识库列表失败')
  }
}

// 移除标签
function handleRemoveTag(index) {
  form.tags.splice(index, 1)
}

/**
 * 添加标签：支持中英文逗号、分号、空格作为分隔符拆分输入
 * 拆分后自动去除首尾空白，忽略空标签
 */
function handleAddTags() {
  const raw = tagInput.value.trim()
  if (!raw) return

  // 支持的分隔符：中文逗号、英文逗号、分号、空格
  const parts = raw.split(/[,，;；\s]+/).map(t => t.trim()).filter(t => t.length > 0)

  parts.forEach(tag => {
    if (!form.tags.includes(tag)) {
      form.tags.push(tag)
    }
  })
  tagInput.value = ''
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 模拟提交
      await new Promise(resolve => setTimeout(resolve, 1000))
      ElMessage.success(isEdit.value ? '保存成功' : '提交成功')
      router.push('/knowledge/item')
    } catch (error) {
      ElMessage.error(isEdit.value ? '保存失败' : '提交失败')
    } finally {
      submitting.value = false
    }
  })
}

// 返回列表
function handleBack() {
  router.push('/knowledge/item')
}

onMounted(() => {
  fetchBaseList()
  fetchDetail()
})
</script>

<style lang="scss" scoped>
.knowledge-item-form-container {
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
    max-width: 900px;
  }

  .upload-area {
    width: 100%;
  }

  .upload-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
  }

  .tag-input-wrapper {
    width: 100%;

    .tag-hint {
      font-size: 12px;
      color: #909399;
      margin: 6px 0 8px;
      line-height: 1.4;
    }

    .tag-list-wrapper {
      min-height: 36px;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .el-tag {
        font-size: 13px;
      }
    }
  }
}
</style>
