<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ 'is-collapsed': isCollapsed }">
      <!-- Logo 区域 -->
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon">
            <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
              <defs>
                <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#6366f1"/>
                  <stop offset="100%" stop-color="#06b6d4"/>
                </linearGradient>
              </defs>
              <circle cx="14" cy="14" r="12" stroke="url(#logoGradient)" stroke-width="2" fill="none"/>
              <circle cx="14" cy="14" r="4" fill="url(#logoGradient)"/>
              <circle cx="14" cy="6" r="2" fill="url(#logoGradient)" opacity="0.7"/>
              <circle cx="14" cy="22" r="2" fill="url(#logoGradient)" opacity="0.7"/>
              <circle cx="6" cy="14" r="2" fill="url(#logoGradient)" opacity="0.7"/>
              <circle cx="22" cy="14" r="2" fill="url(#logoGradient)" opacity="0.7"/>
            </svg>
          </div>
          <transition name="fade-slide">
            <span v-if="!isCollapsed" class="logo-text">AI智答平台</span>
          </transition>
        </div>
      </div>

      <!-- 菜单 -->
      <div class="sidebar-menu">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :router="true"
          :collapse-transition="false"
          background-color="transparent"
          text-color="#64748b"
          active-text-color="#fff"
        >
          <el-menu-item v-if="canView('dashboard')" index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>工作台</template>
          </el-menu-item>

          <el-sub-menu v-if="canView('knowledge.base') || canView('knowledge.item')" index="/knowledge">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>智能知识库</span>
            </template>
            <el-menu-item v-if="canView('knowledge.base')" index="/knowledge/base">知识库列表</el-menu-item>
            <el-menu-item v-if="canView('knowledge.item')" index="/knowledge/item">知识列表</el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="canView('app.customer') || canView('app.search')" index="/app">
            <template #title>
              <el-icon><Grid /></el-icon>
              <span>智能应用</span>
            </template>
            <el-menu-item v-if="canView('app.customer')" index="/app/customer">智能助手</el-menu-item>
            <el-menu-item v-if="canView('app.search')" index="/app/search">智能搜索</el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="canView('flow.template')" index="/flow">
            <template #title>
              <el-icon><Operation /></el-icon>
              <span>流程管理</span>
            </template>
            <el-menu-item index="/flow/template">流程模板</el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="canView('system.user') || canView('system.role') || canView('system.credential') || canView('system.model')" index="/system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item v-if="canView('system.user')" index="/system/user">用户管理</el-menu-item>
            <el-menu-item v-if="canView('system.role')" index="/system/role">角色管理</el-menu-item>
            <el-menu-item v-if="canView('system.credential')" index="/system/credential">API凭证管理</el-menu-item>
            <el-menu-item v-if="canView('system.model')" index="/system/model">大模型配置</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>

      <!-- 侧边栏底部信息 -->
      <div class="sidebar-footer">
        <div class="sidebar-toggle" @click="toggleSidebar">
          <el-icon :size="18">
            <ArrowLeft v-if="!isCollapsed" />
            <ArrowRight v-else />
          </el-icon>
          <transition name="fade-slide">
            <span v-if="!isCollapsed">收起</span>
          </transition>
        </div>
      </div>
    </aside>

    <!-- 主内容区域 -->
    <div class="main-wrapper">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left">
          <div class="breadcrumb">
            <span class="breadcrumb-home">首页</span>
            <span class="breadcrumb-separator">
              <el-icon><ArrowRight /></el-icon>
            </span>
            <span class="breadcrumb-current" v-if="currentRoute.meta?.title">
              {{ currentRoute.meta.title }}
            </span>
          </div>
        </div>

        <div class="header-right">
          <!-- 搜索按钮 -->
          <el-tooltip content="搜索" placement="bottom">
            <button class="header-btn">
              <el-icon :size="18"><Search /></el-icon>
            </button>
          </el-tooltip>

          <!-- 通知 -->
          <el-tooltip content="通知" placement="bottom">
            <button class="header-btn notification-btn">
              <el-icon :size="18"><Bell /></el-icon>
              <span class="notification-dot"></span>
            </button>
          </el-tooltip>

          <!-- 全屏 -->
          <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="bottom">
            <button class="header-btn" @click="toggleFullscreen">
              <el-icon :size="18">
                <FullScreen v-if="!isFullscreen" />
                <Close v-else />
              </el-icon>
            </button>
          </el-tooltip>

          <!-- 用户信息 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <div class="user-avatar">
                <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" alt="avatar" />
                <el-icon v-else :size="18"><User /></el-icon>
              </div>
              <span class="user-name">{{ userStore.username || '管理员' }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="user-dropdown">
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>
                  系统设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store'
import {
  Odometer, Document, Grid, Operation, Setting,
  ArrowLeft, ArrowRight, ArrowDown, Search, Bell, FullScreen,
  Close, User, SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)
const isFullscreen = ref(false)
const currentRoute = computed(() => route)

// 当前激活的菜单
const activeMenu = computed(() => route.path)

function canView(code) {
  return userStore.hasPermission(code)
}

// 切换侧边栏折叠状态
function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

// 切换全屏
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

// 全屏变化监听
function handleFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

// 处理下拉菜单命令
async function handleCommand(command) {
  switch (command) {
    case 'profile':
      ElMessage.info('个人中心功能开发中')
      break
    case 'settings':
      ElMessage.info('系统设置功能开发中')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        userStore.logout()
        router.push('/login')
      } catch {
        // 取消操作
      }
      break
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})
</script>

<style lang="scss" scoped>
// ============================================
// Layout 主容器
// ============================================
.layout-container {
  display: flex;
  width: 100%;
  height: 100%;
  background: $gray-100;
}

// ============================================
// 侧边栏
// ============================================
.sidebar {
  width: $sidebar-width;
  height: 100%;
  background: $gray-50;
  border-right: 1px solid $gray-200;
  display: flex;
  flex-direction: column;
  transition: width $transition-base;
  position: relative;
  overflow: hidden;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);

  &.is-collapsed {
    width: $sidebar-collapsed-width;

    .logo {
      justify-content: center;
    }
  }
}

// 侧边栏头部
.sidebar-header {
  height: $header-height;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid $gray-200;
  background: $gray-50;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  svg {
    filter: drop-shadow(0 0 8px rgba(99, 102, 241, 0.5));
  }
}

.logo-text {
  font-size: $font-size-lg;
  font-weight: 700;
  color: $gray-800;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

// 菜单区域
.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 8px;

  // 自定义菜单样式
  :deep(.el-menu) {
    border: none;
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 44px;
    line-height: 44px;
    border-radius: $radius-md;
    margin-bottom: 4px;
    transition: all $transition-fast;
    position: relative;
    overflow: hidden;

    &:hover {
      background: rgba($primary-color, 0.08);
      color: $primary-color;
    }
  }

  :deep(.el-menu-item.is-active) {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
    font-weight: 600;

    .el-icon {
      color: $primary-color;
    }
  }

  // 子菜单
  :deep(.el-sub-menu .el-menu) {
    background: transparent !important;
    padding-left: 8px;
  }

  :deep(.el-sub-menu__title) {
    &:hover {
      background: rgba($primary-color, 0.08);
    }
  }
}

// 侧边栏底部
.sidebar-footer {
  padding: 12px 8px;
  border-top: 1px solid $gray-200;
}

.sidebar-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: $radius-md;
  color: $gray-500;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: rgba($primary-color, 0.08);
    color: $primary-color;
  }

  span {
    font-size: $font-size-sm;
  }
}

// ============================================
// 主内容区域
// ============================================
.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

// ============================================
// 顶部导航
// ============================================
.header {
  height: $header-height;
  background: $gray-50;
  border-bottom: 1px solid $gray-200;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: relative;
  z-index: 10;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
}

// 面包屑导航
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: $font-size-sm;
}

.breadcrumb-home {
  color: $gray-500;
}

.breadcrumb-separator {
  color: $gray-400;
  display: flex;
  align-items: center;
}

.breadcrumb-current {
  color: $gray-700;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: $radius-md;
  color: $gray-500;
  cursor: pointer;
  transition: all $transition-fast;
  position: relative;

  &:hover {
    background: rgba($primary-color, 0.08);
    color: $primary-color;
  }
}

// 通知按钮
.notification-btn {
  .notification-dot {
    position: absolute;
    top: 6px;
    right: 6px;
    width: 8px;
    height: 8px;
    background: $danger-color;
    border-radius: 50%;
    border: 2px solid $gray-900;
    animation: pulse-dot 2s ease-in-out infinite;
  }
}

@keyframes pulse-dot {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.2);
    opacity: 0.8;
  }
}

// 用户信息
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: all $transition-fast;
  margin-left: 8px;
  background: $gray-100;
  border: 1px solid $gray-200;

  &:hover {
    background: $gray-200;
    border-color: $gray-300;
  }
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: $radius-md;
  background: $gray-200;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $gray-600;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.user-name {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $gray-700;
}

.user-arrow {
  color: $gray-400;
  font-size: 12px;
}

// 下拉菜单样式
:deep(.user-dropdown) {
  min-width: 160px;

  .el-dropdown-menu__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;

    .el-icon {
      width: 16px;
      font-size: 14px;
    }
  }
}

// ============================================
// 内容区域
// ============================================
.main-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  position: relative;
  background: $gray-100;
}

// ============================================
// 过渡动画
// ============================================
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all $transition-base;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

.page-enter-active,
.page-leave-active {
  transition: all $transition-base;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
