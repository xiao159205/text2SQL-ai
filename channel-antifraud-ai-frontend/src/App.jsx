import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, Input, Popover, Tooltip } from 'antd'
import {
  ArrowUpOutlined,
  PlusOutlined,
  DoubleLeftOutlined,
  DoubleRightOutlined,
  UserOutlined,
  CopyOutlined,
  CheckOutlined,
  EditOutlined,
  MessageOutlined,
  DatabaseOutlined,
  ReadOutlined,
  RobotOutlined,
  LogoutOutlined,
  DeleteOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import Welcome from './components/Welcome'
import AssistantMessage from './components/AssistantMessage'
import SchemaConfigPage from './pages/SchemaConfigPage'
import KnowledgePage from './pages/KnowledgePage'
import PromptConfigPage from './pages/PromptConfigPage'
import UserManagementPage from './pages/UserManagementPage'
import AuthPage from './pages/AuthPage'
import BrandIcon from './components/BrandIcon'
import DatabaseConnectionPicker from './components/DatabaseConnectionPicker'
import {
  askQuestionStream,
  deleteConversation,
  getConversation,
  listConversations,
} from './api/chatService'
import { getSession, clearSession, saveSession } from './api/userService'
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
const nextId = () => `local-${++msgId}`
const CONNECTION_KEY = 'channel-antifraud-ai-data-source'

const savedConnection = () => {
  try { return JSON.parse(sessionStorage.getItem(CONNECTION_KEY)) }
  catch { return null }
}

function ChatComposer({
  input,
  setInput,
  asking,
  onSend,
  useKnowledgeBase,
  onToggleKnowledgeBase,
  centered = false,
}) {
  return (
    <footer className={`chat-footer${centered ? ' centered' : ''}`}>
      <div className="chat-input-wrap">
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入你的问题，例如：近30天各渠道交易金额排行（Enter 发送，Shift+Enter 换行）"
          autoSize={{ minRows: 1, maxRows: 5 }}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault()
              onSend()
            }
          }}
        />
        <div className="chat-composer-actions">
          <Tooltip title={useKnowledgeBase ? '本次提问将检索知识库' : '本次提问不会检索知识库'}>
            <Button
              className={`knowledge-toggle${useKnowledgeBase ? ' active' : ''}`}
              type="text"
              size="small"
              icon={<ReadOutlined />}
              aria-label={useKnowledgeBase ? '关闭知识库检索' : '开启知识库检索'}
              aria-pressed={useKnowledgeBase}
              disabled={asking}
              onClick={onToggleKnowledgeBase}
            >
              知识库
            </Button>
          </Tooltip>
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
  const [databaseConnection, setDatabaseConnection] = useState(savedConnection)
  const [useKnowledgeBase, setUseKnowledgeBase] = useState(true)
  const [editingId, setEditingId] = useState(null)
  const [editText, setEditText] = useState('')
  const [copiedId, setCopiedId] = useState(null)
  const [conversations, setConversations] = useState([])
  const [activeConversationId, setActiveConversationId] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [accountMenuOpen, setAccountMenuOpen] = useState(false)
  const bodyRef = useRef(null)
  const isAdmin = user?.role === 'ADMIN'
  const allowedDatabaseTypes = useMemo(
    () => (isAdmin ? ['mysql', 'hive'] : (user?.dataSources || [])),
    [isAdmin, user?.dataSources],
  )

  useEffect(() => {
    if (databaseConnection && !allowedDatabaseTypes.includes(databaseConnection.databaseType)) {
      setDatabaseConnection(null)
      sessionStorage.removeItem(CONNECTION_KEY)
    }
  }, [allowedDatabaseTypes, databaseConnection])

  const selectDatabaseConnection = useCallback((connection) => {
    setDatabaseConnection(connection)
    setDatabase(connection.databaseType)
    sessionStorage.setItem(CONNECTION_KEY, JSON.stringify(connection))
    if (!activeConversationId || connection.databaseType !== database) {
      setActiveConversationId(null)
      setMessages([])
    }
  }, [activeConversationId, database])

  const refreshConversations = useCallback(async () => {
    if (!user?.id) return
    try {
      setConversations(await listConversations())
    } catch (error) {
      console.error('加载历史会话失败', error)
    }
  }, [user?.id])

  useEffect(() => {
    refreshConversations()
  }, [refreshConversations])

  const startNewConversation = useCallback(() => {
    setActiveConversationId(null)
    setMessages([])
    setEditingId(null)
    setEditText('')
    setView('chat')
  }, [])

  const openConversation = useCallback(async (conversation) => {
    if (asking || historyLoading) return
    setHistoryLoading(true)
    try {
      const detail = await getConversation(conversation.id)
      setActiveConversationId(detail.id)
      setDatabase(detail.database)
      if (databaseConnection?.databaseType !== detail.database) {
        setDatabaseConnection(null)
        sessionStorage.removeItem(CONNECTION_KEY)
      }
      setMessages(detail.messages.map((item) => (
        item.role === 'user'
          ? { id: item.id, role: 'user', content: item.content }
          : { id: item.id, role: 'assistant', loading: false, result: item.result }
      )))
      setView('chat')
    } catch (error) {
      console.error('加载会话失败', error)
    } finally {
      setHistoryLoading(false)
    }
  }, [asking, databaseConnection?.databaseType, historyLoading])

  const removeConversation = useCallback(async (event, conversationId) => {
    event.stopPropagation()
    if (asking) return
    try {
      await deleteConversation(conversationId)
      if (activeConversationId === conversationId) {
        setActiveConversationId(null)
        setMessages([])
      }
      await refreshConversations()
    } catch (error) {
      console.error('删除会话失败', error)
    }
  }, [activeConversationId, asking, refreshConversations])

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
          { id: aiId, role: 'assistant', loading: true, streamEvents: [] },
        ]
      })
      setEditingId(null)
      setEditText('')

      try {
        const turn = await askQuestionStream(
          text,
          database,
          databaseConnection?.id,
          activeConversationId,
          replaceFromId,
          useKnowledgeBase,
          (event) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === aiId
              ? { ...m, streamEvents: [...(m.streamEvents || []), event] }
              : m)),
          )
          },
        )
        const result = turn.result
        setActiveConversationId(turn.conversationId)
        setMessages((prev) =>
          prev.map((m) => {
            if (m.id === userId) return { ...m, id: turn.userMessageId }
            if (m.id === aiId) {
              return { ...m, id: turn.assistantMessageId, loading: false, result }
            }
            return m
          }),
        )
        await refreshConversations()
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
    [input, asking, database, databaseConnection?.id, activeConversationId,
      useKnowledgeBase, refreshConversations],
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
    setAccountMenuOpen(false)
    clearSession()
    setUser(null)
    setMessages([])
    setConversations([])
    setActiveConversationId(null)
    setDatabaseConnection(null)
    sessionStorage.removeItem(CONNECTION_KEY)
    setView('chat')
  }, [])

  const handleCurrentUserUpdated = useCallback((updated) => {
    setUser((current) => {
      const next = { ...current, ...updated, token: current.token }
      saveSession(next)
      return next
    })
  }, [])

  const accountMenu = (
    <div className="sider-account-menu">
      <div className="sider-account-menu-user">
        <UserAvatar avatarPath={user?.avatarPath} className="msg-avatar user sider-avatar" />
        <div className="sider-user-info">
          <div className="sider-user-name">{user?.username}</div>
          <div className="sider-user-phone">{user?.phone}</div>
          <div className="sider-user-role">{isAdmin ? '管理员' : '普通用户'}</div>
        </div>
      </div>
      <Button
        type="text"
        danger
        block
        icon={<LogoutOutlined />}
        onClick={handleLogout}
      >
        退出登录
      </Button>
    </div>
  )

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
                <BrandIcon className="brand-icon-default" />
                <DoubleRightOutlined className="brand-icon-hover" />
              </div>
            </Tooltip>
            <Tooltip title="开启新会话" placement="right">
              <Button
                type="text"
                icon={<PlusOutlined />}
                onClick={startNewConversation}
              />
            </Tooltip>
            <Tooltip title="智能问答" placement="right">
              <Button
                type="text"
                icon={<MessageOutlined />}
                onClick={() => setView('chat')}
              />
            </Tooltip>
            {isAdmin && (
              <>
                <Tooltip title="Schema 配置" placement="right">
                  <Button type="text" icon={<DatabaseOutlined />} onClick={() => setView('schema')} />
                </Tooltip>
                <Tooltip title="知识库管理" placement="right">
                  <Button type="text" icon={<ReadOutlined />} onClick={() => setView('knowledge')} />
                </Tooltip>
                <Tooltip title="提示词配置" placement="right">
                  <Button type="text" icon={<RobotOutlined />} onClick={() => setView('prompt')} />
                </Tooltip>
                <Tooltip title="用户管理" placement="right">
                  <Button type="text" icon={<TeamOutlined />} onClick={() => setView('users')} />
                </Tooltip>
              </>
            )}
            <div className="sider-footer">
              <Popover
                content={accountMenu}
                trigger="click"
                placement="rightBottom"
                open={accountMenuOpen}
                onOpenChange={setAccountMenuOpen}
                rootClassName="sider-account-popover"
              >
                <button type="button" className="sider-avatar-trigger" aria-label="打开账户菜单">
                  <UserAvatar avatarPath={user.avatarPath} className="msg-avatar user sider-avatar" />
                </button>
              </Popover>
            </div>
          </>
        ) : (
          <>
            <div className="app-brand">
              <div className="app-brand-icon app-brand-icon-static">
                <BrandIcon />
              </div>
              <div className="app-brand-copy">
                <div className="app-brand-title">AI Agent大数据智能查询系统</div>
                <div className="app-brand-sub">一句话查数据</div>
              </div>
              <Button
                className="app-sider-collapse-button"
                type="text"
                size="small"
                icon={<DoubleLeftOutlined />}
                aria-label="收起侧边栏"
                onClick={() => {
                  setAccountMenuOpen(false)
                  setCollapsed(true)
                }}
              />
            </div>
            <Button
              block
              icon={<PlusOutlined />}
              onClick={startNewConversation}
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
            {isAdmin && (
              <>
                <Button block type={view === 'schema' ? 'default' : 'text'} icon={<DatabaseOutlined />} onClick={() => setView('schema')} style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: view === 'schema' ? '#efefef' : 'transparent', justifyContent: 'flex-start', display: 'flex' }}>
                  Schema 配置
                </Button>
                <Button block type={view === 'knowledge' ? 'default' : 'text'} icon={<ReadOutlined />} onClick={() => setView('knowledge')} style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: view === 'knowledge' ? '#efefef' : 'transparent', justifyContent: 'flex-start', display: 'flex' }}>
                  知识库管理
                </Button>
                <Button block type={view === 'prompt' ? 'default' : 'text'} icon={<RobotOutlined />} onClick={() => setView('prompt')} style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: view === 'prompt' ? '#efefef' : 'transparent', justifyContent: 'flex-start', display: 'flex' }}>
                  提示词配置
                </Button>
                <Button block type={view === 'users' ? 'default' : 'text'} icon={<TeamOutlined />} onClick={() => setView('users')} style={{ marginBottom: 4, border: 'none', boxShadow: 'none', background: view === 'users' ? '#efefef' : 'transparent', justifyContent: 'flex-start', display: 'flex' }}>
                  用户管理
                </Button>
              </>
            )}
            <div className="conversation-section">
              <div className="conversation-section-title">历史会话</div>
              <div className="conversation-list">
                {conversations.length === 0 ? (
                  <div className="conversation-empty">暂无历史会话</div>
                ) : conversations.map((conversation) => (
                  <div
                    key={conversation.id}
                    role="button"
                    tabIndex={0}
                    className={`conversation-item${activeConversationId === conversation.id ? ' active' : ''}`}
                    onClick={() => openConversation(conversation)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') openConversation(conversation)
                    }}
                    aria-disabled={historyLoading}
                  >
                    <MessageOutlined />
                    <span className="conversation-title">{conversation.title}</span>
                    <button
                      type="button"
                      className="conversation-delete"
                      aria-label="删除会话"
                      onClick={(event) => removeConversation(event, conversation.id)}
                    >
                      <DeleteOutlined />
                    </button>
                  </div>
                ))}
              </div>
            </div>
            <Popover
              content={accountMenu}
              trigger="click"
              placement="topLeft"
              open={accountMenuOpen}
              onOpenChange={setAccountMenuOpen}
              rootClassName="sider-account-popover"
            >
              <div
                className="sider-footer expanded sider-user-trigger"
                role="button"
                tabIndex={0}
                aria-label="打开账户菜单"
                aria-expanded={accountMenuOpen}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    setAccountMenuOpen((open) => !open)
                  }
                }}
              >
                <UserAvatar avatarPath={user.avatarPath} className="msg-avatar user sider-avatar" />
                <div className="sider-user-info">
                  <div className="sider-user-name">{user.username}</div>
                  <div className="sider-user-phone">{user.phone}</div>
                </div>
              </div>
            </Popover>
          </>
        )}
      </aside>

      {/* 管理配置页仅向管理员展示；普通用户只保留智能问答。 */}
      {view === 'schema' && isAdmin ? (
        <SchemaConfigPage />
      ) : view === 'knowledge' && isAdmin ? (
        <KnowledgePage />
      ) : view === 'prompt' && isAdmin ? (
        <PromptConfigPage />
      ) : view === 'users' && isAdmin ? (
        <UserManagementPage currentUser={user} onCurrentUserUpdated={handleCurrentUserUpdated} />
      ) : (
      <main className="chat-main">
        <div className="database-switch-wrap" aria-label="数据库连接选择">
          <DatabaseConnectionPicker
            allowedTypes={allowedDatabaseTypes}
            value={databaseConnection}
            disabled={asking}
            onChange={selectDatabaseConnection}
          />
          {allowedDatabaseTypes.length === 0 && (
            <span className="database-permission-hint">尚未分配数据库权限，请联系管理员</span>
          )}
        </div>
        <div className={`chat-body${messages.length === 0 ? ' empty' : ''}`} ref={bodyRef}>
          {messages.length === 0 ? (
            <div className="empty-chat-center">
              <Welcome />
              <ChatComposer
                input={input}
                setInput={setInput}
                asking={asking || !databaseConnection}
                onSend={() => send()}
                useKnowledgeBase={useKnowledgeBase}
                onToggleKnowledgeBase={() => setUseKnowledgeBase((enabled) => !enabled)}
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
                              aria-label={copiedId === m.id ? '\u5df2\u590d\u5236' : '\u590d\u5236\u95ee\u9898'}
                              icon={copiedId === m.id ? <CheckOutlined /> : <CopyOutlined />}
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
            asking={asking || !databaseConnection}
            onSend={() => send()}
            useKnowledgeBase={useKnowledgeBase}
            onToggleKnowledgeBase={() => setUseKnowledgeBase((enabled) => !enabled)}
          />
        )}
      </main>
      )}
    </div>
  )
}
