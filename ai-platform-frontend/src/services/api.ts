/**
 * 统一请求服务
 */
import request from '@/utils/request'

/** API 响应类型 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 创建 API 服务工厂 */
export function createApiService() {
  return {
    /**
     * 发送消息（流式）
     */
    sendMessageStream: async (
      message: string,
      options: {
        sessionId?: string
        onMessage?: (content: string) => void
        onWaiting?: (prompt: string) => void
        onError?: (error: string) => void
        onDone?: () => void
      }
    ) => {
      const { sessionId, onMessage, onWaiting, onError, onDone } = options

      const params = new URLSearchParams()
      params.append('message', message)
      if (sessionId) params.append('sessionId', sessionId)

      const token = localStorage.getItem('token') || ''

      const response = await fetch(`/api/flow/chat/stream?${params.toString()}`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Accept': 'text/event-stream'
        }
      })

      if (!response.ok) {
        throw new Error('请求失败')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()

      const processStream = async ({ done, value }: { done: boolean; value?: Uint8Array }) => {
        if (done) {
          onDone?.()
          return
        }

        const chunk = decoder.decode(value, { stream: true })
        const lines = chunk.split('\n')

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6)
            try {
              const parsed = JSON.parse(data)

              if (parsed.type === 'content' && parsed.content) {
                onMessage?.(parsed.content)
              }

              if (parsed.type === 'waiting' && parsed.prompt) {
                onWaiting?.(parsed.prompt)
              }

              if (parsed.type === 'error') {
                onError?.(parsed.message || '未知错误')
              }

              if (parsed.type === 'done') {
                onDone?.()
              }
            } catch (e) {
              // 忽略解析错误
            }
          }
        }

        return reader.read().then(processStream)
      }

      await reader.read().then(processStream)
    },

    /**
     * 获取对话上下文
     */
    getContext: async (sessionId?: string) => {
      const res = await request.get('/flow/chat/context', { params: { sessionId } })
      return res.data
    },

    /**
     * 清除对话上下文
     */
    clearContext: async (sessionId?: string) => {
      const res = await request.delete('/flow/chat/context', { params: { sessionId } })
      return res.data
    },

    /**
     * 获取所有可用技能
     */
    getSkills: async () => {
      const res = await request.get('/flow/nodes')
      return res.data
    }
  }
}

/** 导出默认 API 服务实例 */
export const apiService = createApiService()

/** 导出 API 服务工厂（可创建多个实例） */
export { createApiService as createApiServiceFactory }
