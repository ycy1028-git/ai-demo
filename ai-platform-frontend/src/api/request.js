import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/store'
import { isTokenExpired } from '@/utils/token'

let redirectingToLogin = false

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
      if (isTokenExpired(userStore.token)) {
        handleAuthError('登录已过期，请重新登录')
        return Promise.reject(new Error('Token expired'))
      }
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
export function handleAuthError(message) {
  if (redirectingToLogin) {
    return
  }

  redirectingToLogin = true
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')

  try {
    const userStore = useUserStore()
    if (typeof userStore.logout === 'function') {
      userStore.logout()
    } else {
      userStore.$reset()
    }
  } catch (e) {
    console.error('清除用户状态失败', e)
  }

  const errorMsg = message || '登录已过期，请重新登录'
  ElMessage.warning(errorMsg)

  const currentPath = router.currentRoute.value?.fullPath || '/'
  if (router.currentRoute.value?.path !== '/login') {
    router.replace({
      path: '/login',
      query: { redirect: currentPath }
    }).finally(() => {
      redirectingToLogin = false
    })
    return
  }

  redirectingToLogin = false
}

export default request
