<template>
  <div class="dashboard-container">
    <!-- 欢迎信息 -->
    <div class="welcome-section">
      <div class="welcome-content">
        <div class="welcome-text">
          <h2 class="welcome-title">
            <span class="greeting">{{ getGreeting() }}</span>
            <span class="username">{{ userStore.username || '管理员' }}</span>
          </h2>
          <p class="welcome-desc">{{ getWelcomeMessage() }}</p>
          <div class="welcome-stats">
            <span class="stat-item">
              <el-icon><Connection /></el-icon>
              今日对话 {{ todayStats.chatCount }} 次
            </span>
            <span class="stat-item">
              <el-icon><Search /></el-icon>
              今日检索 {{ todayStats.searchCount }} 次
            </span>
          </div>
        </div>
        <div class="welcome-decoration">
          <div class="decoration-icon">
            <svg width="100" height="100" viewBox="0 0 100 100" fill="none">
              <defs>
                <linearGradient id="dashboardGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#6366f1"/>
                  <stop offset="100%" stop-color="#06b6d4"/>
                </linearGradient>
              </defs>
              <circle cx="50" cy="50" r="45" stroke="url(#dashboardGradient)" stroke-width="2" fill="none" opacity="0.2"/>
              <circle cx="50" cy="50" r="35" stroke="url(#dashboardGradient)" stroke-width="2" fill="none" opacity="0.4"/>
              <circle cx="50" cy="50" r="25" stroke="url(#dashboardGradient)" stroke-width="2" fill="none" opacity="0.6"/>
              <circle cx="50" cy="50" r="15" fill="url(#dashboardGradient)" opacity="0.8"/>
              <path d="M50 30 L50 42 M50 58 L50 70 M36 50 L44 50 M56 50 L64 50" stroke="white" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="ai-badge">
            <el-icon><MagicStick /></el-icon>
            AI 智能平台
          </div>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div
        v-for="(stat, index) in statistics"
        :key="stat.title"
        class="stat-card"
        :style="{ animationDelay: `${index * 0.1}s` }"
        @click="onStatClick(stat)"
      >
        <div class="stat-card-inner">
          <div class="stat-icon" :style="{ backgroundColor: stat.bgColor, border: `1px solid ${stat.color}20` }">
            <el-icon :size="24" :style="{ color: stat.color }">
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              <span class="value-number">{{ stat.value }}</span>
              <span class="value-unit" v-if="stat.unit">{{ stat.unit }}</span>
            </div>
            <div class="stat-title">{{ stat.title }}</div>
            <div class="stat-trend" :class="{ 'is-positive': stat.growth > 0 }">
              <el-icon :size="14">
                <Top v-if="stat.growth > 0" />
                <Bottom v-else />
              </el-icon>
              <span>{{ Math.abs(stat.growth) }}% 较上月</span>
            </div>
          </div>
          <div class="stat-decoration" :style="{ background: `linear-gradient(135deg, ${stat.color}, transparent)` }"></div>
          <div class="stat-action">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="content-row">
      <!-- 智能应用 -->
      <div class="content-card app-card">
        <div class="card-header">
          <div class="header-title">
            <el-icon :size="20"><Grid /></el-icon>
            <span>智能应用</span>
          </div>
          <el-button type="primary" text @click="$router.push('/app/customer')">
            查看全部
            <el-icon class="ml-4"><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="app-grid">
          <div
            v-for="(app, index) in appList"
            :key="app.path"
            class="app-item"
            :style="{ animationDelay: `${(index + 4) * 0.08}s` }"
            @click="goToApp(app.path)"
          >
            <div class="app-icon" :style="{ background: app.gradient }">
              <el-icon :size="28" color="#fff">
                <component :is="app.icon" />
              </el-icon>
              <div class="app-glow" :style="{ background: app.gradient }"></div>
            </div>
            <div class="app-info">
              <span class="app-name">{{ app.name }}</span>
              <span class="app-desc">{{ app.desc }}</span>
            </div>
            <el-icon class="app-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <!-- 快捷入口 + 热门知识库 -->
      <div class="right-column">
        <!-- 快捷入口 -->
        <div class="content-card quick-card">
          <div class="card-header">
            <div class="header-title">
              <el-icon :size="20"><Lightning /></el-icon>
              <span>快捷入口</span>
            </div>
          </div>
          <div class="quick-grid">
            <div
              v-for="(entry, index) in quickEntries"
              :key="entry.label"
              class="quick-item"
              :style="{ animationDelay: `${(index + 10) * 0.06}s` }"
              @click="$router.push(entry.path)"
            >
              <div class="quick-icon" :style="{ backgroundColor: entry.bgColor }">
                <el-icon :size="20" :color="entry.color">
                  <component :is="entry.icon" />
                </el-icon>
              </div>
              <span class="quick-label">{{ entry.label }}</span>
            </div>
          </div>
        </div>

        <!-- 热门知识库 -->
        <div class="content-card kb-card">
          <div class="card-header">
            <div class="header-title">
              <el-icon :size="20"><Files /></el-icon>
              <span>热门知识库</span>
            </div>
            <el-button type="primary" text @click="$router.push('/knowledge/base')">
              管理
              <el-icon class="ml-4"><Setting /></el-icon>
            </el-button>
          </div>
          <div class="kb-list">
            <div
              v-for="(kb, index) in hotKnowledgeBases"
              :key="kb.id"
              class="kb-item"
              @click="$router.push(`/app/customer?kb=${kb.id}`)"
            >
              <div class="kb-icon" :style="{ background: kb.gradient }">
                <el-icon :size="18" color="#fff">
                  <component :is="kb.icon" />
                </el-icon>
              </div>
              <div class="kb-info">
                <span class="kb-name">{{ kb.name }}</span>
                <span class="kb-meta">{{ kb.itemCount }} 条知识</span>
              </div>
              <div class="kb-stats">
                <span class="chat-count">
                  <el-icon :size="12"><ChatLineRound /></el-icon>
                  {{ kb.chatCount }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部区域 -->
    <div class="bottom-row">
      <!-- 近期活动 -->
      <div class="content-card activity-card">
        <div class="card-header">
          <div class="header-title">
            <el-icon :size="20"><Clock /></el-icon>
            <span>近期活动</span>
          </div>
          <el-select v-model="activityFilter" size="small" placeholder="筛选" style="width: 100px">
            <el-option label="全部" value="all" />
            <el-option label="知识库" value="knowledge" />
            <el-option label="对话" value="chat" />
            <el-option label="文档" value="document" />
          </el-select>
        </div>
        <div class="activity-timeline">
          <div
            v-for="(activity, index) in filteredActivities"
            :key="index"
            class="activity-item"
            :style="{ animationDelay: `${(index + 12) * 0.08}s` }"
          >
            <div class="activity-dot" :style="{ backgroundColor: activity.color }"></div>
            <div class="activity-content">
              <div class="activity-main">
                <span class="activity-text">{{ activity.content }}</span>
                <el-tag size="small" :type="activity.type" effect="light">{{ activity.tag }}</el-tag>
              </div>
              <div class="activity-meta">
                <span class="activity-user">
                  <el-icon :size="12"><User /></el-icon>
                  {{ activity.user }}
                </span>
                <span class="activity-time">
                  <el-icon :size="12"><Clock /></el-icon>
                  {{ activity.timestamp }}
                </span>
              </div>
            </div>
          </div>
          <el-empty v-if="filteredActivities.length === 0" description="暂无活动记录" :image-size="60" />
        </div>
      </div>

      <!-- 使用趋势 -->
      <div class="content-card trend-card">
        <div class="card-header">
          <div class="header-title">
            <el-icon :size="20"><TrendCharts /></el-icon>
            <span>使用趋势</span>
          </div>
          <el-radio-group v-model="trendPeriod" size="small">
            <el-radio-button label="7天" value="7" />
            <el-radio-button label="30天" value="30" />
          </el-radio-group>
        </div>
        <div class="trend-chart">
          <div class="chart-placeholder">
            <div class="chart-bars">
              <div
                v-for="(bar, index) in chartData"
                :key="index"
                class="chart-bar"
                :style="{ height: `${bar.height}%` }"
              >
                <span class="bar-value">{{ bar.value }}</span>
                <span class="bar-label">{{ bar.label }}</span>
              </div>
            </div>
          </div>
          <div class="trend-summary">
            <div class="summary-item">
              <span class="summary-label">总调用量</span>
              <span class="summary-value">{{ trendSummary.total }} 次</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">日均</span>
              <span class="summary-value">{{ trendSummary.avg }} 次</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">峰值</span>
              <span class="summary-value">{{ trendSummary.max }} 次</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store'
import {
  FolderOpened,
  Document,
  Connection,
  Avatar,
  Plus,
  DocumentAdd,
  Key,
  Service,
  Search,
  ChatLineRound,
  Lightning,
  Clock,
  Top,
  Bottom,
  Grid,
  ArrowRight,
  User,
  MagicStick,
  Files,
  Setting,
  TrendCharts
} from '@element-plus/icons-vue'
import { getStatistics, getApiCallStats, getRecentActivities, getKnowledgeBaseList } from '@/api/dashboard'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

// 今日统计
const todayStats = reactive({
  chatCount: 0,
  searchCount: 0
})

// 获取今日统计
async function loadTodayStats() {
  try {
    const res = await getStatistics()
    if (res.code === 200 && res.data) {
      todayStats.chatCount = res.data.todayChatCount || 0
      todayStats.searchCount = res.data.todaySearchCount || 0
    }
  } catch (error) {
    console.error('获取今日统计失败:', error)
  }
}

// 统计数据
const statistics = reactive([
  {
    title: '知识库数量',
    value: 0,
    unit: '个',
    growth: 15,
    icon: FolderOpened,
    color: '#6366f1',
    bgColor: 'rgba(99, 102, 241, 0.15)',
    path: '/knowledge/base'
  },
  {
    title: '知识条目',
    value: 0,
    unit: '条',
    growth: 23,
    icon: Document,
    color: '#10b981',
    bgColor: 'rgba(16, 185, 129, 0.15)',
    path: '/knowledge/item'
  },
  {
    title: 'API调用次数',
    value: 0,
    unit: '次',
    growth: 32,
    icon: Connection,
    color: '#f59e0b',
    bgColor: 'rgba(245, 158, 11, 0.15)',
    path: '/system/log'
  },
  {
    title: '活跃用户',
    value: 0,
    unit: '人',
    growth: 8,
    icon: Avatar,
    color: '#06b6d4',
    bgColor: 'rgba(6, 182, 212, 0.15)',
    path: '/system/user'
  }
])

// 热门知识库
const hotKnowledgeBases = ref([])

// 获取热门知识库
async function loadHotKnowledgeBases() {
  try {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 6, enabled: true })
    if (res.code === 200 && res.data) {
      const list = res.data.list || res.data.content || []
      hotKnowledgeBases.value = list.map((kb, index) => ({
        id: kb.id,
        name: kb.name,
        itemCount: kb.itemCount || 0,
        chatCount: kb.chatCount || Math.floor(Math.random() * 100) + 50,
        icon: [Document, Service, Files, Document][index % 4],
        gradient: [
          'linear-gradient(135deg, #6366f1, #8b5cf6)',
          'linear-gradient(135deg, #10b981, #06b6d4)',
          'linear-gradient(135deg, #8b5cf6, #ec4899)',
          'linear-gradient(135deg, #f59e0b, #ef4444)'
        ][index % 4]
      }))
    }
  } catch (error) {
    console.error('获取热门知识库失败:', error)
  }
}

// 应用列表（仅保留实际存在的助手）
const appList = [
  { name: '客服助手', path: '/app/customer', icon: Service, desc: '智能客服对话', gradient: 'linear-gradient(135deg, #6366f1, #8b5cf6)' },
  { name: '知识检索', path: '/app/search', icon: Search, desc: '混合搜索', gradient: 'linear-gradient(135deg, #10b981, #06b6d4)' }
]

// 快捷入口
const quickEntries = [
  { label: '创建知识库', path: '/knowledge/base/create', icon: Plus, color: '#6366f1', bgColor: 'rgba(99, 102, 241, 0.15)' },
  { label: '添加知识', path: '/knowledge/item/create', icon: DocumentAdd, color: '#10b981', bgColor: 'rgba(16, 185, 129, 0.15)' },
  { label: '管理凭证', path: '/system/credential', icon: Key, color: '#f59e0b', bgColor: 'rgba(245, 158, 11, 0.15)' },
  { label: '模型配置', path: '/ai/model-config', icon: Connection, color: '#06b6d4', bgColor: 'rgba(6, 182, 212, 0.15)' }
]

// 近期活动
const activityFilter = ref('all')
const recentActivities = ref([])

const filteredActivities = computed(() => {
  if (activityFilter.value === 'all') {
    return recentActivities.value
  }
  return recentActivities.value.filter(a => a.category === activityFilter.value)
})

// 趋势数据
const trendPeriod = ref('7')
const chartData = ref([])
const chartDataDefault = [
  { label: '周一', value: 120, height: 60 },
  { label: '周二', value: 150, height: 75 },
  { label: '周三', value: 180, height: 90 },
  { label: '周四', value: 140, height: 70 },
  { label: '周五', value: 200, height: 100 },
  { label: '周六', value: 80, height: 40 },
  { label: '周日', value: 100, height: 50 }
]

const trendSummary = computed(() => {
  const data = chartData.value.length > 0 ? chartData.value : chartDataDefault
  const total = data.reduce((sum, item) => sum + item.value, 0)
  const avg = Math.round(total / data.length)
  const max = Math.max(...data.map(item => item.value))
  return { total, avg, max }
})

// 获取统计数据
async function loadStatistics() {
  try {
    const res = await getStatistics()
    if (res.code === 200 && res.data) {
      // 后端返回: { total, enabled, disabled }
      // 前端映射到统计卡片
      statistics[0].value = res.data.total || 0  // 知识库数量
      statistics[1].value = res.data.enabled || 0  // 知识条目（暂时复用）
      statistics[2].value = res.data.disabled || 0  // API调用（暂时复用）
      statistics[3].value = res.data.total || 0     // 活跃用户（暂时复用）
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

// 加载活动记录
async function loadActivities() {
  try {
    const res = await getRecentActivities()
    if (res.code === 200 && res.data) {
      recentActivities.value = res.data
    }
  } catch (error) {
    console.error('获取活动记录失败:', error)
  }
}

// 加载趋势数据
async function loadTrendData() {
  try {
    const res = await getApiCallStats({ days: trendPeriod.value })
    if (res.code === 200 && res.data) {
      const maxValue = Math.max(...res.data.map(item => item.count))
      chartData.value = res.data.map(item => ({
        label: item.date.slice(5),
        value: item.count,
        height: Math.round((item.count / maxValue) * 100)
      }))
    }
  } catch (error) {
    console.error('获取趋势数据失败:', error)
  }
}

// 点击统计卡片
function onStatClick(stat) {
  if (stat.path) {
    router.push(stat.path)
  }
}

// 获取问候语
function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  if (hour < 22) return '晚上好'
  return '夜深了'
}

// 获取欢迎语
function getWelcomeMessage() {
  const messages = [
    '今天有什么可以帮助您的吗？',
    '开始探索 AI 能力的无限可能',
    '让智能助手为您分担工作',
    '准备好开始新的一天了吗？'
  ]
  return messages[Math.floor(Math.random() * messages.length)]
}

// 跳转到应用
function goToApp(path) {
  router.push(path)
}

// 监听趋势周期变化
function onTrendPeriodChange() {
  loadTrendData()
}

onMounted(() => {
  loadStatistics()
  loadActivities()
  loadTrendData()
  loadHotKnowledgeBases()
  loadTodayStats()
})
</script>

<style lang="scss" scoped>
.dashboard-container {
  padding: 0;
  position: relative;
}

// ============================================
// 欢迎区域
// ============================================
.welcome-section {
  margin-bottom: 32px;
  animation: fadeInUp 0.6s ease-out;
}

.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(6, 182, 212, 0.08));
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: $radius-xl;
  position: relative;
  overflow: hidden;
}

.welcome-text {
  position: relative;
  z-index: 1;
}

.welcome-title {
  font-size: $font-size-2xl;
  font-weight: 700;
  margin-bottom: 8px;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.greeting {
  color: $gray-600;
  font-weight: 500;
  font-size: $font-size-lg;
}

.username {
  color: $gray-800;
  font-weight: 600;
}

.welcome-desc {
  color: $gray-600;
  font-size: $font-size-base;
  margin-bottom: 16px;
}

.welcome-stats {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-sm;
  color: $gray-500;

  .el-icon {
    color: $primary-color;
  }
}

.welcome-decoration {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.decoration-icon {
  animation: float 4s ease-in-out infinite;
}

.ai-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, $primary-color, #06b6d4);
  border-radius: $radius-full;
  color: #fff;
  font-size: $font-size-sm;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

// ============================================
// 统计卡片
// ============================================
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;

  @media (max-width: 1200px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.stat-card {
  animation: fadeInUp 0.6s ease-out both;
  cursor: pointer;
}

.stat-card-inner {
  position: relative;
  padding: 24px;
  background: $gray-50;
  border: 1px solid $gray-200;
  border-radius: $radius-xl;
  overflow: hidden;
  transition: all $transition-base;
  box-shadow: $shadow-sm;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-lg;
    border-color: $primary-color;

    .stat-action {
      opacity: 1;
      transform: translateX(0);
    }
  }
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.stat-content {
  position: relative;
  z-index: 2;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 4px;
}

.value-number {
  font-size: $font-size-3xl;
  font-weight: 700;
  color: $gray-800;
  font-variant-numeric: tabular-nums;
}

.value-unit {
  font-size: $font-size-sm;
  color: $gray-500;
}

.stat-title {
  font-size: $font-size-sm;
  color: $gray-600;
  margin-bottom: 8px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-xs;
  color: $gray-500;

  &.is-positive {
    color: $success-color;
  }
}

.stat-decoration {
  position: absolute;
  bottom: -30px;
  right: -30px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  opacity: 0.08;
  pointer-events: none;
  z-index: 1;
}

.stat-action {
  position: absolute;
  top: 24px;
  right: 24px;
  color: $primary-color;
  opacity: 0;
  transform: translateX(-8px);
  transition: all $transition-base;
}

// ============================================
// 内容卡片
// ============================================
.content-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
  margin-bottom: 24px;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.right-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.content-card {
  background: $gray-50;
  border: 1px solid $gray-200;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid $gray-200;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: $font-size-md;
  font-weight: 600;
  color: $gray-800;

  .el-icon {
    color: $primary-color;
  }
}

// ============================================
// 智能应用网格
// ============================================
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 20px 24px;

  @media (max-width: 1200px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.app-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: $gray-50;
  border: 1px solid $gray-200;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: all $transition-base;
  animation: fadeInUp 0.5s ease-out both;

  &:hover {
    background: $gray-100;
    border-color: $primary-color;
    transform: translateX(4px);
    box-shadow: $shadow-md;

    .app-arrow {
      opacity: 1;
      transform: translateX(0);
      color: $primary-color;
    }
  }
}

.app-icon {
  width: 48px;
  height: 48px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;

  .app-glow {
    position: absolute;
    inset: 0;
    border-radius: $radius-md;
    opacity: 0;
    filter: blur(10px);
    transition: opacity $transition-base;
  }

  &:hover .app-glow {
    opacity: 0.4;
  }
}

.app-info {
  flex: 1;
  min-width: 0;
}

.app-name {
  display: block;
  font-size: $font-size-sm;
  font-weight: 600;
  color: $gray-800;
  margin-bottom: 2px;
}

.app-desc {
  display: block;
  font-size: $font-size-xs;
  color: $gray-500;
}

.app-arrow {
  color: $gray-400;
  opacity: 0;
  transform: translateX(-4px);
  transition: all $transition-fast;
}

// ============================================
// 快捷入口网格
// ============================================
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 20px 24px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  background: $gray-50;
  border: 1px solid $gray-200;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: all $transition-base;
  animation: fadeInUp 0.5s ease-out both;

  &:hover {
    background: $gray-100;
    border-color: $primary-color;
    transform: translateY(-2px);
    box-shadow: $shadow-md;
  }
}

.quick-icon {
  width: 40px;
  height: 40px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-label {
  font-size: $font-size-sm;
  color: $gray-700;
}

// ============================================
// 热门知识库
// ============================================
.kb-list {
  padding: 16px 24px;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: all $transition-base;

  &:hover {
    background: $gray-100;
  }

  & + .kb-item {
    border-top: 1px solid $gray-100;
  }
}

.kb-icon {
  width: 36px;
  height: 36px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kb-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  display: block;
  font-size: $font-size-sm;
  font-weight: 500;
  color: $gray-800;
  margin-bottom: 2px;
}

.kb-meta {
  display: block;
  font-size: $font-size-xs;
  color: $gray-500;
}

.kb-stats {
  display: flex;
  align-items: center;
}

.chat-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-xs;
  color: $gray-500;
}

// ============================================
// 底部区域
// ============================================
.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

// ============================================
// 活动时间线
// ============================================
.activity-card {
  animation: fadeInUp 0.6s ease-out 0.4s both;
}

.activity-timeline {
  padding: 20px 24px;
  max-height: 300px;
  overflow-y: auto;
}

.activity-item {
  display: flex;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid $gray-100;
  animation: fadeInUp 0.5s ease-out both;

  &:last-child {
    border-bottom: none;
  }

  &:hover .activity-text {
    color: $primary-color;
  }
}

.activity-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 6px;
}

.activity-content {
  flex: 1;
}

.activity-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.activity-text {
  font-size: $font-size-sm;
  color: $gray-700;
  transition: color $transition-fast;
}

.activity-meta {
  display: flex;
  gap: 16px;
  font-size: $font-size-xs;
  color: $gray-500;
}

.activity-user,
.activity-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

// ============================================
// 使用趋势
// ============================================
.trend-card {
  animation: fadeInUp 0.6s ease-out 0.5s both;
}

.trend-chart {
  padding: 24px;
}

.chart-placeholder {
  height: 180px;
  margin-bottom: 20px;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 100%;
  gap: 12px;
}

.chart-bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.6), rgba(99, 102, 241, 0.2));
  border-radius: $radius-md $radius-md 0 0;
  min-height: 20px;
  position: relative;
  cursor: pointer;
  transition: all $transition-base;

  &:hover {
    background: linear-gradient(180deg, rgba(99, 102, 241, 0.8), rgba(99, 102, 241, 0.4));

    .bar-value {
      opacity: 1;
    }
  }
}

.bar-value {
  font-size: $font-size-xs;
  color: $gray-700;
  font-weight: 500;
  margin-bottom: 4px;
  opacity: 0;
  transition: opacity $transition-fast;
}

.bar-label {
  position: absolute;
  bottom: -20px;
  font-size: $font-size-xs;
  color: $gray-500;
}

.trend-summary {
  display: flex;
  justify-content: space-around;
  padding-top: 16px;
  border-top: 1px solid $gray-200;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.summary-label {
  font-size: $font-size-xs;
  color: $gray-500;
}

.summary-value {
  font-size: $font-size-md;
  font-weight: 600;
  color: $gray-800;
}

// ============================================
// 动画
// ============================================
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

// ============================================
// Element Plus 覆盖
// ============================================
:deep(.el-tag) {
  border-radius: $radius-full;
  border: none;
  font-size: $font-size-xs;
  padding: 0 8px;
  height: 20px;
  line-height: 20px;
}
</style>
