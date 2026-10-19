/**
 * 系统提示词配置接口封装（Text2SQL 查询规划器 System Prompt 的读取/保存/恢复默认）。
 * 后端接口约定：
 *   GET  /api/prompt?database=mysql|hive        读取当前生效提示词与内置默认提示词
 *   PUT  /api/prompt                            保存某数据库类型的提示词
 *   POST /api/prompt/reset?database=mysql|hive  恢复为内置默认提示词
 * 响应结构约定：
 *   GET  返回 { database, content, defaultContent, updatedAt }
 * 提示词内容支持占位符：{schema} 实时Schema、{format} 输出格式约束、{maxRows} 最大返回行数。
 */
import { clearSession, getSession } from './userService'

async function request(url, options = {}) {
  const token = getSession()?.token
  const headers = { ...options.headers }
  if (!(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(url, { ...options, headers })
  if (response.status === 401) {
    clearSession()
    window.location.reload()
    throw new Error('登录状态已失效，请重新登录')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }
  return body
}

/** 读取指定数据库类型的系统提示词；database 取值 mysql/hive。 */
export function getPrompt(database) {
  const params = new URLSearchParams({ database })
  return request(`/api/prompt?${params.toString()}`)
}

/** 保存指定数据库类型的系统提示词。 */
export function savePrompt(database, content) {
  return request('/api/prompt', {
    method: 'PUT',
    body: JSON.stringify({ database, content }),
  })
}

/** 恢复指定数据库类型的提示词为后端内置默认值。 */
export function resetPrompt(database) {
  const params = new URLSearchParams({ database })
  return request(`/api/prompt/reset?${params.toString()}`, { method: 'POST' })
}
