import request from './request'

/**
 * 用户登录
 * @param {Object} data - 登录参数 { username, password }
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 获取当前用户信息
 */
export function getUserInfo() {
  return request.get('/auth/current')
}

/**
 * 用户登出
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 获取用户列表
 * @param {Object} params - 查询参数
 */
export function getUserList(params) {
  return request.get('/system/user', { params })
}

/**
 * 创建用户
 * @param {Object} data - 用户数据
 */
export function createUser(data) {
  return request.post('/system/user', data)
}

/**
 * 更新用户
 * @param {number} id - 用户ID
 * @param {Object} data - 用户数据
 */
export function updateUser(id, data) {
  return request.put(`/system/user/${id}`, data)
}

/**
 * 删除用户
 * @param {number} id - 用户ID
 */
export function deleteUser(id) {
  return request.delete(`/system/user/${id}`)
}
