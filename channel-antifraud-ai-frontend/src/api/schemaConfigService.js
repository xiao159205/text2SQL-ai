/**
 * Schema 黑白名单/注释配置接口封装。
 * 黑名单模式：默认所有表/字段都会发送给大模型，visible=false 表示排除。
 */

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }
  return body
}

/** 拉取指定数据库的全部表/字段及当前配置规则。 */
export function fetchSchemaTree(database) {
  return request(`/api/schema-config/tree?database=${database}`)
}

/** 保存表规则；MySQL 下仅在提供 comment 时修改真实表注释。 */
export function saveTableRule(database, tableName, { visible, comment }) {
  return request(`/api/schema-config/tables/${encodeURIComponent(tableName)}`, {
    method: 'PUT',
    body: JSON.stringify({ database, visible, comment }),
  })
}

/** 保存字段规则；MySQL 下仅在提供 comment 时修改真实字段注释。 */
export function saveFieldRule(database, tableName, fieldName, { visible, comment }) {
  return request(
    `/api/schema-config/tables/${encodeURIComponent(tableName)}/fields/${encodeURIComponent(fieldName)}`,
    {
      method: 'PUT',
      body: JSON.stringify({ database, visible, comment }),
    },
  )
}

/** 预览当前生效的、发给大模型的 Schema 文本。 */
export function fetchPromptPreview(database) {
  return request(`/api/schema-config/prompt-preview?database=${database}`)
}
