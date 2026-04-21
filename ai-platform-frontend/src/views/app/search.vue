<template>
  <div class="search-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <h1>智能知识检索</h1>
        <p class="subtitle">基于知识库的智能问答系统，支持多知识库检索</p>
      </div>
    </div>

    <!-- 搜索区域 -->
    <div class="search-section">
      <!-- 知识库选择 -->
      <div class="kb-selector">
        <span class="selector-label">选择知识库：</span>
        <el-select
          v-model="selectedKbId"
          placeholder="请选择知识库（可选）"
          clearable
          @change="handleKbChange"
        >
          <el-option
            :label="'所有知识库'"
            :value="ALL_KB_ID"
          />
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          >
            <div class="kb-option">
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-desc">{{ kb.description }}</span>
            </div>
          </el-option>
        </el-select>
        <div class="scope-tip">
          <el-tag size="mini" type="success">当前范围：{{ searchScopeLabel }}</el-tag>
          <span>未选择或选择“所有知识库”即在全部知识库中搜索</span>
        </div>
      </div>

      <!-- 搜索类型 -->
      <div class="search-type-tabs">
        <el-radio-group v-model="searchType" size="default">
          <el-radio-button value="hybrid">
            <el-icon><MagicStick /></el-icon>
            混合搜索
          </el-radio-button>
          <el-radio-button value="keyword">
            <el-icon><Search /></el-icon>
            关键词搜索
          </el-radio-button>
          <el-radio-button value="vector">
            <el-icon><Connection /></el-icon>
            向量搜索
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 搜索框 -->
      <div class="search-box-wrapper">
        <el-input
          v-model="searchQuery"
          placeholder="请输入您的问题或关键词..."
          size="large"
          class="search-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button
              type="primary"
              :loading="loading"
              @click="handleSearch"
              class="search-btn"
            >
              搜索
            </el-button>
          </template>
        </el-input>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div class="results-section">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <el-skeleton :rows="5" animated />
      </div>

      <!-- 结果统计 -->
      <div v-else-if="hasSearched && searchResult.records?.length > 0" class="results-header">
        <div class="results-info">
          <span class="results-count">
            共找到 <strong>{{ searchResult.total }}</strong> 条相关知识
          </span>
          <span class="search-time" v-if="searchTime">
            搜索耗时：{{ searchTime }}ms
          </span>
        </div>
      </div>

      <!-- 结果列表 -->
      <div v-if="searchResult.records?.length > 0" class="results-list">
        <div
          v-for="(item, index) in searchResult.records"
          :key="item.id"
          class="result-card"
          @click="viewDetail(item)"
        >
          <div class="card-header">
            <div class="card-tags">
              <el-tag size="small" type="info">{{ item.kbName }}</el-tag>
              <el-tag v-if="item.sourceType === 'upload'" size="small" type="warning">
                {{ item.fileType || '文档' }}
              </el-tag>
            </div>
            <div class="card-score">
              <el-progress
                :percentage="Math.round((item.score || 0) * 100)"
                :color="getScoreColor(item.score)"
                :stroke-width="6"
                :show-text="false"
                style="width: 60px"
              />
              <span class="score-text">{{ Math.round((item.score || 0) * 100) }}%</span>
            </div>
          </div>

          <h3 class="card-title">{{ item.title }}</h3>

          <div class="card-content">
            <p v-html="highlightKeyword(item.content, searchQuery)"></p>
          </div>

          <div class="card-footer">
            <div class="card-meta">
              <span v-if="item.originalFileName" class="meta-item">
                <el-icon><Document /></el-icon>
                {{ item.originalFileName }}
              </span>
              <span v-if="item.createTime" class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ formatDate(item.createTime) }}
              </span>
            </div>
            <div class="card-actions">
              <el-button type="primary" link @click.stop="viewDetail(item)">
                查看详情
                <el-icon><ArrowRight /></el-icon>
              </el-button>
              <el-button
                v-if="item.sourceDocId"
                type="success"
                link
                @click.stop="previewDocument(item)"
              >
                文档预览
                <el-icon><View /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="searchResult.total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="searchResult.total"
          layout="prev, pager, next, jumper"
          @current-change="handlePageChange"
        />
      </div>

      <!-- 无结果 -->
      <div v-else-if="hasSearched && !loading" class="empty-state">
        <el-empty description="未找到相关结果，请尝试其他关键词或切换搜索模式">
          <template #image>
            <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
              <circle cx="60" cy="60" r="50" fill="#f5f5f5"/>
              <circle cx="50" cy="50" r="20" stroke="#d9d9d9" stroke-width="4" fill="none"/>
              <line x1="65" y1="65" x2="85" y2="85" stroke="#d9d9d9" stroke-width="4" stroke-linecap="round"/>
              <path d="M40 45 Q50 35 60 45" stroke="#d9d9d9" stroke-width="3" fill="none"/>
            </svg>
          </template>
        </el-empty>
      </div>

      <!-- 初始状态提示 -->
      <div v-else-if="!hasSearched" class="init-hint">
        <div class="hint-content">
          <el-icon class="hint-icon"><MagicStick /></el-icon>
          <h3>开始智能检索</h3>
          <p>知识库选择可选，留空或选择“所有知识库”时会覆盖所有内容</p>
          <div class="hint-features">
            <div class="feature-item">
              <el-icon><Check /></el-icon>
              <span>混合搜索</span>
            </div>
            <div class="feature-item">
              <el-icon><Check /></el-icon>
              <span>向量检索</span>
            </div>
            <div class="feature-item">
              <el-icon><Check /></el-icon>
              <span>文档预览</span>
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
      <div v-if="currentDetail" class="detail-content">
        <div class="detail-header">
          <el-tag type="primary">{{ currentDetail.kbName }}</el-tag>
          <h2>{{ currentDetail.title }}</h2>
        </div>

        <div class="detail-meta">
          <span v-if="currentDetail.sourceType">
            <el-icon><Document /></el-icon>
            来源：{{ currentDetail.sourceType === 'manual' ? '手动录入' : '文档上传' }}
          </span>
          <span v-if="currentDetail.originalFileName">
            <el-icon><Files /></el-icon>
            {{ currentDetail.originalFileName }}
          </span>
          <span v-if="currentDetail.createTime">
            <el-icon><Clock /></el-icon>
            创建时间：{{ formatDate(currentDetail.createTime) }}
          </span>
        </div>

        <div class="detail-tags" v-if="currentDetail.tags?.length">
          <el-tag v-for="tag in currentDetail.tags" :key="tag" size="small">
            {{ tag }}
          </el-tag>
        </div>

        <el-divider />

        <div class="detail-body">
          <h4>内容</h4>
          <div class="content-text" v-html="currentDetail.content"></div>
        </div>

        <div v-if="currentDetail.summary" class="detail-summary">
          <h4>摘要</h4>
          <p>{{ currentDetail.summary }}</p>
        </div>

        <div class="detail-actions">
          <el-button
            v-if="currentDetail.sourceDocId"
            type="primary"
            @click="previewDocument(currentDetail)"
          >
            <el-icon><View /></el-icon>
            文档预览
          </el-button>
          <el-button @click="copyContent">
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
import { ref, reactive, onMounted, computed } from 'vue'
import { Search, Document, Clock, ArrowRight, View, Files, Connection, MagicStick, Check, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

// 状态
const searchQuery = ref('')
const ALL_KB_ID = 'all'
const selectedKbId = ref(ALL_KB_ID)
const searchType = ref('hybrid')
const loading = ref(false)
const hasSearched = ref(false)
const searchTime = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const knowledgeBases = ref([])
const searchResult = reactive({
  records: [],
  total: 0,
  page: 1,
  pageSize: 10
})
const searchScopeLabel = computed(() => {
  if (selectedKbId.value === ALL_KB_ID) {
    return '所有知识库'
  }
  const kb = knowledgeBases.value.find(kb => kb.id === selectedKbId.value)
  return kb ? kb.name : '所有知识库'
})


const detailDrawerVisible = ref(false)
const currentDetail = ref(null)

// 预览
const previewDialogVisible = ref(false)
const previewUrl = ref('')

// 计算对话框宽度
const dialogWidth = computed(() => {
  return window.innerWidth > 1200 ? '800px' : '90%'
})

// 加载知识库列表
async function loadKnowledgeBases() {
  try {
    const res = await request.get('/knowledge/bases')
    if (res.code === 200) {
      knowledgeBases.value = res.data || []
    }
  } catch (error) {
    console.error('加载知识库失败:', error)
  }
}

// 搜索
async function handleSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }

  loading.value = true
  hasSearched.value = true
  currentPage.value = 1

  const startTime = Date.now()

  try {
    const res = await request.post('/knowledge/search', {
      keyword: searchQuery.value,
      searchType: searchType.value,
      topK: 50,
      page: 1,
      pageSize: pageSize.value,
      ...(selectedKbId.value && selectedKbId.value !== ALL_KB_ID ? { kbId: selectedKbId.value } : {})
    })

    searchTime.value = Date.now() - startTime

    if (res.code === 200) {
      const data = res.data
      searchResult.records = data.records || []
      searchResult.total = data.total || 0
      searchResult.page = data.page || 1
      searchResult.pageSize = data.pageSize || 10
      // 滚动到顶部
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('搜索失败，请稍后重试')
    searchResult.records = []
    searchResult.total = 0
  } finally {
    loading.value = false
  }
}

// 分页
async function handlePageChange(page) {
  loading.value = true
  currentPage.value = page

  try {
    const res = await request.post('/knowledge/search', {
      keyword: searchQuery.value,
      searchType: searchType.value,
      topK: 50,
      page: page,
      pageSize: pageSize.value,
      ...(selectedKbId.value && selectedKbId.value !== ALL_KB_ID ? { kbId: selectedKbId.value } : {})
    })

    if (res.code === 200) {
      const data = res.data
      searchResult.records = data.records || []
      searchResult.total = data.total || 0
      searchResult.page = data.page || page
      searchResult.pageSize = data.pageSize || pageSize.value
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}


// 知识库变更
function handleKbChange() {
  if (hasSearched.value && searchQuery.value) {
    handleSearch()
  }
}

// 查看详情
async function viewDetail(item) {
  try {
    const res = await request.get(`/knowledge/detail/${item.id}`)
    if (res.code === 200) {
      currentDetail.value = res.data
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
    if (res.code === 200) {
      previewUrl.value = res.data
      previewDialogVisible.value = true
    }
  } catch (error) {
    console.error('获取预览URL失败:', error)
    ElMessage.error('获取预览失败')
  }
}

// 复制内容
function copyContent() {
  if (currentDetail.value?.content) {
    navigator.clipboard.writeText(currentDetail.value.content).then(() => {
      ElMessage.success('内容已复制到剪贴板')
    }).catch(() => {
      ElMessage.error('复制失败')
    })
  }
}

// 高亮关键词
function highlightKeyword(text, keyword) {
  if (!text || !keyword) return text
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<mark style="background-color: #fef08a; padding: 0 2px;">$1</mark>')
}

// 获取分数颜色
function getScoreColor(score) {
  if (score >= 0.8) return '#22c55e'
  if (score >= 0.6) return '#f59e0b'
  return '#ef4444'
}

// 格式化日期
function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped>
.search-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.subtitle {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.search-section {
  background: var(--el-bg-color);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.kb-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.selector-label {
  font-weight: 500;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.kb-option {
  display: flex;
  flex-direction: column;
}

.kb-name {
  font-weight: 500;
}

.kb-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.scope-tip {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.search-type-tabs {
  margin-bottom: 16px;
}

.search-type-tabs .el-radio-button__inner {
  display: flex;
  align-items: center;
  gap: 6px;
}

.search-box-wrapper {
  display: flex;
  gap: 12px;
}

.search-input {
  flex: 1;
}

.search-btn {
  min-width: 80px;
}

.results-section {
  min-height: 400px;
}

.results-header {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.results-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.results-count {
  color: var(--el-text-color-regular);
}

.results-count strong {
  color: var(--el-color-primary);
  font-size: 18px;
}

.search-time {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-card {
  background: var(--el-bg-color);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.result-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-tags {
  display: flex;
  gap: 8px;
}

.card-score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  min-width: 40px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
  line-height: 1.4;
}

.card-content {
  margin-bottom: 16px;
}

.card-content p {
  color: var(--el-text-color-regular);
  line-height: 1.8;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.card-meta {
  display: flex;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card-actions {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.empty-state,
.init-hint {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.hint-content {
  text-align: center;
  max-width: 400px;
}

.hint-icon {
  font-size: 64px;
  color: var(--el-color-primary-light-5);
  margin-bottom: 16px;
}

.hint-content h3 {
  font-size: 20px;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.hint-content p {
  color: var(--el-text-color-secondary);
  margin-bottom: 24px;
}

.hint-features {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);
}

.feature-item .el-icon {
  color: var(--el-color-success);
}

.loading-state {
  padding: 20px;
}

/* 详情抽屉样式 */
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

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-body h4,
.detail-summary h4 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.content-text {
  line-height: 1.8;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}

.detail-summary {
  margin-top: 20px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.detail-summary p {
  color: var(--el-text-color-regular);
  line-height: 1.6;
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

.preview-loading {
  padding: 20px;
}
</style>
