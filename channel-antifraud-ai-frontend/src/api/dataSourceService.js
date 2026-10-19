import { clearSession, getSession } from './userService'

async function request(url, body) {
  const token = getSession()?.token
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })
  if (response.status === 401) {
    clearSession()
    window.location.reload()
    throw new Error('登录状态已失效，请重新登录')
  }
  const result = await response.json().catch(() => null)
  if (!response.ok) throw new Error(result?.message || `请求失败（HTTP ${response.status}）`)
  return result
}

export function testDataSource(payload) {
  return request('/api/data-source-connections/test', payload)
}

export function connectDataSource(payload) {
  return request('/api/data-source-connections', payload)
}
