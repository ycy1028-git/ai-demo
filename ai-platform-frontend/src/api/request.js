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
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
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
    // 检查后端返回的业务状态码
    if (res.code && res.code !== 200) {
      // 认证相关错误码：401、2001、2002、2003、2004
      if (res.code === 401 || res.code === 2001 || res.code === 2002 || res.code === 2003 || res.code === 2004) {
        handleAuthError(res.message)
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          handleAuthError()
          break
        case 403:
          ElMessage.error('没有权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(error.response.data?.message || error.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

// 处理认证错误（Token 过期或无效）
function handleAuthError(message) {
  localStorage.removeItem('token')
  try {
    const userStore = useUserStore()
    userStore.$reset()
  } catch (e) {
    console.error('清除用户状态失败', e)
  }
  // 使用后端返回的错误消息，如果没有则使用默认消息
  const errorMsg = message || '登录已过期，请重新登录'
  ElMessage.warning(errorMsg)
  // 延迟跳转，让用户看到提示信息
  setTimeout(() => {
    router.push('/login')
  }, 1500)
}

export default request
