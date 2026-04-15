import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)
  const permissions = ref([])

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const isAdmin = computed(() => {
    if (userInfo.value?.admin) return true
    return permissions.value.includes('*')
  })

  // 方法
  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setPermissions(perms) {
    permissions.value = Array.isArray(perms) ? perms : []
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  function hasPermission(code) {
    if (!code) return true
    if (isAdmin.value) return true
    if (!permissions.value || permissions.value.length === 0) return true
    return permissions.value.includes(code)
  }

  function initUser() {
    const storedToken = localStorage.getItem('token')
    const storedUserInfo = localStorage.getItem('userInfo')
    const storedPermissions = localStorage.getItem('permissions')
    if (storedToken) {
      token.value = storedToken
    }
    if (storedUserInfo) {
      try {
        userInfo.value = JSON.parse(storedUserInfo)
      } catch (e) {
        console.error('解析用户信息失败', e)
      }
    }
    if (storedPermissions) {
      try {
        const parsed = JSON.parse(storedPermissions)
        permissions.value = Array.isArray(parsed) ? parsed : []
      } catch (e) {
        permissions.value = []
      }
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
  }

  return {
    token,
    userInfo,
    permissions,
    isLoggedIn,
    isAdmin,
    username,
    avatar,
    setToken,
    setUserInfo,
    setPermissions,
    hasPermission,
    initUser,
    logout
  }
})
