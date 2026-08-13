/** 后端处理流程，仅用于前端加载状态展示。 */
export const PIPELINE_STEPS = [
  { key: 'understand', label: 'LLM 理解业务语义' },
  { key: 'sql_gen', label: '生成结构化 SQL AST' },
  { key: 'sql_check', label: 'AST 白名单校验' },
  { key: 'query', label: 'MySQL 执行查询' },
  { key: 'analyze', label: '整理查询结果' },
]

/**
 * 调用真实 Spring Boot 后端。
 * @param {string} question 用户问题
 * @param {(stepKey: string) => void} [onStep] 流程进度回调
 */
export async function askQuestion(question, database, onStep) {
  onStep?.('understand')

  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, database }),
  })

  onStep?.('query')

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }

  onStep?.('analyze')
  return body
}