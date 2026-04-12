<template>
  <div class="flow-editor-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <el-button @click="handleBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <span class="template-name">{{ templateName }}</span>
        <el-tag :type="templateStatus === 1 ? 'success' : 'info'" size="small">
          {{ templateStatus === 1 ? '已发布' : '未发布' }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button @click="handleSave">保存</el-button>
        <el-button type="primary" @click="handlePublish">发布</el-button>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="editor-body">
      <!-- 左侧：节点面板 -->
      <div class="node-panel">
        <div class="panel-title">节点组件</div>
        <div class="node-list">
          <div 
            v-for="category in nodeCategories" 
            :key="category.name"
            class="node-category"
          >
            <div class="category-title">{{ category.label }}</div>
            <div class="category-nodes">
              <div
                v-for="node in category.nodes"
                :key="node.code"
                class="node-item"
                draggable="true"
                @dragstart="onDragStart($event, node)"
              >
                <el-icon><component :is="node.icon || 'Document'" /></el-icon>
                <span>{{ node.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中间：画布 -->
      <div class="flow-canvas">
        <div class="canvas-empty" v-if="nodes.length === 0">
          <el-icon :size="48"><DocumentAdd /></el-icon>
          <p>从左侧拖拽节点到此处</p>
        </div>
        <div v-else class="node-sequence">
          <div 
            v-for="(node, index) in nodes" 
            :key="node.id"
            class="canvas-node"
            :class="{ 'is-selected': selectedNode?.id === node.id }"
            @click="handleSelectNode(node)"
          >
            <div class="node-header">
              <el-icon><component :is="getNodeIcon(node.type)" /></el-icon>
              <span>{{ getNodeName(node) }}</span>
            </div>
            <div class="node-body">
              <div class="node-config" v-if="node.config && Object.keys(node.config).length">
                <div v-for="(value, key) in getDisplayConfig(node.config)" :key="key" class="config-item">
                  <span class="config-key">{{ key }}:</span>
                  <span class="config-value">{{ formatConfigValue(value) }}</span>
                </div>
              </div>
              <div v-else class="node-empty">未配置</div>
            </div>
            <div class="node-actions">
              <el-button type="danger" link size="small" @click.stop="handleDeleteNode(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          
          <!-- 添加节点按钮 -->
          <div class="add-node-hint" v-if="nodes.length > 0">
            <el-icon><Plus /></el-icon>
            <span>继续添加节点</span>
          </div>
        </div>
      </div>

      <!-- 右侧：配置面板 -->
      <div class="config-panel" v-if="selectedNode">
        <div class="panel-title">
          节点配置
          <el-button type="danger" link size="small" @click="selectedNode = null">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="config-form">
          <el-form label-width="100px">
            <el-form-item label="节点类型">
              <el-tag>{{ selectedNode.type }}</el-tag>
            </el-form-item>
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.name" placeholder="请输入节点名称" />
            </el-form-item>
            <el-divider>节点配置</el-divider>
            
            <!-- 根据节点类型显示不同配置 -->
            <template v-if="selectedNode.type === 'llm_call'">
              <el-form-item label="系统提示词">
                <el-input
                  v-model="selectedNode.config.systemPrompt"
                  type="textarea"
                  :rows="4"
                  placeholder="LLM 的系统提示词"
                />
              </el-form-item>
              <el-form-item label="温度参数">
                <el-slider 
                  v-model="selectedNode.config.temperature" 
                  :min="0" 
                  :max="1" 
                  :step="0.1"
                  show-input
                />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.type === 'collect'">
              <el-form-item label="参数名">
                <el-input v-model="selectedNode.config.paramName" placeholder="参数名称" />
              </el-form-item>
              <el-form-item label="提示文本">
                <el-input
                  v-model="selectedNode.config.prompt"
                  type="textarea"
                  :rows="2"
                  placeholder="询问用户的提示文本"
                />
              </el-form-item>
              <el-form-item label="选项">
                <el-select
                  v-model="selectedNode.config.options"
                  multiple
                  placeholder="选项列表（可选）"
                  style="width: 100%"
                >
                  <el-option
                    v-for="option in selectedNode.config.options || []"
                    :key="option"
                    :label="option"
                    :value="option"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="是否必需">
                <el-switch v-model="selectedNode.config.required" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.type === 'execute'">
              <el-form-item label="执行操作">
                <el-input v-model="selectedNode.config.operation" placeholder="执行的操作名称" />
              </el-form-item>
              <el-form-item label="消息">
                <el-input
                  v-model="selectedNode.config.message"
                  type="textarea"
                  :rows="2"
                  placeholder="操作完成后的消息"
                />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.type === 'knowledge_retrieval'">
              <el-form-item label="检索策略">
                <el-select v-model="selectedNode.config.strategy" placeholder="选择检索策略">
                  <el-option label="智能判断" value="smart" />
                  <el-option label="始终检索" value="always" />
                  <el-option label="按需检索" value="on_demand" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.type === 'condition'">
              <el-form-item label="条件模式">
                <el-radio-group v-model="selectedNode.config.mode">
                  <el-radio label="preset">预设条件</el-radio>
                  <el-radio label="llm">LLM决策</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="检查字段">
                <el-input v-model="selectedNode.config.checkField" placeholder="要检查的变量名" />
              </el-form-item>
              <el-form-item label="条件分支">
                <div class="branch-list">
                  <div v-for="(branch, idx) in selectedNode.config.branches" :key="idx" class="branch-item">
                    <el-input v-model="branch.name" placeholder="分支名称" style="width: 80px" />
                    <el-input v-model="branch.condition" placeholder="条件" style="flex: 1" />
                    <el-button type="danger" link @click="removeBranch(idx)"><el-icon><Delete /></el-icon></el-button>
                  </div>
                  <el-button type="primary" link @click="addBranch">
                    <el-icon><Plus /></el-icon> 添加分支
                  </el-button>
                </div>
              </el-form-item>
            </template>

            <template v-else>
              <el-form-item label="配置信息">
                <el-input
                  v-model="configJson"
                  type="textarea"
                  :rows="4"
                  placeholder="自定义配置（JSON格式）"
                  @blur="updateConfig"
                />
              </el-form-item>
            </template>

            <el-form-item>
              <el-button type="primary" @click="saveNodeConfig">保存配置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- 右侧：空状态 -->
      <div class="config-panel empty" v-else>
        <div class="empty-tip">
          <el-icon :size="48"><Setting /></el-icon>
          <p>点击节点进行配置</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  ArrowLeft, DocumentAdd, Delete, Plus, Close, Setting,
  ChatDotRound, Collection, DataAnalysis, Document, Message, Operation, Search
} from '@element-plus/icons-vue'
import { getFlowTemplateById, updateFlowTemplate, publishFlowTemplate } from '@/api/flow'

const router = useRouter()
const route = useRoute()

const templateId = route.params.id
const templateName = ref('')
const templateStatus = ref(0)
const nodes = ref([])
const selectedNode = ref(null)

const configJson = computed({
  get: () => selectedNode.value?.config ? JSON.stringify(selectedNode.value.config, null, 2) : '{}',
  set: (val) => {
    if (selectedNode.value) {
      try {
        selectedNode.value.config = JSON.parse(val)
      } catch (e) {
        // ignore
      }
    }
  }
})

// 预定义的节点分类
const nodeCategories = ref([
  {
    name: 'foundation',
    label: '基础节点',
    nodes: [
      { code: 'start', name: '开始', type: 'start', icon: 'CaretTop' },
      { code: 'end', name: '结束', type: 'end', icon: 'CircleClose' }
    ]
  },
  {
    name: 'ai',
    label: 'AI 能力',
    nodes: [
      { code: 'llm_call', name: 'LLM调用', type: 'llm_call', icon: 'ChatDotRound' },
      { code: 'knowledge_retrieval', name: '知识检索', type: 'knowledge_retrieval', icon: 'Search' }
    ]
  },
  {
    name: 'execute',
    label: '业务执行',
    nodes: [
      { code: 'collect', name: '参数收集', type: 'collect', icon: 'Edit' },
      { code: 'execute', name: '业务执行', type: 'execute', icon: 'Operation' }
    ]
  },
  {
    name: 'logic',
    label: '逻辑控制',
    nodes: [
      { code: 'condition', name: '条件分支', type: 'condition', icon: 'DataAnalysis' },
      { code: 'echo', name: '消息回复', type: 'echo', icon: 'Message' }
    ]
  }
])

function getNodeIcon(type) {
  const iconMap = {
    'start': 'CaretTop',
    'end': 'CircleClose',
    'llm_call': 'ChatDotRound',
    'knowledge_retrieval': 'Search',
    'collect': 'Edit',
    'execute': 'Operation',
    'condition': 'DataAnalysis',
    'echo': 'Message'
  }
  return iconMap[type] || 'Document'
}

function getNodeName(node) {
  if (node.name) return node.name
  const category = nodeCategories.value.find(c => 
    c.nodes.some(n => n.type === node.type)
  )
  const def = category?.nodes.find(n => n.type === node.type)
  return def?.name || node.type
}

function getDisplayConfig(config) {
  const displayKeys = ['systemPrompt', 'operation', 'prompt', 'paramName', 'strategy', 'message', 'checkField']
  const result = {}
  for (const key of displayKeys) {
    if (config[key] !== undefined) {
      result[key] = config[key]
    }
  }
  return result
}

function formatConfigValue(value) {
  if (typeof value === 'object') {
    return JSON.stringify(value).substring(0, 50) + '...'
  }
  return String(value).substring(0, 50)
}

function updateConfig() {
  try {
    if (selectedNode.value) {
      selectedNode.value.config = JSON.parse(configJson.value)
    }
  } catch (e) {
    // ignore
  }
}

function addBranch() {
  if (!selectedNode.value.config.branches) {
    selectedNode.value.config.branches = []
  }
  selectedNode.value.config.branches.push({ name: '', condition: '', targetNode: '' })
}

function removeBranch(index) {
  selectedNode.value.config.branches.splice(index, 1)
}

function handleSelectNode(node) {
  selectedNode.value = node
}

function handleDeleteNode(index) {
  nodes.value.splice(index, 1)
  if (selectedNode.value && !nodes.value.includes(selectedNode.value)) {
    selectedNode.value = null
  }
}

function onDragStart(event, node) {
  event.dataTransfer.setData('nodeType', node.code)
  event.dataTransfer.setData('nodeDef', JSON.stringify(node))
}

function handleSave() {
  const flowData = JSON.stringify({ nodes: nodes.value })
  updateFlowTemplate({
    id: templateId,
    templateName: templateName.value,
    flowData: flowData
  }).then(() => {
    ElMessage.success('保存成功')
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

function handlePublish() {
  ElMessageBox.confirm('确定要发布该模板吗？发布后将启用该模板。', '提示', { type: 'warning' })
    .then(() => {
      return publishFlowTemplate(templateId)
    })
    .then(() => {
      ElMessage.success('发布成功')
      templateStatus.value = 1
    })
    .catch(() => {})
}

function handleBack() {
  router.push('/flow/template')
}

onMounted(async () => {
  // 加载模板数据
  if (templateId) {
    try {
      const res = await getFlowTemplateById(templateId)
      if (res.data) {
        templateName.value = res.data.templateName
        templateStatus.value = res.data.status
        const flowData = JSON.parse(res.data.flowData || '{}')
        nodes.value = flowData.nodes || []
      }
    } catch (error) {
      console.error('加载模板数据失败', error)
      ElMessage.error('加载模板数据失败')
    }
  }
})
</script>

<style lang="scss" scoped>
.flow-editor-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .template-name {
      font-size: 16px;
      font-weight: 600;
    }
  }
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.node-panel {
  width: 240px;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
  overflow-y: auto;

  .panel-title {
    padding: 12px 16px;
    font-weight: 600;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .node-list {
    padding: 12px;
  }

  .node-category {
    margin-bottom: 16px;

    .category-title {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-bottom: 8px;
    }

    .category-nodes {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .node-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: var(--el-fill-color-light);
      border-radius: 4px;
      cursor: move;
      font-size: 13px;
      transition: all 0.2s;

      &:hover {
        background: var(--el-fill-color);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }
    }
  }
}

.flow-canvas {
  flex: 1;
  background: var(--el-fill-color-lighter);
  overflow-y: auto;
  padding: 24px;

  .canvas-empty {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    color: var(--el-text-color-secondary);

    .el-icon {
      margin-bottom: 16px;
    }
  }

  .node-sequence {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
  }

  .canvas-node {
    width: 400px;
    background: var(--el-bg-color);
    border: 2px solid var(--el-border-color);
    border-radius: 8px;
    overflow: hidden;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: var(--el-color-primary-light-5);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    &.is-selected {
      border-color: var(--el-color-primary);
      box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
    }

    .node-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: var(--el-fill-color-light);
      border-bottom: 1px solid var(--el-border-color-light);
      font-weight: 500;
    }

    .node-body {
      padding: 12px 16px;

      .node-config {
        .config-item {
          font-size: 13px;
          margin-bottom: 4px;

          .config-key {
            color: var(--el-text-color-secondary);
          }
        }
      }

      .node-empty {
        color: var(--el-text-color-secondary);
        font-size: 13px;
      }
    }

    .node-actions {
      display: flex;
      justify-content: flex-end;
      padding: 4px 8px;
      border-top: 1px solid var(--el-border-color-light);
    }
  }

  .add-node-hint {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 24px;
    color: var(--el-text-color-secondary);
    border: 2px dashed var(--el-border-color);
    border-radius: 8px;
  }
}

.config-panel {
  width: 320px;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color-light);
  overflow-y: auto;

  &.empty {
    display: flex;
    justify-content: center;
    align-items: center;

    .empty-tip {
      text-align: center;
      color: var(--el-text-color-secondary);
    }
  }

  .panel-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    font-weight: 600;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .config-form {
    padding: 16px;
  }

  .branch-list {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .branch-item {
      display: flex;
      gap: 8px;
      align-items: center;
    }
  }
}

:deep(.el-divider) {
  margin: 16px 0;
}
</style>
