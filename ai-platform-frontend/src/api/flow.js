import request from './request'

// ==================== 流程模板管理 ====================

/**
 * 获取流程模板列表
 * @param {Object} params - 查询参数
 * @param {number} params.page - 页码
 * @param {number} params.pageSize - 每页数量
 * @param {string} params.keyword - 搜索关键词
 * @param {number} params.status - 状态筛选
 */
export const getFlowTemplateList = (params) => request.get('/flow/template', { params })

/**
 * 获取所有启用的流程模板（下拉框用）
 */
export const getFlowTemplateAll = () => request.get('/flow/template/all')

/**
 * 根据ID获取流程模板详情
 * @param {string} id - UUID
 */
export const getFlowTemplateById = (id) => request.get(`/flow/template/${id}`)

/**
 * 根据编码获取流程模板
 * @param {string} code - 模板编码
 */
export const getFlowTemplateByCode = (code) => request.get(`/flow/template/code/${code}`)

/**
 * 创建流程模板
 * @param {Object} data - 模板数据
 */
export const createFlowTemplate = (data) => request.post('/flow/template', data)

/**
 * 更新流程模板
 * @param {Object} data - 模板数据
 */
export const updateFlowTemplate = (data) => request.put('/flow/template', data)

/**
 * 删除流程模板
 * @param {string} id - UUID
 */
export const deleteFlowTemplate = (id) => request.delete(`/flow/template/${id}`)

/**
 * 更新模板状态
 * @param {string} id - UUID
 * @param {number} status - 状态 0-禁用 1-启用
 */
export const updateFlowTemplateStatus = (id, status) => 
  request.put(`/flow/template/${id}/status`, { status })

/**
 * 发布流程模板
 * @param {string} id - UUID
 */
export const publishFlowTemplate = (id) => request.post(`/flow/template/${id}/publish`)

// ==================== 对话接口 ====================

/**
 * 发送消息（流式）
 */
export const chatStream = (message, sessionId) => {
  return request.get('/flow/chat/stream', {
    params: { message, sessionId }
  }, { responseType: 'stream' })
}

/**
 * 获取对话上下文
 */
export const getChatContext = () => request.get('/flow/chat/context')

/**
 * 清除对话上下文
 */
export const clearChatContext = () => request.delete('/flow/chat/context')
