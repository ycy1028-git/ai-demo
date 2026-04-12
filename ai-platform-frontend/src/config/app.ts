/**
 * 应用配置
 */

/**
 * API 配置
 */
export const API_CONFIG = {
  /** 后端 API 基础路径 */
  baseUrl: import.meta.env.VITE_API_BASE_URL || '/api',

  /** 请求超时时间（毫秒） */
  timeout: 30000,

  /** 是否启用调试模式 */
  debug: import.meta.env.DEV
}
