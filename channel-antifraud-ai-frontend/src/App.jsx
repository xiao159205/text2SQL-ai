import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Input, Segmented, Tooltip } from 'antd'
import {
  SafetyCertificateOutlined,
  ArrowUpOutlined,
  PlusOutlined,
  DoubleLeftOutlined,
  DoubleRightOutlined,
  UserOutlined,
  CopyOutlined,
  EditOutlined,
  MessageOutlined,
  DatabaseOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import Welcome from './components/Welcome'
import AssistantMessage from './components/AssistantMessage'
import SchemaConfigPage from './pages/SchemaConfigPage'
import AuthPage from './pages/AuthPage'
import { askQuestion } from './api/chatService'
import { getSession, clearSession } from './api/userService'
import { resolveAvatar } from './utils/avatars'

function UserAvatar({ avatarPath, className = 'msg-avatar user' }) {
  const url = resolveAvatar(avatarPath)
  if (!url) {
    return (
      <div className={className}>
        <UserOutlined />
      </div>
    )
  }
  return <img className={`${className} avatar-img`} src={url} alt="用户头像" />
}

let msgId = 0
const nextId = () => ++msgId

function ChatComposer({ input, setInput, asking, onSend, centered = false }) {
  return (
    <footer className={`chat-footer${centered ? ' centered' : ''}`}>
      <div className="chat-input-wrap">
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入你的问题，例如：近30天各渠道欺诈率排行（Enter 发送，Shift+Enter 换行）"
          autoSize={{ minRows: 1, maxRows: 5 }}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault()
              onSend()
            }
          }}
        />
        <Button
          className="chat-send-button"
          type="primary"
          shape="circle"
          aria-label="发送"
          icon={<ArrowUpOutlined />}
          loading={asking}
          disabled={!input.trim() || asking}
          onClick={onSend}
        />
      </div>
    </footer>
  )
}

export default function App() {
  const [user, setUser] = useState(getSession())
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [asking, setAsking] = useState(false)
  const [collapsed, setCollapsed] = useState(false)
  const [view, setView] = useState('chat')
  const [database, setDatabase] = useState('mysql')
  const [editingId, setEditingId] = useState(null)
  const [editText, setEditText] = useState('')
  const [copiedId, setCopiedId] = useState(null)
  const bodyRef = useRef(null)

  // 新消息到达时滚动到底部
  useEffect(() => {
    const el = bodyRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages])

  const send = useCallback(
    async (question, replaceFromId = null) => {
      const text = (question ?? input).trim()
      if (!text || asking) return

      setInput('')
      setAsking(true)

      const aiId = nextId()
      const userId = replaceFromId ?? nextId()
      setMessages((prev) => {
        const editIndex = replaceFromId == null
          ? -1
          : prev.findIndex((message) => message.id === replaceFromId)
        const base = editIndex >= 0 ? prev.slice(0, editIndex) : prev
        return [
          ...base,
          { id: userId, role: 'user', content: text },
          { id: aiId, role: 'assistant', loading: true, currentStep: 'understand' },
        ]
      })
      setEditingId(null)
      setEditText('')

      try {
        const result = await askQuestion(text, database, (stepKey) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === aiId ? { ...m, currentStep: stepKey } : m)),
          )
        })
        setMessages((prev) =>
          prev.map((m) => (m.id === aiId ? { ...m, loading: false, result } : m)),
        )
      } catch (e) {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === aiId
              ? { ...m, loading: false, error: `查询失败：${e.message || '服务异常，请稍后重试'}` }
              : m,
          ),
        )
      } finally {
        setAsking(false)
      }
    },
    [input, asking, database],
  )
  const copyQuestion = useCallback(async (message) => {
    try {
      await navigator.clipboard.writeText(message.content)
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = message.content
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      textarea.remove()
    }
    setCopiedId(message.id)
    window.setTimeout(() => setCopiedId(null), 1500)
  }, [])

  const startEditing = useCallback((message) => {
    if (asking) return
    setEditingId(message.id)
    setEditText(message.content)
  }, [asking])

  const cancelEditing = useCallback(() => {
    setEditingId(null)
    setEditText('')
  }, [])

  const handleLogout = useCallback(() => {
    clearSession()
    setUser(null)
    setMessages([])
    setView('chat')
  }, [])

  // 未登录时只渲染登录 / 注册页
  if (!user) {
    return <AuthPage onLogin={setUser} />
  }

  return (
    <div className="app-layout">
      {/* 侧边栏：折叠时只留图标窄栏 */}
      <aside className={`app-sider${collapsed ? ' collapsed' : ''}`}>
        {collapsed ? (
          <>
            <Tooltip title="展开侧边栏" placement="right">
              <div
                className="app-brand-icon expandable"
                role="button"
                aria-label="展开侧边栏"
                onClick={() => setCollapsed(false)}
              >
                <SafetyCertificateOutlined className="brand-icon-default" />
                <DoubleRightOutlined className="brand-icon-hover" />
              </div>
            </Tooltip>
            <Tooltip title="开启新会话" placement="right">
              <Button
                type="text"
                icon={<PlusOutlined />}
                onClick={() => setMessages([])}
              />
            </Tooltip>
            <Tooltip title="智能问答" placement="right">
              <Button
                type="text"
                icon={<MessageOutlined />}
                onClick={() => setView('chat')}
              />
            </Tooltip>
            <Tooltip title="Schema 配置" placement="right">
              <Button
                type="text"
                icon={<DatabaseOutlined />}
                onClick={() => setView('schema')}
              />
            </Tooltip>
            <div className="sider-footer">
              <Tooltip title={user.username} placement="right">
                <span>
                  <UserAvatar avatarPath={user.avatarPath} className="msg-avatar user sider-avatar" />
                </span>
              </Tooltip>
              <Tooltip title="退出登录" placement="right">
                <Button
                  type="text"
                  icon={<LogoutOutlined />}
                  onClick={handleLogout}
                />
              </Tooltip>
            </div>
          </>
        ) : (
          <>
            <div className="app-brand">
              <div className="app-brand-icon">
                <SafetyCertificateOutlined />
              </div>
              <div style={{ flex: 1 }}>
                <div className="app-brand-title">渠道反欺诈 AI</div>
                <div className="app-brand-sub">一句话找客群</div>
              </div>
              <Button
                type="text"
                size="small"
                icon={<DoubleLeftOutlined />}
                onClick={() => setCollapsed(true)}
              />
            </div>
            <Button
              block
              icon={<PlusOutlined />}
              onClick={() => setMessages([])}
              style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: 'transparent', justifyContent: 'flex-start', display: 'flex' }}
            >
              开启新会话
            </Button>
            <Button
              block
              type={view === 'chat' ? 'default' : 'text'}
              icon={<MessageOutlined />}
              onClick={() => setView('chat')}
              style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: view === 'chat' ? '#efefef' : 'transparent', justifyContent: 'flex-start', display: 'flex' }}
            >
              智能问答
            </Button>
            <Button
              block
              type={view === 'schema' ? 'default' : 'text'}
              icon={<DatabaseOutlined />}
              onClick={() => setView('schema')}
              style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: view === 'schema' ? '#efefef' : 'transparent', justifyContent: 'flex-start', display: 'flex' }}
            >
              Schema 配置
            </Button>
            <div className="sider-footer expanded">
              <UserAvatar avatarPath={user.avatarPath} className="msg-avatar user sider-avatar" />
              <div className="sider-user-info">
                <div className="sider-user-name">{user.username}</div>
                <div className="sider-user-phone">{user.phone}</div>
              </div>
              <Tooltip title="退出登录">
                <Button
                  type="text"
                  size="small"
                  icon={<LogoutOutlined />}
                  onClick={handleLogout}
                />
              </Tooltip>
            </div>
          </>
        )}
      </aside>

      {/* 主区域：聊天页 / Schema 配置页 */}
      {view === 'schema' ? (
        <SchemaConfigPage />
      ) : (
      <main className="chat-main">
        <div className="database-switch-wrap" aria-label="数据库选择">
          <Segmented
            value={database}
            onChange={setDatabase}
            options={[
              { label: 'MySQL', value: 'mysql' },
              { label: 'Hive', value: 'hive' },
            ]}
          />
        </div>
        <div className={`chat-body${messages.length === 0 ? ' empty' : ''}`} ref={bodyRef}>
          {messages.length === 0 ? (
            <div className="empty-chat-center">
              <Welcome />
              <ChatComposer
                input={input}
                setInput={setInput}
                asking={asking}
                onSend={() => send()}
                centered
              />
            </div>
          ) : (
          <div className="chat-body-inner">
            {messages.map((m) => {
              if (m.role !== 'user') {
                return <AssistantMessage key={m.id} message={m} />
              }

              const isEditing = editingId === m.id
              return (
                <div
                  key={m.id}
                  className={`msg-row user${isEditing ? ' editing' : ''}`}
                >
                  <div className="msg-content user-message">
                    {isEditing ? (
                      <div className="msg-edit-card">
                        <Input.TextArea
                          autoFocus
                          value={editText}
                          onChange={(event) => setEditText(event.target.value)}
                          autoSize={{ minRows: 3, maxRows: 8 }}
                          onPressEnter={(event) => {
                            if (!event.shiftKey) {
                              event.preventDefault()
                              send(editText, m.id)
                            }
                          }}
                        />
                        <div className="msg-edit-actions">
                          <Button onClick={cancelEditing}>
                            {'\u53d6\u6d88'}
                          </Button>
                          <Button
                            type="primary"
                            disabled={!editText.trim()}
                            loading={asking}
                            onClick={() => send(editText, m.id)}
                          >
                            {'\u53d1\u9001'}
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <div className="msg-bubble-user">{m.content}</div>
                        <div className="msg-actions">
                          <Tooltip
                            title={copiedId === m.id ? '\u5df2\u590d\u5236' : '\u590d\u5236'}
                          >
                            <Button
                              type="text"
                              size="small"
                              aria-label={'\u590d\u5236\u95ee\u9898'}
                              icon={<CopyOutlined />}
                              onClick={() => copyQuestion(m)}
                            />
                          </Tooltip>
                          <Tooltip title={'\u7f16\u8f91'}>
                            <Button
                              type="text"
                              size="small"
                              aria-label={'\u7f16\u8f91\u95ee\u9898'}
                              icon={<EditOutlined />}
                              disabled={asking}
                              onClick={() => startEditing(m)}
                            />
                          </Tooltip>
                        </div>
                      </>
                    )}
                  </div>
                  {!isEditing && (
                    <UserAvatar avatarPath={user.avatarPath} />
                  )}
                </div>
              )
            })}
          </div>
          )}
        </div>

        {messages.length > 0 && (
          <ChatComposer
            input={input}
            setInput={setInput}
            asking={asking}
            onSend={() => send()}
          />
        )}
      </main>
      )}
    </div>
  )
}
