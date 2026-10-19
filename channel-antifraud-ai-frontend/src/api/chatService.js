import { clearSession, getSession } from './userService'

export async function askQuestionStream(
  question,
  database,
  connectionId,
  conversationId,
  replaceFromMessageId,
  useKnowledgeBase,
  onEvent,
) {
  const persistedMessageId = Number.isSafeInteger(replaceFromMessageId) && replaceFromMessageId > 0
    ? replaceFromMessageId : null
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify({
      conversationId,
      replaceFromMessageId: persistedMessageId,
      question,
      database,
      connectionId,
      useKnowledgeBase,
    }),
  })
  throwIfUnauthorized(response)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) throw new Error('当前浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let turn = null
  let streamError = null

  const consumeFrame = (frame) => {
    const data = frame.split('\n').filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart()).join('\n')
    if (!data) return
    const event = JSON.parse(data)
    onEvent?.(event)
    if (event.type === 'RUN_FINISHED') turn = event.result
    if (event.type === 'RUN_ERROR') {
      streamError = new Error(event.message || '智能问答处理失败')
      streamError.code = event.code
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    buffer = buffer.replaceAll('\r\n', '\n').replaceAll('\r', '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      consumeFrame(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  if (buffer.trim()) consumeFrame(buffer)
  if (streamError) throw streamError
  if (!turn) throw new Error('事件流已结束，但没有收到查询结果')
  return turn
}

export async function listConversations() {
  return parseResponse(await fetch('/api/conversations', { headers: authHeaders() }))
}

export async function getConversation(conversationId) {
  return parseResponse(await fetch(`/api/conversations/${conversationId}`, { headers: authHeaders() }))
}

export async function deleteConversation(conversationId) {
  const response = await fetch(`/api/conversations/${conversationId}`, { method: 'DELETE', headers: authHeaders() })
  throwIfUnauthorized(response)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `删除失败（HTTP ${response.status}）`)
  }
}

async function parseResponse(response) {
  throwIfUnauthorized(response)
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  return body
}

function authHeaders(extra = {}) {
  const token = getSession()?.token
  return token ? { ...extra, Authorization: `Bearer ${token}` } : extra
}

function throwIfUnauthorized(response) {
  if (response.status === 401) {
    clearSession()
    window.location.reload()
    throw new Error('登录状态已失效，请重新登录')
  }
}
