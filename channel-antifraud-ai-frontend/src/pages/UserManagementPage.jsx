import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Avatar,
  Button,
  Divider,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Table,
  Tag,
  Upload,
  message,
} from 'antd'
import {
  CameraOutlined,
  SearchOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { createUser, deleteUser, listUsers, updateUser } from '../api/adminUserService'
import { resolveAvatar } from '../utils/avatars'

const formatTime = (value) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '—'

const ROLE_OPTIONS = [
  { value: 'ALL', label: '全部角色' },
  { value: 'ADMIN', label: '管理员' },
  { value: 'USER', label: '普通用户' },
]

const DATA_SOURCE_OPTIONS = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'hive', label: 'Hive' },
]

export default function UserManagementPage({ currentUser, onCurrentUserUpdated }) {
  const [form] = Form.useForm()
  const [users, setUsers] = useState([])
  const [keyword, setKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingUser, setEditingUser] = useState(null)
  const [avatarFile, setAvatarFile] = useState(null)
  const [avatarPreview, setAvatarPreview] = useState('')

  const load = useCallback(async (query = keyword) => {
    setLoading(true)
    try {
      setUsers(await listUsers(query))
    } catch (error) {
      message.error(error.message || '用户列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword])

  useEffect(() => { load('') }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const filteredUsers = useMemo(() => (
    roleFilter === 'ALL' ? users : users.filter((item) => item.role === roleFilter)
  ), [users, roleFilter])

  const summary = useMemo(() => ({
    total: users.length,
    admins: users.filter((item) => item.role === 'ADMIN').length,
  }), [users])

  const resetSearch = () => {
    setKeyword('')
    setRoleFilter('ALL')
    load('')
  }

  const openCreate = () => {
    setEditingUser(null)
    setAvatarFile(null)
    setAvatarPreview('')
    form.resetFields()
    form.setFieldsValue({ role: 'USER', dataSources: ['mysql'] })
    setDrawerOpen(true)
  }

  const openEdit = (user) => {
    setEditingUser(user)
    setAvatarFile(null)
    setAvatarPreview(resolveAvatar(user.avatarPath))
    form.setFieldsValue({
      username: user.username,
      phone: user.phone,
      password: '',
      role: user.role,
      dataSources: user.dataSources || [],
    })
    setDrawerOpen(true)
  }

  const chooseAvatar = (file) => {
    if (!file.type?.startsWith('image/')) {
      message.error('请选择图片文件')
      return Upload.LIST_IGNORE
    }
    setAvatarFile(file)
    const reader = new FileReader()
    reader.onload = () => setAvatarPreview(reader.result)
    reader.readAsDataURL(file)
    return false
  }

  const submit = async () => {
    const values = await form.validateFields()
    if (!editingUser && !avatarFile) {
      message.error('新增用户需要上传头像')
      return
    }
    setSaving(true)
    try {
      const payload = { ...values, avatarFile }
      const saved = editingUser
        ? await updateUser(editingUser.id, payload)
        : await createUser(payload)
      message.success(editingUser ? '用户资料已更新' : '用户已创建')
      setDrawerOpen(false)
      await load()
      if (saved.id === currentUser.id) onCurrentUserUpdated?.(saved)
    } catch (error) {
      message.error(error.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const remove = async (user) => {
    try {
      await deleteUser(user.id)
      message.success('用户已删除')
      await load()
    } catch (error) {
      message.error(error.message || '删除失败')
    }
  }

  const columns = [
    {
      title: '用户',
      key: 'user',
      render: (_, user) => (
        <div className="admin-user-cell">
          <Avatar size={36} src={resolveAvatar(user.avatarPath)} icon={<UserOutlined />} />
          <div>
            <div className="admin-user-name">
              {user.username}
              {user.id === currentUser.id && <span className="admin-self-label">当前账号</span>}
            </div>
            <div className="admin-user-id">ID: {user.id}</div>
          </div>
        </div>
      ),
    },
    { title: '手机号', dataIndex: 'phone', width: 160 },
    {
      title: '角色',
      dataIndex: 'role',
      width: 110,
      render: (role) => role === 'ADMIN'
        ? <Tag color="gold">管理员</Tag>
        : <Tag>普通用户</Tag>,
    },
    {
      title: '数据源权限',
      dataIndex: 'dataSources',
      width: 180,
      render: (values = [], user) => user.role === 'ADMIN'
        ? <><Tag color="blue">MySQL</Tag><Tag color="cyan">Hive</Tag></>
        : values.length
          ? values.map((value) => <Tag key={value}>{value === 'hive' ? 'Hive' : 'MySQL'}</Tag>)
          : <Tag color="default">未授权</Tag>,
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      width: 180,
      render: formatTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, user) => (
        <>
          <Button type="link" size="small" onClick={() => openEdit(user)}>
            编辑
          </Button>
          <Divider type="vertical" />
          <Popconfirm
            title="删除用户"
            description={`确认删除“${user.username}”及其历史会话吗？`}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            disabled={user.id === currentUser.id}
            onConfirm={() => remove(user)}
          >
            <Button
              type="link"
              size="small"
              danger
              disabled={user.id === currentUser.id}
            >
              删除
            </Button>
          </Popconfirm>
        </>
      ),
    },
  ]

  return (
    <main className="admin-users-page">
      <div className="admin-users-container">
        <header className="admin-users-header">
          <div>
            <h1>用户管理</h1>
            <p>管理系统账号、角色与登录权限</p>
          </div>
          <Button type="primary" icon={<UserAddOutlined />} onClick={openCreate}>
            新增用户
          </Button>
        </header>

        <section className="admin-users-panel">
          <div className="admin-users-toolbar">
            <Input
              allowClear
              value={keyword}
              prefix={<SearchOutlined />}
              placeholder="搜索用户名或手机号"
              style={{ width: 240 }}
              onChange={(event) => setKeyword(event.target.value)}
              onPressEnter={() => load()}
            />
            <Select
              value={roleFilter}
              options={ROLE_OPTIONS}
              style={{ width: 130 }}
              onChange={setRoleFilter}
            />
            <Button type="primary" onClick={() => load()}>查询</Button>
            <Button onClick={resetSearch}>重置</Button>
            <span className="admin-users-summary">
              共 {summary.total} 个账号，其中管理员 {summary.admins} 个
            </span>
          </div>
          <Table
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={filteredUsers}
            pagination={{
              pageSize: 10,
              showSizeChanger: false,
              showTotal: (total) => `共 ${total} 条`,
            }}
            scroll={{ x: 720 }}
          />
        </section>
      </div>

      <Drawer
        title={editingUser ? '编辑用户' : '新增用户'}
        width={420}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={<Button type="primary" loading={saving} onClick={submit}>保存</Button>}
      >
        <div className="admin-avatar-editor">
          <Avatar size={72} src={avatarPreview} icon={<UserOutlined />} />
          <Upload accept="image/*" showUploadList={false} beforeUpload={chooseAvatar}>
            <Button icon={<CameraOutlined />}>{editingUser ? '更换头像' : '上传头像'}</Button>
          </Upload>
          <span>支持 JPG、PNG、GIF、WebP</span>
        </div>
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="username" label="用户名" rules={[
            { required: true, whitespace: true, message: '请输入用户名' },
            { max: 64, message: '用户名不能超过 64 个字符' },
          ]}>
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[
            { required: true, message: '请输入手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号' },
          ]}>
            <Input placeholder="请输入手机号" maxLength={11} />
          </Form.Item>
          <Form.Item name="password" label={editingUser ? '重置密码' : '登录密码'} rules={[
            ...(editingUser ? [] : [{ required: true, message: '请设置登录密码' }]),
            { min: 6, message: '密码至少 6 位' },
          ]} extra={editingUser ? '留空则保持原密码不变' : undefined}>
            <Input.Password placeholder={editingUser ? '无需修改时留空' : '至少 6 位'} />
          </Form.Item>
          <Form.Item name="role" label="系统权限" rules={[{ required: true }]}>
            <Select options={[
              { value: 'USER', label: '普通用户 — 查数与知识问答' },
              { value: 'ADMIN', label: '管理员 — 系统配置与用户管理' },
            ]} />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(previous, current) => previous.role !== current.role}
          >
            {({ getFieldValue }) => (
              <Form.Item
                name="dataSources"
                label="可访问数据源"
                extra={getFieldValue('role') === 'ADMIN'
                  ? '管理员默认拥有全部数据源权限'
                  : '用户登录后只能看到并使用这里分配的数据源'}
              >
                <Select
                  mode="multiple"
                  options={DATA_SOURCE_OPTIONS}
                  disabled={getFieldValue('role') === 'ADMIN'}
                  placeholder="请选择该用户可使用的数据源"
                />
              </Form.Item>
            )}
          </Form.Item>
        </Form>
      </Drawer>
    </main>
  )
}
