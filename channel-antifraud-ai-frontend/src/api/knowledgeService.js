/**
 * 知识库管理接口封装（RAG 检索知识的新增/更新/检索/删除）。
 * 后端接口约定：
 *   POST   /api/knowledge          新增或更新（携带 id 时覆盖同 id 文档）
 *   GET    /api/knowledge/search   混合检索（BM25 + 向量），question 必填
 *   DELETE /api/knowledge/{id}     删除
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

/** 混合检索知识；question 必填，database 取值 all/mysql/hive。 */
export function searchKnowledge(question, database = 'all') {
  const params = new URLSearchParams({ question, database })
  return request(`/api/knowledge/search?${params.toString()}`).then((result) => {
    const maxScore = Number(result?.theoreticalMaxRrfScore) || 0
    return (result?.hits || []).map((hit) => {
      const rawScore = Number(hit.score) || 0
      const relevancePercent = maxScore > 0
        ? Math.min(100, Math.max(0, (rawScore / maxScore) * 100))
        : 0
      return { ...hit, relevancePercent }
    })
  })
}

/** 新增知识；payload.id 存在时为覆盖更新。 */
export function saveKnowledge(payload) {
  return request('/api/knowledge', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** 按 id 删除知识。 */
export function deleteKnowledge(id) {
  return request(`/api/knowledge/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/** 上传并构建 md/txt/pdf/doc/docx 知识文件。 */
export function uploadKnowledgeFile({ file, database, knowledgeType, tableNames, keywords, source }) {
  const form = new FormData()
  form.append('file', file)
  form.append('database', database || 'all')
  form.append('knowledgeType', knowledgeType || 'GENERAL')
  if (source) form.append('source', source)
  ;(tableNames || []).forEach((value) => form.append('tableNames', value))
  ;(keywords || []).forEach((value) => form.append('keywords', value))
  return request('/api/knowledge/files', { method: 'POST', body: form })
}

export function listKnowledgeFiles(database = 'all') {
  const params = new URLSearchParams({ database })
  return request(`/api/knowledge/files?${params.toString()}`)
}

export function rebuildKnowledgeFile(id) {
  return request(`/api/knowledge/files/${encodeURIComponent(id)}/rebuild`, { method: 'POST' })
}

export function deleteKnowledgeFile(id) {
  return request(`/api/knowledge/files/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
