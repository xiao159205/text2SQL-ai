import { useState } from 'react'
import { Segmented, Table, Typography, Empty } from 'antd'
import {
  CheckCircleFilled,
  LoadingOutlined,
  CopyOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import ChartView from './ChartView'
import { PIPELINE_STEPS } from '../api/chatService'
import { formatSql } from '../utils/formatSql'

const { Paragraph } = Typography

/** 流程步骤进度（问答进行中展示） */
function StepProgress({ currentStep }) {
  const activeIdx = PIPELINE_STEPS.findIndex((s) => s.key === currentStep)
  return (
    <div className="step-list">
      {PIPELINE_STEPS.map((step, i) => {
        const done = i < activeIdx
        const active = i === activeIdx
        return (
          <div
            key={step.key}
            className={`step-item${done ? ' done' : ''}${active ? ' active' : ''}`}
          >
            {done ? <CheckCircleFilled /> : active ? <LoadingOutlined spin /> : <span style={{ width: 14 }} />}
            {step.label}
            {done ? '' : active ? '...' : ''}
          </div>
        )
      })}
    </div>
  )
}

/** 查询结果：AI总结 + 表格/图表/SQL 切换 */
function ResultContent({ result }) {
  const hasChart = !!result.chart
  const hasExecutedQuery = typeof result.sql === 'string' && result.sql.trim().length > 0
  const formattedSql = hasExecutedQuery ? formatSql(result.sql) : ''
  const [view, setView] = useState(hasChart ? 'chart' : 'table')

  return (
    <div>
      <div className="result-summary">{result.summary}</div>

      {hasExecutedQuery && (
      <div className="result-panel">
        <Segmented
          value={view}
          onChange={setView}
          options={[
            { label: '数据表格', value: 'table' },
            ...(hasChart ? [{ label: '图表分析', value: 'chart' }] : []),
            { label: '生成 SQL', value: 'sql' },
          ]}
          style={{ marginBottom: 12 }}
        />

        {view === 'table' &&
          (result.table?.rows?.length ? (
            <Table
              size="small"
              columns={result.table.columns}
              dataSource={result.table.rows}
              pagination={result.table.rows.length > 10 ? { pageSize: 10 } : false}
              scroll={{ x: true }}
            />
          ) : (
            <Empty description="查询无数据" />
          ))}

        {view === 'chart' && hasChart && (
          <div>
            <div style={{ fontWeight: 600, marginBottom: 8 }}>{result.chart.title}</div>
            <ChartView option={result.chart.option} />
          </div>
        )}

        {view === 'sql' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 6 }}>
              <Paragraph copyable={{ text: result.sql, icon: <CopyOutlined /> }} style={{ margin: 0 }} />
            </div>
            <div className="sql-block">{formattedSql}</div>
          </div>
        )}
      </div>
      )}
    </div>
  )
}

/** 一条 AI 消息：进行中显示步骤，完成后显示结果 */
export default function AssistantMessage({ message }) {
  const hasChart = !!message.result?.chart
  return (
    <div className={`msg-row${hasChart ? ' has-chart' : ''}`}>
      <div className="msg-avatar ai">
        <SafetyCertificateOutlined />
      </div>
      <div className="msg-content">
        <div className="msg-bubble-ai">
          {message.loading ? (
            <StepProgress currentStep={message.currentStep} />
          ) : message.error ? (
            <span style={{ color: '#f5222d' }}>{message.error}</span>
          ) : (
            <ResultContent result={message.result} />
          )}
        </div>
      </div>
    </div>
  )
}
