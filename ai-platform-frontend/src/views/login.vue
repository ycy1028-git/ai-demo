<template>
  <div class="login-container">
    <div class="login-card">
      <div class="card-left">
        <div class="left-content">
          <h1>AI 智能助手</h1>
          <p>基于知识库的智能问答系统</p>
          <div class="features">
            <div class="feature-item">
              <el-icon :size="20"><Document /></el-icon>
              <span>智能知识库检索</span>
            </div>
            <div class="feature-item">
              <el-icon :size="20"><ChatDotRound /></el-icon>
              <span>自然语言对话</span>
            </div>
            <div class="feature-item">
              <el-icon :size="20"><Connection /></el-icon>
              <span>多模型支持</span>
            </div>
          </div>
        </div>
      </div>

      <div class="card-right">
        <div class="form-container">
          <h2>用户登录</h2>
          <p class="form-subtitle">请输入您的账号信息</p>

          <el-form
            ref="formRef"
            :model="loginForm"
            :rules="rules"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                :prefix-icon="User"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form>

          <div class="form-footer">
            <span>默认账号：</span>
            <code>admin / admin123</code>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Document, ChatDotRound, Connection } from '@element-plus/icons-vue'
import { useUserStore } from '@/store'
import { login } from '@/api/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await login(loginForm)
      const data = res.data || res
      userStore.setToken(data.token)
      userStore.setUserInfo({
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        roleId: data.roleId,
        roleCode: data.roleCode,
        roleName: data.roleName,
        admin: !!data.admin,
        avatar: ''
      })
      userStore.setPermissions(data.menuPermissions || [])
      ElMessage.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
      router.push(redirect)
    } catch (error) {
      ElMessage.error(error.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.login-card {
  display: flex;
  width: 900px;
  max-width: 90vw;
  min-height: 500px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.card-left {
  width: 360px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 48px;
  display: flex;
  align-items: center;
  justify-content: center;

  .left-content {
    text-align: center;
    color: #fff;

    h1 {
      font-size: 28px;
      font-weight: 700;
      margin-bottom: 12px;
    }

    p {
      font-size: 14px;
      opacity: 0.9;
      margin-bottom: 40px;
    }

    .features {
      display: flex;
      flex-direction: column;
      gap: 16px;
      text-align: left;

      .feature-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 16px;
        background: rgba(255, 255, 255, 0.15);
        border-radius: 8px;
        font-size: 14px;
      }
    }
  }
}

.card-right {
  flex: 1;
  padding: 48px;
  display: flex;
  align-items: center;
  justify-content: center;

  .form-container {
    width: 100%;
    max-width: 320px;

    h2 {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
    }

    .form-subtitle {
      font-size: 14px;
      color: #909399;
      margin-bottom: 32px;
    }

    :deep(.el-input) {
      .el-input__wrapper {
        border-radius: 8px;
        padding: 4px 14px;
      }
    }

    .login-btn {
      width: 100%;
      height: 44px;
      border-radius: 8px;
      font-size: 16px;
      margin-top: 16px;
    }

    .form-footer {
      margin-top: 24px;
      text-align: center;
      font-size: 13px;
      color: #909399;

      code {
        padding: 4px 10px;
        background: #f5f7fa;
        border-radius: 4px;
        color: #606266;
        font-family: monospace;
      }
    }
  }
}
</style>
