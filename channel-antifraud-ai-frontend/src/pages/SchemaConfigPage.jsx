import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Input,
  Modal,
  Segmented,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd'
import {
  ReloadOutlined,
  EyeOutlined,
  SearchOutlined,
  CheckOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  fetchSchemaTree,
  saveTableRule,
  saveFieldRule,
  saveTableVisibilityBatch,
  saveFieldVisibilityBatch,
  fetchPromptPreview,
} from '../api/schemaConfigService'

/** 可见性筛选项：白名单绿色点、黑名单红色点，与批量操作配色呼应 */
const VISIBILITY_FILTER_OPTIONS = [
  { label: '全部', value: 'all' },
  {
    label: (
      <span className="schema-seg-option">
        <i className="schema-dot schema-dot-white" />
        仅白名单
      </span>
    ),
    value: 'white',
  },
  {
    label: (
      <span className="schema-seg-option">
        <i className="schema-dot schema-dot-black" />
        仅黑名单
      </span>
    ),
    value: 'black',
  },
]

/** 批量黑白名单操作：作用于当前筛选结果 */
function BatchVisibilityActions({ loading, disabled, onSelectAll, onSelectNone }) {
  return (
    <div className="schema-batch-actions">
      <span className="schema-batch-label">批量</span>
      <Tooltip title="把当前筛选结果全部加入白名单">
        <Button
          size="small"
          className="schema-batch-btn schema-batch-white"
          icon={<CheckOutlined />}
          loading={loading}
          disabled={disabled}
          onClick={onSelectAll}
        >
          全选
        </Button>
      </Tooltip>
      <Tooltip title="把当前筛选结果全部加入黑名单">
        <Button
          size="small"
          className="schema-batch-btn schema-batch-black"
          icon={<StopOutlined />}
          loading={loading}
          disabled={disabled}
          onClick={onSelectNone}
        >
          全不选
        </Button>
      </Tooltip>
    </div>
  )
}

/**
 * Schema 配置页：表/字段黑白名单（黑名单模式，默认全部发送给大模型）+ 自定义注释。
 * 左侧表列表，点选后右侧展示字段列表；Switch 与注释编辑即时保存。
 */
export default function SchemaConfigPage() {
  const [database, setDatabase] = useState('mysql')
  const [tables, setTables] = useState([])
  const [loading, setLoading] = useState(false)
  const [selectedTable, setSelectedTable] = useState(null)
  const [tableSearch, setTableSearch] = useState('')
  const [tableVisibilityFilter, setTableVisibilityFilter] = useState('all')
  const [fieldVisibilityFilter, setFieldVisibilityFilter] = useState('all')
  const [tableBatchLoading, setTableBatchLoading] = useState(false)
  const [fieldBatchLoading, setFieldBatchLoading] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewText, setPreviewText] = useState('')
  const [previewLoading, setPreviewLoading] = useState(false)

  const loadTree = useCallback(async (db, keepSelection = false) => {
    setLoading(true)
    try {
      const tree = await fetchSchemaTree(db)
      setTables(tree)
      setSelectedTable((prev) => {
        if (keepSelection && prev && tree.some((t) => t.tableName === prev)) {
          return prev
        }
        return tree.length > 0 ? tree[0].tableName : null
      })
    } catch (e) {
      message.error(`加载Schema失败：${e.message || '服务异常'}`)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTree(database)
  }, [database, loadTree])

  const currentTable = useMemo(
    () => tables.find((t) => t.tableName === selectedTable) || null,
    [tables, selectedTable],
  )

  const filteredTables = useMemo(() => {
    const keyword = tableSearch.trim().toLowerCase()
    return tables.filter((t) => {
      const visibilityMatches = tableVisibilityFilter === 'all'
        || (tableVisibilityFilter === 'white' ? t.visible : !t.visible)
      const keywordMatches = !keyword
        || (
        t.tableName.toLowerCase().includes(keyword) ||
        (t.dbComment || '').toLowerCase().includes(keyword) ||
        (t.customComment || '').toLowerCase().includes(keyword)
        )
      return visibilityMatches && keywordMatches
    })
  }, [tables, tableSearch, tableVisibilityFilter])

  const filteredFields = useMemo(() => {
    if (!currentTable) return []
    return currentTable.fields.filter((field) => (
      fieldVisibilityFilter === 'all'
      || (fieldVisibilityFilter === 'white' ? field.visible : !field.visible)
    ))
  }, [currentTable, fieldVisibilityFilter])

  useEffect(() => {
    if (filteredTables.some((table) => table.tableName === selectedTable)) return
    setSelectedTable(filteredTables[0]?.tableName || null)
  }, [filteredTables, selectedTable])

  const visibleCount = useMemo(
    () => tables.filter((t) => t.visible).length,
    [tables],
  )

  // 乐观更新本地状态，失败则回滚
  const patchTable = useCallback((tableName, patch) => {
    setTables((prev) =>
      prev.map((t) => (t.tableName === tableName ? { ...t, ...patch } : t)),
    )
  }, [])

  const patchField = useCallback((tableName, fieldName, patch) => {
    setTables((prev) =>
      prev.map((t) =>
        t.tableName === tableName
          ? {
              ...t,
              fields: t.fields.map((f) =>
                f.fieldName === fieldName ? { ...f, ...patch } : f,
              ),
            }
          : t,
      ),
    )
  }, [])

  const toggleTable = useCallback(
    async (table, visible) => {
      const rollback = table.visible
      patchTable(table.tableName, { visible })
      try {
        await saveTableRule(database, table.tableName, {
          visible,
        })
        message.success(visible ? `已恢复发送表 ${table.tableName}` : `已停止发送表 ${table.tableName}`)
      } catch (e) {
        patchTable(table.tableName, { visible: rollback })
        message.error(`保存失败：${e.message || '服务异常'}`)
      }
    },
    [database, patchTable],
  )

  const toggleField = useCallback(
    async (table, field, visible) => {
      const rollback = field.visible
      patchField(table.tableName, field.fieldName, { visible })
      try {
        await saveFieldRule(database, table.tableName, field.fieldName, {
          visible,
        })
      } catch (e) {
        patchField(table.tableName, field.fieldName, { visible: rollback })
        message.error(`保存失败：${e.message || '服务异常'}`)
      }
    },
    [database, patchField],
  )

  const batchToggleTables = useCallback(async (visible) => {
    const targets = filteredTables.filter((table) => table.visible !== visible)
    if (targets.length === 0) {
      message.info(visible ? '当前表已全部在白名单中' : '当前表已全部在黑名单中')
      return
    }
    setTableBatchLoading(true)
    try {
      await saveTableVisibilityBatch(
        database,
        targets.map((table) => table.tableName),
        visible,
      )
      const names = new Set(targets.map((table) => table.tableName))
      setTables((prev) => prev.map((table) => (
        names.has(table.tableName) ? { ...table, visible } : table
      )))
      message.success(`已将 ${targets.length} 张表加入${visible ? '白名单' : '黑名单'}`)
    } catch (error) {
      message.error(`批量保存失败：${error.message || '服务异常'}`)
    } finally {
      setTableBatchLoading(false)
    }
  }, [database, filteredTables])

  const batchToggleFields = useCallback(async (visible) => {
    if (!currentTable) return
    const targets = filteredFields.filter((field) => field.visible !== visible)
    if (targets.length === 0) {
      message.info(visible ? '当前字段已全部在白名单中' : '当前字段已全部在黑名单中')
      return
    }
    setFieldBatchLoading(true)
    try {
      await saveFieldVisibilityBatch(
        database,
        currentTable.tableName,
        targets.map((field) => field.fieldName),
        visible,
      )
      const names = new Set(targets.map((field) => field.fieldName))
      setTables((prev) => prev.map((table) => (
        table.tableName === currentTable.tableName
          ? {
              ...table,
              fields: table.fields.map((field) => (
                names.has(field.fieldName) ? { ...field, visible } : field
              )),
            }
          : table
      )))
      message.success(`已将 ${targets.length} 个字段加入${visible ? '白名单' : '黑名单'}`)
    } catch (error) {
      message.error(`批量保存失败：${error.message || '服务异常'}`)
    } finally {
      setFieldBatchLoading(false)
    }
  }, [currentTable, database, filteredFields])

  const editTableComment = useCallback(
    async (table, comment) => {
      const commentKey = database === 'mysql' ? 'dbComment' : 'customComment'
      const rollback = table[commentKey]
      patchTable(table.tableName, { [commentKey]: comment || null })
      try {
        await saveTableRule(database, table.tableName, {
          visible: table.visible,
          comment,
        })
        if (database === 'mysql') {
          patchTable(table.tableName, { customComment: null })
        }
        message.success('表注释已保存')
      } catch (e) {
        patchTable(table.tableName, { [commentKey]: rollback })
        message.error(`保存失败：${e.message || '服务异常'}`)
      }
    },
    [database, patchTable],
  )

  const editFieldComment = useCallback(
    async (table, field, comment) => {
      const commentKey = database === 'mysql' ? 'dbComment' : 'customComment'
      const rollback = field[commentKey]
      patchField(table.tableName, field.fieldName, { [commentKey]: comment || null })
      try {
        await saveFieldRule(database, table.tableName, field.fieldName, {
          visible: field.visible,
          comment,
        })
        if (database === 'mysql') {
          patchField(table.tableName, field.fieldName, { customComment: null })
        }
      } catch (e) {
        patchField(table.tableName, field.fieldName, { [commentKey]: rollback })
        message.error(`保存失败：${e.message || '服务异常'}`)
      }
    },
    [database, patchField],
  )

  const openPreview = useCallback(async () => {
    setPreviewOpen(true)
    setPreviewLoading(true)
    try {
      const body = await fetchPromptPreview(database)
      setPreviewText(body.prompt || '')
    } catch (e) {
      setPreviewText(`加载失败：${e.message || '服务异常'}`)
    } finally {
      setPreviewLoading(false)
    }
  }, [database])

  const tableColumns = [
    {
      title: '表名',
      dataIndex: 'tableName',
      key: 'tableName',
      render: (value, record) => (
        <span className={record.visible ? '' : 'schema-row-blocked'}>
          {value}
          {!record.visible && (
            <Tag color="default" style={{ marginLeft: 6 }}>黑名单</Tag>
          )}
        </span>
      ),
    },
    {
      title: database === 'mysql' ? 'MySQL表注释' : '数据库注释',
      dataIndex: 'dbComment',
      key: 'dbComment',
      ellipsis: true,
      render: (_, record) => database === 'mysql' ? (
        <Typography.Text
          className="schema-db-comment"
          editable={{
            text: record.dbComment || '',
            onChange: (value) => editTableComment(record, value.trim()),
            tooltip: '直接修改MySQL表注释',
            triggerType: ['text', 'icon'],
          }}
        >
          {record.dbComment || '＋编辑注释'}
        </Typography.Text>
      ) : <span className="schema-db-comment">{record.dbComment || '—'}</span>,
    },
    database !== 'mysql' && {
      title: '自定义注释',
      key: 'customComment',
      render: (_, record) => (
        <Typography.Text
          className={record.customComment ? 'schema-custom-comment' : 'schema-custom-comment empty'}
          editable={{
            text: record.customComment || '',
            onChange: (value) => editTableComment(record, value.trim()),
            tooltip: '编辑自定义注释（覆盖数据库注释，留空恢复）',
            triggerType: ['text', 'icon'],
          }}
        >
          {record.customComment || '＋自定义注释'}
        </Typography.Text>
      ),
    },
    {
      title: '白名单',
      dataIndex: 'visible',
      key: 'visible',
      width: 110,
      align: 'center',
      render: (visible, record) => (
        <Switch
          size="small"
          checked={visible}
          onChange={(checked) => toggleTable(record, checked)}
        />
      ),
    },
  ].filter(Boolean)

  const fieldColumns = [
    {
      title: '字段名',
      dataIndex: 'fieldName',
      key: 'fieldName',
      render: (value, record) => (
        <span className={record.visible ? '' : 'schema-row-blocked'}>
          {value}
          {!record.visible && (
            <Tag color="default" style={{ marginLeft: 6 }}>黑名单</Tag>
          )}
        </span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'mysqlType',
      key: 'mysqlType',
      width: 150,
      render: (value) => <span className="schema-field-type">{value}</span>,
    },
    {
      title: database === 'mysql' ? 'MySQL字段注释' : '数据库注释',
      dataIndex: 'dbComment',
      key: 'dbComment',
      ellipsis: true,
      render: (value, record) => database === 'mysql' ? (
        <Typography.Text
          className="schema-db-comment"
          editable={{
            text: value || '',
            onChange: (nextValue) => editFieldComment(currentTable, record, nextValue.trim()),
            tooltip: '直接修改MySQL字段注释',
            triggerType: ['text', 'icon'],
          }}
        >
          {value || '＋编辑注释'}
        </Typography.Text>
      ) : <span className="schema-db-comment">{value || '—'}</span>,
    },
    database !== 'mysql' && {
      title: '自定义注释',
      key: 'customComment',
      render: (_, record) => (
        <Typography.Text
          className={record.customComment ? 'schema-custom-comment' : 'schema-custom-comment empty'}
          editable={{
            text: record.customComment || '',
            onChange: (value) => editFieldComment(currentTable, record, value.trim()),
            tooltip: '编辑自定义注释（覆盖数据库注释，留空恢复）',
            triggerType: ['text', 'icon'],
          }}
        >
          {record.customComment || '＋自定义注释'}
        </Typography.Text>
      ),
    },
    {
      title: '白名单',
      dataIndex: 'visible',
      key: 'visible',
      width: 110,
      align: 'center',
      render: (visible, record) => (
        <Switch
          size="small"
          checked={visible}
          onChange={(checked) => toggleField(currentTable, record, checked)}
        />
      ),
    },
  ].filter(Boolean)

  return (
    <div className="schema-config-main">
      <header className="chat-header">
        Schema 黑白名单配置
        <span className="schema-header-sub">白名单发送给模型，黑名单从 Prompt 中排除</span>
      </header>

      <div className="schema-toolbar">
        <Segmented
          value={database}
          onChange={(value) => {
            setDatabase(value)
            setSelectedTable(null)
            setTableSearch('')
            setTableVisibilityFilter('all')
            setFieldVisibilityFilter('all')
          }}
          options={[
            { label: 'MySQL', value: 'mysql' },
            { label: 'Hive', value: 'hive' },
          ]}
        />
        <span className="schema-stat">
          白名单 {visibleCount} · 黑名单 {tables.length - visibleCount} · 共 {tables.length} 张表
        </span>
        <div style={{ flex: 1 }} />
        <Button
          icon={<EyeOutlined />}
          onClick={openPreview}
        >
          预览Prompt
        </Button>
        <Button
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={() => loadTree(database, true)}
        >
          刷新
        </Button>
      </div>

      <div className="schema-content">
        <div className="schema-table-panel">
          <div className="schema-panel-heading">
            <div>
              <div className="schema-panel-title">数据表</div>
              <div className="schema-panel-caption">当前展示 {filteredTables.length} 张</div>
            </div>
          </div>
          <div className="schema-filter-bar">
            <Input
              allowClear
              prefix={<SearchOutlined style={{ color: '#999' }} />}
              placeholder="搜索表名或注释"
              value={tableSearch}
              onChange={(e) => setTableSearch(e.target.value)}
            />
            <div className="schema-filter-controls">
              <Segmented
                size="small"
                value={tableVisibilityFilter}
                onChange={setTableVisibilityFilter}
                options={VISIBILITY_FILTER_OPTIONS}
              />
              <BatchVisibilityActions
                loading={tableBatchLoading}
                disabled={filteredTables.length === 0}
                onSelectAll={() => batchToggleTables(true)}
                onSelectNone={() => batchToggleTables(false)}
              />
            </div>
          </div>
          <Table
            size="small"
            rowKey="tableName"
            loading={loading}
            columns={tableColumns}
            dataSource={filteredTables}
            pagination={false}
            scroll={{ y: 'calc(100vh - 292px)' }}
            rowClassName={(record) =>
              record.tableName === selectedTable ? 'schema-table-row-selected' : ''
            }
            onRow={(record) => ({
              onClick: () => setSelectedTable(record.tableName),
              style: { cursor: 'pointer' },
            })}
          />
        </div>

        <div className="schema-field-panel">
          {currentTable ? (
            <>
              <div className="schema-field-panel-title">
                <div>
                  <span>字段配置</span>
                  <span className="schema-field-panel-table">{currentTable.tableName}</span>
                </div>
                <span className="schema-stat">
                  白名单 {currentTable.fields.filter((f) => f.visible).length} ·
                  黑名单 {currentTable.fields.filter((f) => !f.visible).length}
                </span>
              </div>
              <div className="schema-filter-bar schema-field-filter-bar">
                <Segmented
                  size="small"
                  value={fieldVisibilityFilter}
                  onChange={setFieldVisibilityFilter}
                  options={VISIBILITY_FILTER_OPTIONS}
                />
                <span className="schema-panel-caption">当前展示 {filteredFields.length} 个字段</span>
                <BatchVisibilityActions
                  loading={fieldBatchLoading}
                  disabled={filteredFields.length === 0}
                  onSelectAll={() => batchToggleFields(true)}
                  onSelectNone={() => batchToggleFields(false)}
                />
              </div>
              <Table
                size="small"
                rowKey="fieldName"
                loading={loading}
                columns={fieldColumns}
                dataSource={filteredFields}
                pagination={false}
                scroll={{ y: 'calc(100vh - 292px)' }}
              />
            </>
          ) : (
            <div className="schema-field-empty">
              {loading ? '加载中…' : '暂无表，请检查数据源或白名单配置'}
            </div>
          )}
        </div>
      </div>

      <Modal
        title={`发给大模型的 Schema（${database === 'mysql' ? 'MySQL' : 'Hive'}）`}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={720}
      >
        <pre className="schema-prompt-preview">
          {previewLoading ? '加载中…' : previewText}
        </pre>
      </Modal>
    </div>
  )
}
