import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Empty,
  Input,
  Popconfirm,
  Segmented,
  Spin,
  Tooltip,
  message,
} from 'antd'
import {
  SaveOutlined,
  ReloadOutlined,
  RollbackOutlined,
  CodeOutlined,
  DatabaseOutlined,
  UndoOutlined,
} from '@ant-design/icons'
import { getPrompt, savePrompt, resetPrompt } from '../api/promptService'

const DATABASE_OPTIONS = [
  { label: 'MySQL', value: 'mysql' },
  { label: 'Hive', value: 'hive' },
]

const DATABASE_LABEL = { mysql: 'MySQL', hive: 'Hive' }

/** 后端在拼装 System Prompt 时会替换的运行时占位符 */
const PLACEHOLDERS = [
  { token: '{schema}', label: '实时 Schema', description: '保存时不要删除；运行时会被替换为当前生效的表结构描述' },
  { token: '{format}', label: '输出格式约束', description: '运行时会被替换为 SqlQueryPlan 的 JSON 结构说明' },
  { token: '{maxRows}', label: '最大返回行数', description: '运行时会被替换为配置的最大结果行数' },
]

const formatTime = (value) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'

/** 统计一段文本中各占位符出现次数 */
function countPlaceholder(content, token) {
  if (!content) return 0
  return content.split(token).length - 1
}

/**
 * 提示词配置页：在线维护后端 SqlPlanGenerator 里写死的 MySQL / Hive System Prompt。
 * 保存后下一次问答即生效，无需重启后端服务。
 */
export default function PromptConfigPage() {
  const [database, setDatabase] = useState('mysql')
  const [content, setContent] = useState('')
  const [savedContent, setSavedContent] = useState('')
  const [updatedAt, setUpdatedAt] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [loadError, setLoadError] = useState(null)

  const dirty = content !== savedContent

  const load = useCallback(async (db) => {
    setLoading(true)
    setLoadError(null)
    try {
      const data = await getPrompt(db)
      setContent(data?.content ?? '')
      setSavedContent(data?.content ?? '')
      setUpdatedAt(data?.updatedAt ?? null)
    } catch (e) {
      setContent('')
      setSavedContent('')
      setUpdatedAt(null)
      setLoadError(e.message || '服务异常')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load(database)
  }, [database, load])

  const handleSave = useCallback(async () => {
    if (!content.trim()) {
      message.warning('提示词内容不能为空')
      return
    }
    setSaving(true)
    try {
      const data = await savePrompt(database, content)
      message.success(`${DATABASE_LABEL[database]} 提示词已保存，下一次提问即生效`)
      // 后端不回包时退回本地内容；回包则以服务端落库内容为准
      const next = data?.content ?? content
      setContent(next)
      setSavedContent(next)
      setUpdatedAt(data?.updatedAt ?? new Date().toISOString())
    } catch (e) {
      message.error(`保存失败：${e.message || '服务异常'}`)
    } finally {
      setSaving(false)
    }
  }, [content, database])

  const handleReset = useCallback(async () => {
    setResetting(true)
    try {
      const data = await resetPrompt(database)
      message.success(`已恢复为内置默认的 ${DATABASE_LABEL[database]} 提示词`)
      const next = data?.content ?? ''
      setContent(next)
      setSavedContent(next)
      setUpdatedAt(data?.updatedAt ?? null)
    } catch (e) {
      message.error(`恢复默认失败：${e.message || '服务异常'}`)
    } finally {
      setResetting(false)
    }
  }, [database])

  const handleDiscard = useCallback(() => {
    setContent(savedContent)
    message.info('已放弃未保存的修改')
  }, [savedContent])

  /** 占位符校验：缺失时给出提示，防止保存后大模型拿不到 Schema / 输出格式 */
  const placeholderWarnings = useMemo(
    () => PLACEHOLDERS
      .filter(({ token }) => countPlaceholder(content, token) === 0)
      .map(({ token }) => token),
    [content],
  )

  const stats = useMemo(() => ({
    chars: content.length,
    lines: content ? content.split('\n').length : 0,
  }), [content])

  return (
    <div className="prompt-page">
      <header className="prompt-header">
        <div className="prompt-header-text">
          <h1>提示词配置</h1>
          <p>维护 Text2SQL 查询规划器的 System Prompt，保存后下一次提问即生效 · 请保留 {'{schema}'}、{'{format}'} 占位符</p>
        </div>
        <Segmented
          className="prompt-db-switch"
          value={database}
          onChange={setDatabase}
          options={DATABASE_OPTIONS}
        />
      </header>

      <div className="prompt-body">
        <div className="prompt-editor-card">
          <div className="prompt-editor-head">
            <div className="prompt-editor-title">
              <CodeOutlined />
              <span>{DATABASE_LABEL[database]} System Prompt</span>
              <span className="prompt-editor-sub">
                {stats.chars} 字符 · {stats.lines} 行 · 上次更新 {formatTime(updatedAt)}
              </span>
            </div>
            <div className="prompt-editor-actions">
              <Tooltip title="重新从服务端拉取，未保存的修改会被覆盖">
                <Button
                  icon={<ReloadOutlined />}
                  loading={loading}
                  onClick={() => load(database)}
                >
                  刷新
                </Button>
              </Tooltip>
              <Tooltip title="放弃当前修改，回到已保存内容">
                <Button
                  icon={<UndoOutlined />}
                  disabled={!dirty || loading}
                  onClick={handleDiscard}
                >
                  撤销修改
                </Button>
              </Tooltip>
              <Popconfirm
                title={`恢复默认的 ${DATABASE_LABEL[database]} 提示词？`}
                description="将覆盖当前内容，恢复为后端内置版本"
                okText="恢复默认"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={handleReset}
              >
                <Button icon={<RollbackOutlined />} danger loading={resetting} disabled={loading}>
                  恢复默认
                </Button>
              </Popconfirm>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                disabled={!dirty || loading}
                onClick={handleSave}
              >
                保存
              </Button>
            </div>
          </div>

          {placeholderWarnings.length > 0 && content && (
            <Alert
              className="prompt-warning"
              type="warning"
              showIcon
              message={`缺少占位符 ${placeholderWarnings.join('、')}`}
              description="保存后大模型将无法获取对应运行时内容（如实时 Schema 或输出格式约束），可能导致查询规划失败。"
            />
          )}

          <Spin spinning={loading}>
            {loadError ? (
              <div className="prompt-empty">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={`提示词加载失败：${loadError}，点击右上角刷新重试`}
                />
              </div>
            ) : (
              <Input.TextArea
                className="prompt-textarea"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder={`请输入 ${DATABASE_LABEL[database]} 的 System Prompt`}
                spellCheck={false}
              />
            )}
          </Spin>
        </div>

        <aside className="prompt-side">
          <div className="prompt-side-card">
            <div className="prompt-side-title">运行时占位符</div>
            <p className="prompt-side-desc">以下占位符会在发送给大模型前被替换为实际内容：</p>
            <ul className="prompt-placeholder-list">
              {PLACEHOLDERS.map(({ token, label, description }) => {
                const count = countPlaceholder(content, token)
                return (
                  <li key={token} className={`prompt-placeholder${count === 0 ? ' missing' : ''}`}>
                    <div className="prompt-placeholder-head">
                      <code>{token}</code>
                      <span className="prompt-placeholder-label">{label}</span>
                      <span className={`prompt-placeholder-count${count === 0 ? ' warn' : ''}`}>
                        {count === 0 ? '未使用' : `× ${count}`}
                      </span>
                    </div>
                    <p>{description}</p>
                  </li>
                )
              })}
            </ul>
          </div>

          <div className="prompt-side-card">
            <div className="prompt-side-title">
              <DatabaseOutlined /> 生效说明
            </div>
            <ul className="prompt-tips">
              <li>MySQL 与 Hive 各有一份提示词，互不影响，分别对应问答页右上角的数据库切换。</li>
              <li>保存后立即生效，无需重启后端服务。</li>
              <li>修改强制规则（如「只允许 SELECT」）会直接影响查询安全性，请谨慎调整。</li>
              <li>恢复默认会回到后端代码内置的提示词版本。</li>
            </ul>
          </div>
        </aside>
      </div>
    </div>
  )
}
