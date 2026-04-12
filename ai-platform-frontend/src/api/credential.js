import request from './request'

/**
 * 获取凭证列表
 * @param {Object} params - 查询参数 { keyword, page, size }
 */
export function getCredentialList(params) {
  return request.get('/admin/credentials', { params })
}

/**
 * 获取凭证详情
 * @param {string} id - 凭证ID (UUID)
 */
export function getCredentialDetail(id) {
  return request.get(`/admin/credentials/${id}`)
}

/**
 * 创建凭证
 * @param {Object} data - 凭证数据
 */
export function createCredential(data) {
  return request.post('/admin/credentials', data)
}

/**
 * 更新凭证
 * @param {string} id - 凭证ID (UUID)
 * @param {Object} data - 凭证数据
 */
export function updateCredential(id, data) {
  return request.put(`/admin/credentials/${id}`, data)
}

/**
 * 删除凭证
 * @param {string} id - 凭证ID (UUID)
 */
export function deleteCredential(id) {
  return request.delete(`/admin/credentials/${id}`)
}

/**
 * 修改凭证状态
 * @param {string} id - 凭证ID (UUID)
 * @param {number} status - 状态值
 */
export function updateCredentialStatus(id, status) {
  return request.put(`/admin/credentials/${id}/status`, null, {
    params: { status }
  })
}

/**
 * 重置凭证密钥
 * @param {string} id - 凭证ID (UUID)
 */
export function resetCredentialSecret(id) {
  return request.post(`/admin/credentials/${id}/reset-secret`)
}

/**
 * 获取凭证统计
 * @param {string} id - 凭证ID (UUID)
 */
export function getCredentialStats(id) {
  return request.get(`/admin/credentials/${id}/stats`)
}
