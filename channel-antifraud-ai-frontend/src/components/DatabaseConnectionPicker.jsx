import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Tag, message } from 'antd'
import { CheckCircleOutlined, DatabaseOutlined, LinkOutlined } from '@ant-design/icons'
import { connectDataSource, testDataSource } from '../api/dataSourceService'

const TYPE_META = {
  mysql: { label: 'MySQL', port: 3306 },
  hive: { label: 'Hive', port: 10000 },
}

export default function DatabaseConnectionPicker({ allowedTypes, value, disabled, onChange }) {
  const [form] = Form.useForm()
  const [open, setOpen] = useState(false)
  const [testing, setTesting] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [databases, setDatabases] = useState([])
  const [testedSignature, setTestedSignature] = useState('')
  const typeOptions = useMemo(() => allowedTypes.map((type) => ({
    value: type,
    label: TYPE_META[type]?.label || type,
  })), [allowedTypes])

  useEffect(() => {
    if (!open) return
    const type = value?.databaseType && allowedTypes.includes(value.databaseType)
      ? value.databaseType : allowedTypes[0]
    form.setFieldsValue({
      databaseType: type,
      host: value?.host || '',
      port: value?.port || TYPE_META[type]?.port,
      username: '',
      password: '',
      database: value?.database,
    })
    setDatabases([])
    setTestedSignature('')
  }, [allowedTypes, form, open, value])

  const signature = (values) => [values.databaseType, values.host, values.port,
    values.username, values.password].join('|')

  const test = async () => {
    const values = await form.validateFields(['databaseType', 'host', 'port', 'username', 'password'])
    setTesting(true)
    try {
      const result = await testDataSource(values)
      setDatabases(result.databases || [])
      setTestedSignature(signature(values))
      form.setFieldValue('database', undefined)
      message.success(`连接成功，发现 ${result.databases?.length || 0} 个数据库`)
    } catch (error) {
      setDatabases([])
      setTestedSignature('')
      message.error(error.message || '连接测试失败')
    } finally {
      setTesting(false)
    }
  }

  const connect = async () => {
    const values = await form.validateFields()
    if (testedSignature !== signature(values)) {
      message.warning('连接信息已变化，请重新测试')
      return
    }
    setConnecting(true)
    try {
      const selected = await connectDataSource(values)
      onChange(selected)
      setOpen(false)
      message.success(`已连接 ${selected.label}`)
    } catch (error) {
      message.error(error.message || '连接数据库失败')
    } finally {
      setConnecting(false)
    }
  }

  const changeType = (type) => {
    form.setFieldsValue({ port: TYPE_META[type]?.port, database: undefined })
    setDatabases([])
    setTestedSignature('')
  }

  return (
    <>
      <Button
        className={`database-connection-trigger${value ? ' connected' : ''}`}
        icon={value ? <CheckCircleOutlined /> : <DatabaseOutlined />}
        disabled={disabled || allowedTypes.length === 0}
        onClick={() => setOpen(true)}
      >
        {value?.label || '连接数据库'}
      </Button>
      <Modal
        title="连接业务数据库"
        open={open}
        width={620}
        okText="使用此数据库"
        cancelText="取消"
        confirmLoading={connecting}
        okButtonProps={{ disabled: databases.length === 0 }}
        onOk={connect}
        onCancel={() => !testing && !connecting && setOpen(false)}
        destroyOnHidden
      >
        <Alert
          className="database-connection-alert"
          type="info"
          showIcon
          message="连接凭据仅保存在当前服务进程的临时连接池中"
          description="先测试实例连接，测试成功后再选择该实例中的具体数据库。"
        />
        <Form form={form} layout="vertical" requiredMark={false}>
          <div className="database-connection-grid">
            <Form.Item name="databaseType" label="数据库类型" rules={[{ required: true }]}>
              <Select options={typeOptions} onChange={changeType} />
            </Form.Item>
            <Form.Item name="host" label="IP / 主机名" rules={[{ required: true, message: '请输入数据库地址' }]}>
              <Input placeholder="例如 192.168.1.20" onChange={() => setTestedSignature('')} />
            </Form.Item>
            <Form.Item name="port" label="端口" rules={[{ required: true, message: '请输入端口' }]}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} onChange={() => setTestedSignature('')} />
            </Form.Item>
            <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input autoComplete="username" onChange={() => setTestedSignature('')} />
            </Form.Item>
            <Form.Item name="password" label="密码">
              <Input.Password autoComplete="current-password" onChange={() => setTestedSignature('')} />
            </Form.Item>
          </div>
          <Space className="database-test-row" align="center">
            <Button icon={<LinkOutlined />} loading={testing} onClick={test}>测试连接并读取库列表</Button>
            {databases.length > 0 && <Tag color="success">连接测试通过</Tag>}
          </Space>
          <Form.Item
            name="database"
            label="选择数据库"
            rules={[{ required: true, message: '请选择数据库' }]}
          >
            <Select
              showSearch
              disabled={databases.length === 0}
              placeholder={databases.length ? '请选择本次查询使用的数据库' : '请先测试连接'}
              options={databases.map((database) => ({ value: database, label: database }))}
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
