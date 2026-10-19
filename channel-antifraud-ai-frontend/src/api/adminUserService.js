import { clearSession, getSession } from './userService'

async function request(url, options = {}) {
  const token = getSession()?.token
  const headers = { ...options.headers }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(url, { ...options, headers })
  if (response.status === 401) {
    clearSession()
    window.location.reload()
    throw new Error('登录状态已失效，请重新登录')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  return body
}

export function listUsers(keyword = '') {
  const params = new URLSearchParams()
  if (keyword.trim()) params.set('keyword', keyword.trim())
  const query = params.toString()
  return request(`/api/admin/users${query ? `?${query}` : ''}`)
}

function toFormData(payload) {
  const form = new FormData()
  form.append('username', payload.username)
  form.append('phone', payload.phone)
  form.append('role', payload.role)
  ;(payload.dataSources || []).forEach((dataSource) => form.append('dataSources', dataSource))
  if (payload.password) form.append('password', payload.password)
  if (payload.avatarFile) form.append('avatarFile', payload.avatarFile)
  return form
}

export function createUser(payload) {
  return request('/api/admin/users', { method: 'POST', body: toFormData(payload) })
}

export function updateUser(id, payload) {
  return request(`/api/admin/users/${id}`, { method: 'PUT', body: toFormData(payload) })
}

export function deleteUser(id) {
  return request(`/api/admin/users/${id}`, { method: 'DELETE' })
}
