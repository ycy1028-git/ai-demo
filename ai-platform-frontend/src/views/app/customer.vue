<template>
  <div class="customer-container">
    <div class="chat-wrapper">
      <!-- 聊天主区域 -->
      <div class="chat-main">
        <!-- 顶部工具栏 -->
        <div class="chat-toolbar">
          <div class="toolbar-left">
            <h2>智能助手</h2>
          </div>
          <div class="toolbar-right">
            <el-button @click="clearChat" :loading="clearingContext">
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
            <el-button type="primary" @click="refreshSession">
              <el-icon><Refresh /></el-icon>
              新会话
            </el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesRef" class="messages-container">
          <!-- 欢迎消息 -->
          <div v-if="messages.length === 0" class="welcome-message">
            <div class="welcome-icon">
              <svg width="100" height="100" viewBox="0 0 100 100" fill="none">
                <circle cx="50" cy="50" r="45" fill="url(#welcomeGrad)" opacity="0.15"/>
                <circle cx="50" cy="50" r="35" fill="url(#welcomeGrad)" opacity="0.25"/>
                <circle cx="50" cy="50" r="25" fill="url(#welcomeGrad)" opacity="0.4"/>
                <circle cx="50" cy="50" r="15" fill="url(#welcomeGrad)"/>
                <path d="M50 35 L50 45 M50 55 L50 65 M38 50 L45 50 M55 50 L62 50" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
                <defs>
                  <linearGradient id="welcomeGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#6366f1"/>
                    <stop offset="100%" stop-color="#06b6d4"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <h3>您好，我是AI智能助手</h3>
            <p>我可以帮您解答问题、提供信息和建议。有什么可以帮您的吗？</p>

            <div class="quick-questions">
              <p>试试这样问：</p>
              <div class="question-tags">
                <el-tag
                  v-for="q in quickQuestions"
                  :key="q.text"
                  class="question-tag"
                  @click="askQuestion(q.text)"
                >
                  <el-icon v-if="q.icon"><component :is="q.icon" /></el-icon>
                  {{ q.text }}
                </el-tag>
              </div>
            </div>

            <!-- 功能说明 -->
            <div class="feature-cards">
              <div class="feature-card" v-for="card in featureCards" :key="card.text">
                <el-icon :size="24" :color="card.color"><component :is="card.icon" /></el-icon>
                <span>{{ card.text }}</span>
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['message-item', msg.role]"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'" :size="20"><User /></el-icon>
              <el-icon v-else :size="20"><Service /></el-icon>
            </div>

            <div class="message-body">
              <div class="message-header">
                <span class="sender-name">{{ msg.role === 'user' ? '我' : 'AI助手' }}</span>
                <span v-if="msg.model" class="model-tag">{{ msg.model }}</span>
              </div>

              <div class="message-content">
                <div v-if="msg.loading && !msg.content" class="thinking-dots">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div v-else v-html="formatMessage(msg.content)"></div>
              </div>

              <!-- 参考来源 -->
              <div v-if="msg.citations && msg.citations.length > 0" class="message-citations">
                <div class="citations-header">
                  <el-icon><Document /></el-icon>
                  <span>参考来源 ({{ msg.citations.length }})</span>
                </div>
                <div class="citations-list">
                  <el-tag
                    v-for="cite in msg.citations"
                    :key="cite.id"
                    size="small"
                    class="citation-tag"
                    @click="viewCitation(cite)"
                  >
                    <el-icon><Link /></el-icon>
                    {{ cite.title }}
                  </el-tag>
                </div>
              </div>

              <!-- 消息时间 -->
              <div v-if="msg.createTime" class="message-time">
                {{ formatTime(msg.createTime) }}
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <div class="input-container">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              :autosize="{ minRows: 1, maxRows: 5 }"
              placeholder="请输入您的问题，按 Enter 发送，Shift+Enter 换行..."
              @keydown.enter.exact.prevent="handleSend($event)"
              @keydown.enter.shift.exact.prevent="handleNewline($event)"
              @compositionstart="handleCompositionStart"
              @compositionend="handleCompositionEnd"
              resize="none"
            />
            <div class="input-actions">
              <span class="input-hint">
                <el-icon><InfoFilled /></el-icon>
                Enter 发送 | Shift+Enter 换行
              </span>
              <div class="input-buttons">
                <el-button
                  v-if="isGenerating"
                  type="danger"
                  @click="stopGeneration"
                >
                  <el-icon><VideoPause /></el-icon>
                  停止
                </el-button>
                <el-button
                  type="primary"
                  :loading="sending"
                  :disabled="!inputMessage.trim()"
                  @click="handleSend()"
                >
                  <el-icon v-if="!sending"><Promotion /></el-icon>
                  {{ sending ? '生成中...' : '发送' }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 知识详情抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      title="知识详情"
      :size="dialogWidth"
      direction="rtl"
    >
          <div v-if="currentCitation" class="detail-content">
        <div class="detail-header">
          <el-tag type="primary" effect="dark">{{ currentCitation.kbName }}</el-tag>
          <h2>{{ currentCitation.title }}</h2>
        </div>

        <div class="detail-meta">
          <span v-if="currentCitation.sourceType">
            <el-icon><Document /></el-icon>
            来源：{{ currentCitation.sourceType === 'manual' ? '手动录入' : '文档上传' }}
          </span>
          <span v-if="currentCitation.originalFileName">
            <el-icon><Files /></el-icon>
            {{ currentCitation.originalFileName }}
          </span>
        </div>

        <el-divider />

        <div class="detail-body">
          <div class="content-text" v-html="formatDetailContent(currentCitation.content)"></div>
        </div>

        <div class="detail-actions">
          <el-button
            v-if="currentCitation.sourceDocId"
            type="primary"
            @click="previewDocument(currentCitation)"
          >
            <el-icon><View /></el-icon>
            文档预览
          </el-button>
          <el-button @click="copyContent(currentCitation.content)">
            <el-icon><CopyDocument /></el-icon>
            复制内容
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 文档预览对话框 -->
    <el-dialog
      v-model="previewDialogVisible"
      title="文档预览"
      :width="dialogWidth"
      destroy-on-close
    >
      <div class="preview-container">
        <iframe
          v-if="previewUrl"
          :src="previewUrl"
          class="preview-iframe"
        ></iframe>
        <div v-else class="preview-loading">
          <el-skeleton :rows="10" animated />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import {
  User, Service, Delete, Refresh, Document, Files, View, CopyDocument,
  Promotion, InfoFilled, ChatDotRound, Link, QuestionFilled, VideoPause
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { handleAuthError } from '@/api/request'
import request from '@/utils/request'

// 状态
const inputMessage = ref('')
const sending = ref(false)
const isGenerating = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const sessionId = ref(createSessionId())
const clearingContext = ref(false)
let abortController = null
let messageIdSeed = 0
const isComposing = ref(false)

function createSessionId() {
  return `sess_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

function createMessageId() {
  messageIdSeed += 1
  return `msg_${Date.now()}_${messageIdSeed}`
}

// 功能卡片
const featureCards = [
  { icon: Service, text: '智能问答', color: '#6366f1' },
  { icon: ChatDotRound, text: '多轮对话', color: '#10b981' },
  { icon: Link, text: '知识引用', color: '#f59e0b' }
]

// 详情抽屉
const detailDrawerVisible = ref(false)
const currentCitation = ref(null)

// 预览
const previewDialogVisible = ref(false)
const previewUrl = ref('')

// 快捷问题
const quickQuestions = [
  { text: '您好，请问有什么可以帮您？', icon: QuestionFilled },
  { text: '请介绍一下系统功能', icon: Service },
  { text: '如何开始使用？', icon: QuestionFilled }
]

// 计算对话框宽度
const dialogWidth = computed(() => {
  return window.innerWidth > 1200 ? '800px' : '90%'
})

async function clearServerContext(currentSessionId) {
  if (!currentSessionId) {
    return
  }

  await request.delete('/flow/chat/context', {
    params: { sessionId: currentSessionId }
  })
}

// 刷新会话
async function refreshSession() {
  stopGeneration({ silent: true })
  const oldSessionId = sessionId.value
  try {
    await clearServerContext(oldSessionId)
  } catch (error) {
    console.warn('清理旧会话上下文失败:', error)
  }
  messages.value = []
  sessionId.value = createSessionId()
}

// 清空对话
async function clearChat() {
  if (clearingContext.value) return
  stopGeneration({ silent: true })

  const currentSessionId = sessionId.value
  clearingContext.value = true
  try {
    await clearServerContext(currentSessionId)
    messages.value = []
    sessionId.value = createSessionId()
    ElMessage.success('已清空对话上下文')
  } catch (error) {
    console.error('清空上下文失败:', error)
    ElMessage.error(error?.message || '清空上下文失败，请稍后重试')
  } finally {
    clearingContext.value = false
  }
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 停止生成
function stopGeneration(options = {}) {
  const { silent = false } = options
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  isGenerating.value = false
  sending.value = false
  if (!silent) {
    ElMessage.info('已停止生成')
  }
}

// 发送消息
async function handleSend(event) {
  if ((event && event.isComposing) || isComposing.value) {
    return
  }
  if (!inputMessage.value.trim()) {
    ElMessage.warning('请输入内容')
    return
  }

  if (sending.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  sending.value = true
  isGenerating.value = true

  // 添加用户消息
  messages.value.push({
    id: createMessageId(),
    role: 'user',
    content: userMessage,
    createTime: new Date().toISOString()
  })
  scrollToBottom()

  // 添加助手消息占位
  const assistantIndex = messages.value.length
  messages.value.push({
    id: createMessageId(),
    role: 'assistant',
    content: '',
    loading: true,
    citations: [],
    model: 'AI',
    createTime: new Date().toISOString()
  })
  scrollToBottom()

  try {
    // 创建 AbortController 用于停止
    abortController = new AbortController()

    // 流式模式：调用真实 API
    const token = localStorage.getItem('token') || ''
    if (!token) {
      handleAuthError('请先登录')
      return
    }
    const params = new URLSearchParams()
    params.append('message', userMessage)
    if (sessionId.value) {
      params.append('sessionId', sessionId.value)
    }

    const response = await fetch(`/api/flow/chat/stream?${params.toString()}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'text/event-stream'
      },
      signal: abortController.signal
    })

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        handleAuthError('登录已过期，请重新登录')
        return
      }
      throw new Error('请求失败')
    }

    messages.value[assistantIndex].content = ''

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let pendingContent = ''
    let sseBuffer = ''
    let isDisplaying = false

    // 逐字显示内容
    const displayNextChar = () => {
      if (pendingContent.length > 0) {
        const char = pendingContent.slice(0, 1)
        pendingContent = pendingContent.slice(1)
        messages.value[assistantIndex].content += char
        scrollToBottom()
        if (pendingContent.length > 0) {
          requestAnimationFrame(displayNextChar)
        } else {
          isDisplaying = false
        }
      } else {
        isDisplaying = false
      }
    }

    const processSseLine = (line) => {
      const trimmed = line.trim()
      if (!trimmed) return

      let dataStr = trimmed
      if (trimmed.startsWith('data:')) {
        dataStr = trimmed.slice(5).trim()
      }

      if (!dataStr.startsWith('{')) return

      try {
        const parsed = JSON.parse(dataStr)
        if (parsed.type === 'content') {
          pendingContent += parsed.content || ''
          if (!isDisplaying) {
            isDisplaying = true
            requestAnimationFrame(displayNextChar)
          }
        }

        if (parsed.type === 'status' && parsed.content === 'done') {
          if (pendingContent.length > 0) {
            messages.value[assistantIndex].content += pendingContent
            pendingContent = ''
          }
          messages.value[assistantIndex].loading = false
          sending.value = false
          isGenerating.value = false
        }

        if (parsed.type === 'error') {
          throw new Error(parsed.content || '请求失败')
        }
      } catch (e) {
        if (e instanceof SyntaxError) {
          console.debug('[流式] 解析SSE数据未完成，等待下一片段', dataStr)
          return
        }
        throw e
      }
    }

    const processStream = async ({ done, value }) => {
      if (done || abortController.signal.aborted) {
        sseBuffer += decoder.decode()
        if (sseBuffer.trim()) {
          processSseLine(sseBuffer)
          sseBuffer = ''
        }

        // 显示完剩余内容
        if (pendingContent.length > 0) {
          messages.value[assistantIndex].content += pendingContent
          pendingContent = ''
        }
        messages.value[assistantIndex].loading = false
        return
      }

      sseBuffer += decoder.decode(value, { stream: true })
      const lines = sseBuffer.split(/\r?\n/)
      sseBuffer = lines.pop() || ''

      for (const line of lines) {
        if (abortController.signal.aborted) break
        processSseLine(line)
      }

      if (!abortController.signal.aborted) {
        return reader.read().then(processStream)
      }
    }

    await reader.read().then(processStream)

  } catch (error) {
    if (error.name === 'AbortError') {
      if (messages.value[assistantIndex]) {
        messages.value[assistantIndex].loading = false
      }
      return
    }

    console.error('发送失败:', error)

    if (messages.value[assistantIndex]) {
      messages.value[assistantIndex].content = error.message || '抱歉，发生了错误，请稍后重试'
      messages.value[assistantIndex].loading = false
    }
    ElMessage.error(error.message || '发送消息失败')
  } finally {
    sending.value = false
    isGenerating.value = false
    abortController = null
  }
}

// 换行
function handleNewline(e) {
  if ((e && e.isComposing) || isComposing.value) {
    return
  }
  const textarea = e.target
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const value = inputMessage.value
  inputMessage.value = value.substring(0, start) + '\n' + value.substring(end)
  nextTick(() => {
    textarea.selectionStart = textarea.selectionEnd = start + 1
  })
}

// 快捷问题
function askQuestion(question) {
  inputMessage.value = question
  handleSend()
}

function handleCompositionStart() {
  isComposing.value = true
}

function handleCompositionEnd() {
  isComposing.value = false
}

// 格式化消息
function formatMessage(content) {
  if (!content) return ''

  let formatted = escapeHtml(content)
  // 处理换行
  formatted = formatted.replace(/\n/g, '<br>')
  // 处理代码块
  formatted = formatted.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
  // 处理行内代码
  formatted = formatted.replace(/`([^`]+)`/g, '<code>$1</code>')
  return formatted
}

function formatDetailContent(content) {
  if (!content) return ''
  return escapeHtml(content).replace(/\n/g, '<br>')
}

function escapeHtml(content) {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// 格式化时间
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 查看引用
async function viewCitation(cite) {
  try {
    const res = await request.get(`/knowledge/detail/${cite.id}`)
    if (res.data.code === 200) {
      currentCitation.value = {
        ...res.data.data,
        kbName: cite.kbName,
        title: cite.title
      }
      detailDrawerVisible.value = true
    }
  } catch (error) {
    console.error('获取详情失败:', error)
    ElMessage.error('获取详情失败')
  }
}

// 文档预览
async function previewDocument(item) {
  if (!item.sourceDocId) {
    ElMessage.warning('该知识没有关联文档')
    return
  }

  try {
    const res = await request.get(`/knowledge/document/preview/${item.sourceDocId}`)
    if (res.data.code === 200) {
      previewUrl.value = res.data.data
      previewDialogVisible.value = true
    }
  } catch (error) {
    console.error('获取预览URL失败:', error)
    ElMessage.error('获取预览失败')
  }
}

// 复制内容
function copyContent(content) {
  if (!content) return
  navigator.clipboard.writeText(content).then(() => {
    ElMessage.success('内容已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}
</script>

<style scoped>
.customer-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.chat-wrapper {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

/* 聊天主区域 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  min-width: 0;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-left h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

/* 消息区域 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* 欢迎消息 */
.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 40px;
}

.welcome-icon {
  margin-bottom: 24px;
}

.welcome-message h3 {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 12px;
}

.welcome-message p {
  color: var(--el-text-color-secondary);
  max-width: 450px;
  line-height: 1.6;
}

.quick-questions {
  margin-top: 32px;
}

.quick-questions p {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.question-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.question-tag {
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.question-tag:hover {
  transform: translateY(-2px);
}

/* 功能卡片 */
.feature-cards {
  display: flex;
  gap: 24px;
  margin-top: 40px;
  padding-top: 32px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.feature-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  transition: all 0.2s;
}

.feature-card:hover {
  background: var(--el-fill-color);
  transform: translateY(-2px);
}

.feature-card span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 消息项 */
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #06b6d4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.message-item.user .message-avatar {
  background: linear-gradient(135deg, #22c55e, #10b981);
}

.message-body {
  max-width: 75%;
  min-width: 200px;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.message-item.user .message-header {
  flex-direction: row-reverse;
}

.sender-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.model-tag {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  color: var(--el-text-color-secondary);
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  line-height: 1.8;
  font-size: 14px;
}

.message-item.user .message-content {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1), rgba(6, 182, 212, 0.1));
}

.message-content :deep(pre) {
  background: var(--el-fill-color);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-content :deep(code) {
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}

/* 加载动画 */
.thinking-dots {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-text-color-secondary);
  animation: bounce 1.4s infinite ease-in-out;
}

.thinking-dots span:nth-child(1) { animation-delay: 0s; }
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 引用来源 */
.message-citations {
  margin-top: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-size: 12px;
}

.citations-header {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.citations-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.citation-tag {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.citation-tag:hover {
  opacity: 0.8;
}

.message-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

/* 输入区域 */
.input-area {
  padding: 16px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.input-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.input-buttons {
  display: flex;
  gap: 8px;
}

.input-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 详情抽屉 */
.detail-content {
  padding: 0 20px;
}

.detail-header {
  margin-bottom: 20px;
}

.detail-header h2 {
  font-size: 22px;
  margin-top: 12px;
  line-height: 1.4;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 16px;
}

.detail-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.detail-body {
  margin-top: 20px;
}

.content-text {
  line-height: 1.8;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}

.detail-actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 12px;
}

/* 预览 */
.preview-container {
  height: 70vh;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
