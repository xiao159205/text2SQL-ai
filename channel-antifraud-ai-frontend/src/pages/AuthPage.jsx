import { useState } from 'react'
import { Button, Form, Input, Upload, message } from 'antd'
import {
  UserOutlined,
  MobileOutlined,
  LockOutlined,
  CameraOutlined,
} from '@ant-design/icons'
import { login, register } from '../api/userService'
import { resolveAvatar } from '../utils/avatars'
import BrandIcon from '../components/BrandIcon'

const PHONE_PATTERN = /^1[3-9]\d{9}$/

function AvatarPicker({ value, onChange, error }) {
  const handleUpload = (file) => {
    if (!file.type.startsWith('image/')) {
      message.error('请选择图片文件')
      return false
    }
    const reader = new FileReader()
    reader.onload = () => {
      // 前端先本地预览，提交时把文件交给后端保存到 src/asserts
      onChange({ path: `src/asserts/${file.name}`, preview: reader.result, file })
    }
    reader.readAsDataURL(file)
    return false
  }

  return (
    <div className={`avatar-picker${error ? ' has-error' : ''}`}>
      <Upload accept="image/*" showUploadList={false} beforeUpload={handleUpload}>
        <div className="avatar-preview" role="button" aria-label="上传头像">
          {value ? (
            <img src={value.preview || resolveAvatar(value.path)} alt="头像预览" />
          ) : (
            <CameraOutlined className="avatar-preview-icon" />
          )}
          <span className="avatar-preview-mask">
            <CameraOutlined />
          </span>
        </div>
      </Upload>
      <div className="avatar-picker-tip">
        点击上传头像
        <br />
        支持常见图片格式
      </div>
    </div>
  )
}

export default function AuthPage({ onLogin }) {
  const [mode, setMode] = useState('login')
  const [submitting, setSubmitting] = useState(false)
  const [avatar, setAvatar] = useState(null)
  const [avatarError, setAvatarError] = useState(false)
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()

  const switchMode = (next) => {
    setMode(next)
    setAvatarError(false)
    loginForm.resetFields()
    registerForm.resetFields()
  }

  const handleLogin = async (values) => {
    setSubmitting(true)
    try {
      const user = await login(values)
      message.success(`欢迎回来，${user.username}`)
      onLogin(user)
    } catch (e) {
      message.error(e.message || '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const handleRegister = async (values) => {
    if (!avatar) {
      setAvatarError(true)
      return
    }
    setSubmitting(true)
    try {
      const user = await register({
        ...values,
        avatarFile: avatar.file,
      })
      message.success('注册成功，已自动登录')
      onLogin(user)
    } catch (e) {
      message.error(e.message || '注册失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="auth-brand-icon">
            <BrandIcon />
          </div>
          <div className="auth-brand-title">AI Agent大数据智能查询系统</div>
          <div className="auth-brand-sub">一句话查数据</div>
        </div>

        {mode === 'login' ? (
          <Form
            form={loginForm}
            layout="vertical"
            requiredMark={false}
            onFinish={handleLogin}
            className="auth-form"
          >
            <Form.Item
              label="手机号或用户名"
              name="account"
              rules={[
                { required: true, whitespace: true, message: '请输入手机号或用户名' },
                { max: 64, message: '账号不能超过 64 个字符' },
              ]}
            >
              <Input
                size="large"
                prefix={<UserOutlined />}
                placeholder="请输入手机号或用户名"
                maxLength={64}
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                autoComplete="current-password"
              />
            </Form.Item>
            <Button
              type="primary"
              size="large"
              htmlType="submit"
              block
              loading={submitting}
              className="auth-submit"
            >
              登录
            </Button>
            <div className="auth-switch-tip">
              还没有账号？
              <Button type="link" onClick={() => switchMode('register')}>
                立即注册
              </Button>
            </div>
          </Form>
        ) : (
          <Form
            form={registerForm}
            layout="vertical"
            requiredMark={false}
            onFinish={handleRegister}
            className="auth-form"
          >
            <Form.Item label="头像" required className="auth-avatar-item">
              <AvatarPicker
                value={avatar}
                error={avatarError}
                onChange={(next) => {
                  setAvatar(next)
                  setAvatarError(false)
                }}
              />
              {avatarError && (
                <div className="avatar-error-text">请上传或选择一个头像</div>
              )}
            </Form.Item>
            <Form.Item
              label="用户名"
              name="username"
              rules={[
                { required: true, whitespace: true, message: '请输入用户名' },
                { max: 20, message: '用户名不能超过 20 个字符' },
              ]}
            >
              <Input
                size="large"
                prefix={<UserOutlined />}
                placeholder="请输入用户名"
                maxLength={20}
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item
              label="手机号"
              name="phone"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: PHONE_PATTERN, message: '请输入正确的 11 位手机号' },
              ]}
            >
              <Input
                size="large"
                prefix={<MobileOutlined />}
                placeholder="请输入手机号"
                maxLength={11}
                autoComplete="tel"
              />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[
                { required: true, message: '请设置密码' },
                { min: 6, message: '密码至少 6 位' },
              ]}
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined />}
                placeholder="请设置密码（至少 6 位）"
                autoComplete="new-password"
              />
            </Form.Item>
            <Button
              type="primary"
              size="large"
              htmlType="submit"
              block
              loading={submitting}
              className="auth-submit"
            >
              注册
            </Button>
            <div className="auth-switch-tip">
              已有账号？
              <Button type="link" onClick={() => switchMode('login')}>
                去登录
              </Button>
            </div>
          </Form>
        )}
      </div>

      <div className="auth-footer-note">AI Agent大数据智能查询系统 · 自然语言驱动的数据洞察</div>
    </div>
  )
}
