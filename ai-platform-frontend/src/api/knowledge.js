import request from './request'

/**
 * 获取知识库列表
 * @param {Object} params - 查询参数
 */
export function getKnowledgeBaseList(params) {
  return request.get('/kb/knowledge-base', { params })
}

/**
 * 获取知识库详情
 * @param {number} id - 知识库ID
 */
export function getKnowledgeBaseDetail(id) {
  return request.get(`/kb/knowledge-base/${id}`)
}

/**
 * 创建知识库
 * @param {Object} data - 知识库数据
 */
export function createKnowledgeBase(data) {
  return request.post('/kb/knowledge-base', data)
}

/**
 * 更新知识库
 * @param {number} id - 知识库ID
 * @param {Object} data - 知识库数据
 */
export function updateKnowledgeBase(id, data) {
  return request.put(`/kb/knowledge-base/${id}`, data)
}

/**
 * 删除知识库
 * @param {number} id - 知识库ID
 */
export function deleteKnowledgeBase(id) {
  return request.delete(`/kb/knowledge-base/${id}`)
}

/**
 * 获取知识列表
 * @param {Object} params - 查询参数
 */
export function getKnowledgeItemList(params) {
  return request.get('/kb/knowledge-item', { params })
}

/**
 * 获取知识详情
 * @param {number} id - 知识ID
 */
export function getKnowledgeItemDetail(id) {
  return request.get(`/kb/knowledge-item/${id}`)
}

/**
 * 创建知识
 * @param {Object} data - 知识数据
 */
export function createKnowledgeItem(data) {
  return request.post('/kb/knowledge-item', data)
}

/**
 * 更新知识
 * @param {number} id - 知识ID
 * @param {Object} data - 知识数据
 */
export function updateKnowledgeItem(id, data) {
  return request.put(`/kb/knowledge-item/${id}`, data)
}

/**
 * 删除知识
 * @param {number} id - 知识ID
 */
export function deleteKnowledgeItem(id) {
  return request.delete(`/kb/knowledge-item/${id}`)
}

/**
 * 上传文档
 * @param {FormData} formData - 表单数据
 */
export function uploadDocument(formData) {
  return request.post('/kb/document/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
