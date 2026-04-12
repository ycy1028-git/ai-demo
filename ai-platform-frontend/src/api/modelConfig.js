import request from './request'

/**
 * 获取模型配置列表（支持分页和搜索）
 * @param {Object} params - 查询参数
 * @param {number} params.page - 页码
 * @param {number} params.size - 每页数量
 * @param {string} params.name - 模型名称（模糊搜索）
 * @param {string} params.provider - 提供商
 * @param {boolean} params.enabled - 启用状态
 */
export function getModelConfigList(params = {}) {
  return request.get('/ai/models', { params })
}

/**
 * 获取启用的模型列表
 */
export function getEnabledModels() {
  return request.get('/ai/models/enabled')
}

/**
 * 获取模型详情
 */
export function getModelConfigById(id) {
  return request.get(`/ai/models/${id}`)
}

/**
 * 创建模型配置
 */
export function createModelConfig(data) {
  return request.post('/ai/models', data)
}

/**
 * 更新模型配置
 */
export function updateModelConfig(id, data) {
  return request.put(`/ai/models/${id}`, data)
}

/**
 * 删除模型配置
 */
export function deleteModelConfig(id) {
  return request.delete(`/ai/models/${id}`)
}

/**
 * 设置默认模型
 */
export function setDefaultModel(id) {
  return request.put(`/ai/models/${id}/default`)
}

/**
 * 测试模型连接
 */
export function testModelConnection(id) {
  return request.post(`/ai/models/${id}/test`)
}
