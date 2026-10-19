import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Spin,
  Tooltip,
  Upload,
  Alert,
  message,
} from 'antd'
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  InboxOutlined,
  FileTextOutlined,
  SyncOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import {
  searchKnowledge,
  saveKnowledge,
  deleteKnowledge,
  uploadKnowledgeFile,
  listKnowledgeFiles,
  rebuildKnowledgeFile,
  deleteKnowledgeFile,
} from '../api/knowledgeService'

/** 知识类型：与后端 normalizeUpper 对齐，值统一大写 */
const KNOWLEDGE_TYPE_OPTIONS = [
  { label: '通用知识', value: 'GENERAL' },
  { label: '业务术语', value: 'BUSINESS_TERM' },
  { label: '指标口径', value: 'METRIC' },
  { label: 'SQL 示例', value: 'SQL_EXAMPLE' },
]

const KNOWLEDGE_TYPE_LABEL = Object.fromEntries(
  KNOWLEDGE_TYPE_OPTIONS.map((option) => [option.value, option.label]),
)

const DATABASE_OPTIONS = [
  { label: '全部库', value: 'all' },
  { label: 'MySQL', value: 'mysql' },
  { label: 'Hive', value: 'hive' },
]

const DATABASE_LABEL = { all: '全部库', mysql: 'MySQL', hive: 'Hive' }

const QUICK_KEYWORDS = ['销售额', '客户指标', '指标口径', 'SQL 示例']

const FILE_STATUS = {
  READY: { className: 'ready', text: '可检索' },
  FAILED: { className: 'failed', text: '构建失败' },
}

const formatSize = (bytes) =>
  bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${(bytes / 1024).toFixed(1)} KB`

const formatTime = (value) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'

/**
 * 知识库管理页：RAG 知识的检索、新增、编辑、删除。
 * 后端仅提供语义检索接口（question 必填），列表即检索结果。
 */
export default function KnowledgePage() {
  const [mode, setMode] = useState('knowledge')
  const [database, setDatabase] = useState('all')
  const [question, setQuestion] = useState('')
  const [hits, setHits] = useState([])
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const [uploadForm] = Form.useForm()
  const [files, setFiles] = useState([])
  const [filesLoading, setFilesLoading] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [selectedFile, setSelectedFile] = useState(null)
  const [rebuildingId, setRebuildingId] = useState(null)

  const search = useCallback(async (keyword = question, db = database) => {
    const text = keyword.trim()
    if (!text) {
      message.warning('请输入检索关键词')
      return
    }
    setLoading(true)
    try {
      setHits(await searchKnowledge(text, db))
      setSearched(true)
    } catch (e) {
      message.error(`检索失败：${e.message || '服务异常'}`)
    } finally {
      setLoading(false)
    }
  }, [question, database])

  // 进入页面时给一个有引导性的默认检索，避免面对空白页
  useEffect(() => {
    search('指标', 'all')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadFiles = useCallback(async (db = database) => {
    setFilesLoading(true)
    try {
      setFiles(await listKnowledgeFiles(db))
    } catch (e) {
      message.error(`文件列表加载失败：${e.message || '服务异常'}`)
    } finally {
      setFilesLoading(false)
    }
  }, [database])

  useEffect(() => {
    if (mode === 'files') loadFiles()
  }, [mode, database, loadFiles])

  const openUpload = useCallback(() => {
    uploadForm.resetFields()
    uploadForm.setFieldsValue({ database, knowledgeType: 'GENERAL', tableNames: [], keywords: [] })
    setSelectedFile(null)
    setUploadOpen(true)
  }, [uploadForm, database])

  const handleUpload = useCallback(async () => {
    const values = await uploadForm.validateFields()
    if (!selectedFile) {
      message.warning('请选择要导入的文件')
      return
    }
    setUploading(true)
    try {
      const result = await uploadKnowledgeFile({ ...values, file: selectedFile })
      message.success(`构建完成，共生成 ${result.chunkCount} 个知识切片`)
      setUploadOpen(false)
      await loadFiles(values.database)
    } catch (e) {
      message.error(`文件构建失败：${e.message || '服务异常'}`)
    } finally {
      setUploading(false)
    }
  }, [uploadForm, selectedFile, loadFiles])

  const handleRebuild = useCallback(async (record) => {
    setRebuildingId(record.id)
    try {
      const result = await rebuildKnowledgeFile(record.id)
      message.success(`重新构建完成，共 ${result.chunkCount} 个切片`)
      await loadFiles()
    } catch (e) {
      message.error(`重新构建失败：${e.message || '服务异常'}`)
    } finally {
      setRebuildingId(null)
    }
  }, [loadFiles])

  const handleDeleteFile = useCallback(async (record) => {
    try {
      await deleteKnowledgeFile(record.id)
      message.success('文件及其全部知识切片已删除')
      setFiles((prev) => prev.filter((item) => item.id !== record.id))
    } catch (e) {
      message.error(`删除失败：${e.message || '服务异常'}`)
    }
  }, [])

  const openCreate = useCallback(() => {
    setEditing(null)
    form.resetFields()
    setEditorOpen(true)
  }, [form])

  const openEdit = useCallback((record) => {
    setEditing(record)
    form.setFieldsValue({
      title: record.title,
      knowledgeType: record.knowledgeType || 'GENERAL',
      database: record.database || 'all',
      tableNames: record.tableNames || [],
      keywords: record.keywords || [],
      content: record.content,
      source: record.source || 'manual',
    })
    setEditorOpen(true)
  }, [form])

  const handleSave = useCallback(async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await saveKnowledge({
        id: editing?.id,
        title: values.title,
        content: values.content,
        knowledgeType: values.knowledgeType,
        database: values.database,
        tableNames: values.tableNames,
        keywords: values.keywords,
        source: values.source,
      })
      message.success(editing ? '知识已更新' : '知识已新增')
      setEditorOpen(false)
      // 保存后刷新当前检索结果，让改动立刻可见
      if (searched) await search()
    } catch (e) {
      message.error(`保存失败：${e.message || '服务异常'}`)
    } finally {
      setSaving(false)
    }
  }, [form, editing, searched, search])

  const handleDelete = useCallback(async (record) => {
    try {
      await deleteKnowledge(record.id)
      message.success('知识已删除')
      setHits((prev) => prev.filter((hit) => hit.id !== record.id))
    } catch (e) {
      message.error(`删除失败：${e.message || '服务异常'}`)
    }
  }, [])

  const handleQuickSearch = useCallback((keyword) => {
    setQuestion(keyword)
    search(keyword)
  }, [search])

  const renderKnowledgeCard = (record) => (
    <article key={record.id} className="kb-card">
      <div className="kb-card-top">
        <span className={`kb-type kb-type-${record.knowledgeType || 'GENERAL'}`}>
          {KNOWLEDGE_TYPE_LABEL[record.knowledgeType] || record.knowledgeType || '通用知识'}
        </span>
        <div className="kb-card-actions">
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} />
          </Tooltip>
          <Popconfirm
            title="删除这条知识？"
            description="删除后将立即从检索索引中移除"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(record)}
          >
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </div>
      </div>
      <Tooltip title={record.title} placement="topLeft">
        <h3 className="kb-card-title">{record.title}</h3>
      </Tooltip>
      <p className="kb-card-content">{record.content}</p>
      <div className="kb-card-meta">
        <span className="kb-meta-tag kb-meta-db">
          <DatabaseOutlined /> {DATABASE_LABEL[record.database] || record.database}
        </span>
        {record.tableNames?.map((name) => (
          <span key={name} className="kb-meta-tag">{name}</span>
        ))}
        {record.keywords?.map((word) => (
          <span key={word} className="kb-meta-tag kb-meta-keyword"># {word}</span>
        ))}
      </div>
      <div className="kb-card-foot">
        <Tooltip title={`原始 RRF 分数：${record.score.toFixed(6)}`}>
          <span>相关度 {record.relevancePercent.toFixed(1)}%</span>
        </Tooltip>
        <div className="kb-score-bar">
          <i style={{ width: `${record.relevancePercent}%` }} />
        </div>
      </div>
    </article>
  )

  const renderFileRow = (record) => {
    const status = FILE_STATUS[record.status] || { className: 'building', text: '构建中' }
    return (
      <div key={record.id} className="kb-file-row">
        <span className={`kb-file-icon type-${record.fileType}`}>
          {record.fileType?.toUpperCase() || 'FILE'}
        </span>
        <div className="kb-file-main">
          <Tooltip title={record.fileName} placement="topLeft">
            <div className="kb-file-name">{record.fileName}</div>
          </Tooltip>
          <div className="kb-file-sub">
            {formatSize(record.fileSize)} · {DATABASE_LABEL[record.database] || record.database}
            {record.source ? ` · ${record.source}` : ''}
          </div>
        </div>
        <div className="kb-file-side">
          <Tooltip title={record.errorMessage || ''}>
            <span className={`kb-status ${status.className}`}><i />{status.text}</span>
          </Tooltip>
          <div className="kb-file-stat">
            <b>{record.chunkCount}</b>
            <span>切片</span>
          </div>
          <div className="kb-file-stat kb-file-time">
            <b>{formatTime(record.updatedAt)}</b>
            <span>更新时间</span>
          </div>
          <div className="kb-file-actions">
            <Tooltip title="重新解析并构建向量索引">
              <Button
                type="text"
                size="small"
                icon={<SyncOutlined spin={rebuildingId === record.id} />}
                disabled={rebuildingId != null}
                onClick={() => handleRebuild(record)}
              />
            </Tooltip>
            <Popconfirm
              title="删除这份知识文件？"
              description="原文件和全部切片将被删除"
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => handleDeleteFile(record)}
            >
              <Tooltip title="删除文件">
                <Button type="text" size="small" danger icon={<DeleteOutlined />} />
              </Tooltip>
            </Popconfirm>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="kb-page">
      <header className="kb-header">
        <div className="kb-header-text">
          <h1>知识库管理</h1>
        </div>
        <Segmented
          className="kb-mode-switch"
          value={mode}
          onChange={setMode}
          options={[
            { label: '知识检索', value: 'knowledge', icon: <SearchOutlined /> },
            { label: `文件库${files.length ? ` · ${files.length}` : ''}`, value: 'files', icon: <FileTextOutlined /> },
          ]}
        />
      </header>

      {mode === 'knowledge' ? (
        <div className="kb-body">
          <div className="kb-search-card">
            <div className="kb-search-row">
              <Input
                allowClear
                className="kb-search-input"
                prefix={<SearchOutlined style={{ color: '#9a9a94' }} />}
                placeholder="输入关键词检索知识，如：销售额、客户指标"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onPressEnter={() => search()}
              />
              <Segmented
                value={database}
                onChange={(value) => {
                  setDatabase(value)
                  if (question.trim()) search(question, value)
                }}
                options={DATABASE_OPTIONS}
              />
              <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={() => search()}>
                检索
              </Button>
            </div>
            <div className="kb-quick-chips">
              <span>试试：</span>
              {QUICK_KEYWORDS.map((keyword) => (
                <button key={keyword} type="button" className="kb-chip" onClick={() => handleQuickSearch(keyword)}>
                  {keyword}
                </button>
              ))}
            </div>
          </div>

          <div className="kb-result-head">
            <div className="kb-result-title">
              检索结果
              {searched && <span className="kb-result-sub">共 {hits.length} 条 · 按相关度排序</span>}
            </div>
            <div className="kb-result-actions">
              <Button icon={<ReloadOutlined />} loading={loading} disabled={!question.trim()} onClick={() => search()}>
                刷新
              </Button>
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    { key: 'manual', label: '手动录入', icon: <EditOutlined /> },
                    { key: 'file', label: '从文件导入', icon: <FileTextOutlined /> },
                  ],
                  onClick: ({ key }) => (key === 'file' ? openUpload() : openCreate()),
                }}
              >
                <Button type="primary" icon={<PlusOutlined />}>新增知识</Button>
              </Dropdown>
            </div>
          </div>

          <Spin spinning={loading}>
            {hits.length > 0 ? (
              <div className="kb-grid">{hits.map(renderKnowledgeCard)}</div>
            ) : (
              <div className="kb-empty">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={searched ? '未检索到相关知识，换个关键词试试' : '输入关键词开始检索，或点击右上角新增知识'}
                />
              </div>
            )}
          </Spin>
        </div>
      ) : (
        <div className="kb-body">
          <div className="kb-result-head">
            <div className="kb-result-title">
              文件列表
              <span className="kb-result-sub">共 {files.length} 份文件</span>
            </div>
            <div className="kb-result-actions">
              <Button icon={<ReloadOutlined />} loading={filesLoading} onClick={() => loadFiles()}>
                刷新
              </Button>
            </div>
          </div>

          <Spin spinning={filesLoading}>
            {files.length > 0 ? (
              <div className="kb-file-list">{files.map(renderFileRow)}</div>
            ) : (
              <div className="kb-empty">
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未导入文件，请从“知识检索 → 新增知识 → 从文件导入”开始" />
              </div>
            )}
          </Spin>
        </div>
      )}

      <Modal
        title={editing ? '编辑知识' : '新增知识'}
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onOk={handleSave}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        width={640}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          className="kb-form"
          initialValues={{
            knowledgeType: 'GENERAL',
            database: 'all',
            tableNames: [],
            keywords: [],
            source: 'manual',
          }}
        >
          <Form.Item
            name="title"
            label="标题"
            rules={[
              { required: true, message: '请输入知识标题' },
              { max: 200, message: '标题不能超过200个字符' },
            ]}
          >
            <Input placeholder="如：销售额指标口径" showCount maxLength={200} />
          </Form.Item>
          <Form.Item
            name="content"
            label="内容"
            rules={[
              { required: true, message: '请输入知识内容' },
              { max: 20000, message: '内容不能超过20000个字符' },
            ]}
          >
            <Input.TextArea
              placeholder="知识正文，将作为 RAG 召回内容拼入 Prompt"
              autoSize={{ minRows: 5, maxRows: 12 }}
              showCount
              maxLength={20000}
            />
          </Form.Item>
          <div className="kb-form-row">
            <Form.Item name="knowledgeType" label="知识类型" className="kb-form-col">
              <Select options={KNOWLEDGE_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="database" label="生效数据库" className="kb-form-col">
              <Select options={DATABASE_OPTIONS} />
            </Form.Item>
          </div>
          <Form.Item
            name="tableNames"
            label="关联表"
            tooltip="输入表名后回车添加，用于提升该表相关问题的召回权重"
          >
            <Select mode="tags" placeholder="输入表名后回车，如 dwd_trade_order_di" open={false} suffixIcon={null} />
          </Form.Item>
          <Form.Item
            name="keywords"
            label="关键词"
            tooltip="检索时命中关键词会获得更高权重"
          >
            <Select mode="tags" placeholder="输入关键词后回车" open={false} suffixIcon={null} />
          </Form.Item>
          <Form.Item name="source" label="来源">
            <Input placeholder="manual" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="导入知识文件"
        open={uploadOpen}
        onCancel={() => !uploading && setUploadOpen(false)}
        onOk={handleUpload}
        okText="解析并构建"
        cancelText="取消"
        confirmLoading={uploading}
        width={680}
        destroyOnHidden
        closable={!uploading}
        maskClosable={!uploading}
      >
        <Alert
          className="kb-upload-alert"
          type="info"
          showIcon
          message="保存原文件并自动构建向量索引"
          description="文本会按段落边界切片并保留上下文重叠；扫描版 PDF 暂不支持 OCR。"
        />
        <Upload.Dragger
          accept=".md,.txt,.pdf,.doc,.docx"
          maxCount={1}
          fileList={selectedFile ? [selectedFile] : []}
          beforeUpload={(file) => { setSelectedFile(file); return false }}
          onRemove={() => { setSelectedFile(null); return true }}
          disabled={uploading}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">拖入文件，或点击选择</p>
          <p className="ant-upload-hint">MD / TXT / PDF / DOC / DOCX · 最大 20MB</p>
        </Upload.Dragger>
        <Form form={uploadForm} layout="vertical" className="kb-form kb-upload-form">
          <div className="kb-form-row">
            <Form.Item name="knowledgeType" label="知识类型" className="kb-form-col">
              <Select options={KNOWLEDGE_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="database" label="生效数据库" className="kb-form-col">
              <Select options={DATABASE_OPTIONS} />
            </Form.Item>
          </div>
          <Form.Item name="tableNames" label="关联表">
            <Select mode="tags" placeholder="输入表名后回车" open={false} suffixIcon={null} />
          </Form.Item>
          <Form.Item name="keywords" label="关键词">
            <Select mode="tags" placeholder="输入关键词后回车" open={false} suffixIcon={null} />
          </Form.Item>
          <Form.Item name="source" label="来源（可选）">
            <Input placeholder="不填写时使用文件名" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
