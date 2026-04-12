import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/store'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 添加 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 根据业务状态码处理
    if (res.code !== 200 && res.code !== undefined) {
      ElMessage.error(res.message || '请求失败')

      // token 过期，跳转登录
      if (res.code === 401 || res.code === 403) {
        handleAuthError()
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return response
  },
  (error) => {
    console.error('请求错误:', error)

    if (error.response) {
      const { status, data } = error.response

      switch (status) {
        case 401:
          handleAuthError()
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(data?.message || '请求失败')
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error('请求配置错误')
    }

    return Promise.reject(error)
  }
)

// 处理认证错误（Token 过期）
function handleAuthError() {
  // 清除本地存储
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')

  // 尝试清除 Pinia store
  try {
    const userStore = useUserStore()
    userStore.$reset()
  } catch (e) {
    console.error('清除用户状态失败', e)
  }

  // Token 过期后直接跳转到登录页
  ElMessage.warning('登录已过期，请重新登录')
  router.push('/login')
}

export default request
