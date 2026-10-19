import { useState } from 'react'
import { Segmented, Table, Typography, Empty } from 'antd'
import {
  CopyOutlined, DatabaseOutlined, LoadingOutlined,
  ReadOutlined, MessageOutlined, QuestionCircleOutlined,
} from '@ant-design/icons'
import ChartView from './ChartView'
import { formatSql } from '../utils/formatSql'
import BrandIcon from './BrandIcon'

const { Paragraph } = Typography

const TOOL_STATUS = {
  route_chat_intent: '正在识别请求意图',
  search_knowledge_base: '正在检索企业知识库',
  generate_general_answer: '正在组织回答',
  retrieve_business_knowledge: '正在检索业务口径',
  generate_sql_query_plan: '正在生成查询计划',
  validate_and_compile_sql: '正在校验查询计划',
  execute_database_query: '正在执行数据库查询',
  select_result_chart: '正在整理查询结果',
}

function currentAgentStatus(events = []) {
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const event = events[index]
    if (event.type === 'TOOL_CALL_START') {
      return TOOL_STATUS[event.toolCallName] || '正在调用工具'
    }
    if (event.type === 'REASONING_MESSAGE_CONTENT') {
      return event.delta || '正在思考'
    }
    if (event.type === 'RUN_STARTED') return '正在思考'
  }
  return '正在思考'
}

function AgentStatusLine({ events }) {
  return (
    <div className="agent-status-line" role="status" aria-live="polite">
      <LoadingOutlined className="agent-status-spinner" spin />
      <span className="agent-status-text">{currentAgentStatus(events)}</span>
    </div>
  )
}

const RESPONSE_META = {
  DATA_QUERY: { label: '数据查询', icon: <DatabaseOutlined /> },
  KNOWLEDGE_QA: { label: '知识库回答', icon: <ReadOutlined /> },
  GENERAL_CHAT: { label: '通用回答', icon: <MessageOutlined /> },
  CLARIFICATION: { label: '需要补充', icon: <QuestionCircleOutlined /> },
}

function ResultContent({ result }) {
  const hasChart = !!result.chart
  const hasExecutedQuery = typeof result.sql === 'string' && result.sql.trim().length > 0
  const formattedSql = hasExecutedQuery ? formatSql(result.sql) : ''
  const [view, setView] = useState(hasChart ? 'chart' : 'table')
  const responseType = result.responseType || (hasExecutedQuery ? 'DATA_QUERY' : 'GENERAL_CHAT')
  const responseMeta = RESPONSE_META[responseType] || RESPONSE_META.GENERAL_CHAT
  return <div>
    <div className={`response-type response-type-${responseType.toLowerCase()}`}>
      {responseMeta.icon}{responseMeta.label}
    </div>
    <div className="result-summary">{result.summary}</div>
    {!!result.sources?.length && <div className="answer-sources">
      <div className="answer-sources-title">参考资料</div>
      <div className="answer-source-list">{result.sources.map((source, index) =>
        <div className="answer-source" key={source.id || `${source.title}-${index}`}>
          <span className="answer-source-index">{index + 1}</span>
          <div className="answer-source-copy">
            <div className="answer-source-name">{source.title || '未命名资料'}</div>
            <div className="answer-source-path">{source.source || '知识库'}</div>
          </div>
        </div>)}</div>
    </div>}
    {hasExecutedQuery && <div className="result-panel">
      <Segmented value={view} onChange={setView} options={[
        { label: '数据表格', value: 'table' },
        ...(hasChart ? [{ label: '图表分析', value: 'chart' }] : []),
        { label: '生成 SQL', value: 'sql' },
      ]} style={{ marginBottom: 12 }} />
      {view === 'table' && (result.table?.rows?.length
        ? <Table size="small" columns={result.table.columns} dataSource={result.table.rows}
          pagination={result.table.rows.length > 10 ? { pageSize: 10 } : false} scroll={{ x: true }} />
        : <Empty description="查询无数据" />)}
      {view === 'chart' && hasChart && <div>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>{result.chart.title}</div>
        <ChartView option={result.chart.option} />
      </div>}
      {view === 'sql' && <div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 6 }}>
          <Paragraph copyable={{ text: result.sql, icon: <CopyOutlined /> }} style={{ margin: 0 }} />
        </div>
        <div className="sql-block">{formattedSql}</div>
      </div>}
    </div>}
  </div>
}

export default function AssistantMessage({ message }) {
  const hasChart = !!message.result?.chart
  return <div className={`msg-row${hasChart ? ' has-chart' : ''}${message.loading ? ' agent-loading-row' : ''}`}>
    <div className="msg-avatar ai"><BrandIcon /></div>
    <div className="msg-content"><div className="msg-bubble-ai">
      {message.loading
        ? <AgentStatusLine events={message.streamEvents} />
        : message.error
          ? <span style={{ color: '#f5222d' }}>{message.error}</span>
          : <ResultContent result={message.result} />}
    </div></div>
  </div>
}
