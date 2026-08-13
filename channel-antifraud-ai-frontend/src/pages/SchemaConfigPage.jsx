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
} from '@ant-design/icons'
import {
  fetchSchemaTree,
  saveTableRule,
  saveFieldRule,
  fetchPromptPreview,
} from '../api/schemaConfigService'

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
    if (!keyword) return tables
    return tables.filter(
      (t) =>
        t.tableName.toLowerCase().includes(keyword) ||
        (t.customComment || t.dbComment || '').toLowerCase().includes(keyword),
    )
  }, [tables, tableSearch])

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
            <Tag color="default" style={{ marginLeft: 6 }}>不发送</Tag>
          )}
        </span>
      ),
    },
    {
      title: database === 'mysql' ? 'MySQL表注释' : '表注释（自定义覆盖）',
      key: 'comment',
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
      ) : (
        <div className="schema-comment-cell">
          {record.dbComment && (
            <Tooltip title="数据库原始注释">
              <span className="schema-db-comment">{record.dbComment}</span>
            </Tooltip>
          )}
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
        </div>
      ),
    },
    {
      title: '发送给大模型',
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
  ]

  const fieldColumns = [
    {
      title: '字段名',
      dataIndex: 'fieldName',
      key: 'fieldName',
      render: (value, record) => (
        <span className={record.visible ? '' : 'schema-row-blocked'}>
          {value}
          {!record.visible && (
            <Tag color="default" style={{ marginLeft: 6 }}>不发送</Tag>
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
      title: '发送给大模型',
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
        黑白名单表配置
      </header>

      <div className="schema-toolbar">
        <Segmented
          value={database}
          onChange={(value) => {
            setDatabase(value)
            setSelectedTable(null)
            setTableSearch('')
          }}
          options={[
            { label: 'MySQL', value: 'mysql' },
            { label: 'Hive', value: 'hive' },
          ]}
        />
        <span className="schema-stat">
          已启用 {visibleCount}/{tables.length} 张表
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
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: '#999' }} />}
            placeholder="搜索表名或注释"
            value={tableSearch}
            onChange={(e) => setTableSearch(e.target.value)}
            style={{ marginBottom: 8 }}
          />
          <Table
            size="small"
            rowKey="tableName"
            loading={loading}
            columns={tableColumns}
            dataSource={filteredTables}
            pagination={false}
            scroll={{ y: 'calc(100vh - 220px)' }}
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
                字段配置：
                <span className="schema-field-panel-table">{currentTable.tableName}</span>
                <span className="schema-stat">
                  {currentTable.fields.filter((f) => f.visible).length}/
                  {currentTable.fields.length} 个字段已启用
                </span>
              </div>
              <Table
                size="small"
                rowKey="fieldName"
                loading={loading}
                columns={fieldColumns}
                dataSource={currentTable.fields}
                pagination={false}
                scroll={{ y: 'calc(100vh - 260px)' }}
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
