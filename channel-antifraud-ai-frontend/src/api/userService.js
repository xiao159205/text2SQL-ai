/**
 * 用户认证服务（登录 / 注册）与本地会话管理。
 * 后端接口约定（由后端同学实现）：
 *   POST /api/auth/register  入参 multipart/form-data：
 *       username、phone、password、avatarFile 均必填；
 *       后端将 avatarFile 保存到前端工程 src/asserts 目录，
 *       并在 users.avatar_path 中记录相对路径（如 src/asserts/xxx.png）。
 *   POST /api/auth/login     入参 JSON：{ account, password }，account 可为手机号或用户名
 *   两个接口成功时均返回：{ id, username, phone, avatarPath, role, token }
 */

const SESSION_KEY = 'channel-antifraud-ai-user'

export function getSession() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY))
    if (session && !Array.isArray(session.dataSources)) {
      session.dataSources = session.role === 'ADMIN' ? ['mysql', 'hive'] : ['mysql']
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(user) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}

async function parseResponse(response) {
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }
  return body
}

function toSession(body) {
  const user = {
    id: body?.id ?? null,
    username: body?.username ?? '',
    phone: body?.phone ?? '',
    avatarPath: body?.avatarPath ?? body?.avatar_path ?? '',
    role: body?.role === 'ADMIN' ? 'ADMIN' : 'USER',
    dataSources: Array.isArray(body?.dataSources) ? body.dataSources : [],
    token: body?.token ?? '',
  }
  saveSession(user)
  return user
}

/**
 * 账号密码登录（手机号或用户名 + 密码）。
 * @returns {Promise<object>} 登录成功后的用户会话
 */
export async function login({ account, password }) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  return toSession(await parseResponse(response))
}

/**
 * 注册新用户。头像、用户名、手机号、密码均为必填。
 * @param {File} avatarFile 头像图片文件（后端负责保存到 src/asserts 并记录路径）
 * @returns {Promise<object>} 注册成功后的用户会话
 */
export async function register({ username, phone, password, avatarFile }) {
  const form = new FormData()
  form.append('username', username)
  form.append('phone', phone)
  form.append('password', password)
  form.append('avatarFile', avatarFile)

  const response = await fetch('/api/auth/register', {
    method: 'POST',
    body: form,
  })
  return toSession(await parseResponse(response))
}
