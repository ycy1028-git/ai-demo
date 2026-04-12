import request from './request'

/**
 * 获取统计数据
 */
export function getStatistics() {
  return request.get('/kb/knowledge-base/statistics')
}

/**
 * 获取调用次数统计
 * @param {Object} params - 查询参数 { startDate, endDate }
 */
export function getApiCallStats(params) {
  return request.get('/knowledge/search/statistics', { params })
}

/**
 * 获取最近活动时间
 */
export function getRecentActivities() {
  return request.get('/auth/current/activities')
}

/**
 * 获取知识库列表（用于仪表盘热门知识库展示）
 * @param {Object} params - 查询参数
 */
export function getKnowledgeBaseList(params) {
  return request.get('/kb/knowledge-base', { params })
}
