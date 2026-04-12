import request from './request'

/**
 * 发送聊天消息
 * @param {Object} data - 聊天参数 { appType, message, sessionId }
 */
export function sendChatMessage(data) {
  return request.post('/app/conversation/send', data)
}

/**
 * 流式发送聊天消息（EventSource方式）
 * @param {Object} params - 参数 { assistantCode, sessionId, message }
 */
export function streamChatMessage(params) {
  const query = new URLSearchParams({
    assistantCode: params.assistantCode,
    message: params.message
  })
  if (params.sessionId) {
    query.append('sessionId', params.sessionId)
  }
  return new EventSource(`/app/chat/stream?${query.toString()}`)
}

/**
 * 流式发送聊天消息（fetch方式，更可靠）
 */
export function streamChatMessageFetch(params, onMessage, onError, onDone) {
  const controller = new AbortController()
  const query = new URLSearchParams({
    assistantCode: params.assistantCode,
    message: params.message
  })
  if (params.sessionId) {
    query.append('sessionId', params.sessionId)
  }

  // 从 store 获取 token
  const userStore = useUserStore()
  const token = userStore.token || localStorage.getItem('token')

  fetch(`/app/chat/stream?${query.toString()}`, {
    method: 'GET',
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Authorization': token ? `Bearer ${token}` : ''
    },
    signal: controller.signal
  })
    .then(response => {
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            if (buffer) processLine(buffer)
            if (onDone) onDone()
            return
          }
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop()
          lines.forEach(line => processLine(line))
          read()
        })
      }

      function processLine(line) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (!data || data === '[DONE]') return
          try {
            const event = parseSseData(data)
            if (event && onMessage) onMessage(event)
          } catch (e) {
            console.error('解析SSE数据失败:', e, data)
          }
        }
      }

      read()
    })
    .catch(error => {
      if (error.name !== 'AbortError') {
        if (onError) onError(error.message || '请求失败')
      }
      if (onDone) onDone()
    })

  return controller
}

function parseSseData(data) {
  const parts = data.split(':')
  if (parts.length >= 2) {
    return { event: parts[0].trim(), data: parts.slice(1).join(':').trim() }
  }
  return { event: 'unknown', data }
}

/**
 * 获取聊天历史
 */
export function getChatHistory(sessionId) {
  return request.get(`/app/conversation/session/${sessionId}/messages`)
}

/**
 * 获取会话列表
 */
export function getSessionList(appType) {
  return request.get('/app/conversation/sessions')
}

/**
 * 创建新会话
 */
export function createSession(appType) {
  return request.post('/app/conversation/session', { assistantCode: appType })
}

/**
 * 删除会话
 */
export function deleteSession(sessionId) {
  return request.delete(`/app/conversation/session/${sessionId}`)
}
