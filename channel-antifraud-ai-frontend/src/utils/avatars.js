/**
 * 数据库 users.avatar_path 只保存头像图片的相对路径（如 src/asserts/xxx.png）。
 * Vite 构建时会把 src/asserts 中已有的图片转换为最终静态资源 URL；
 * 开发期间刚上传、尚未进入构建产物的图片则直接使用源码相对路径。
 */
const bundledAvatars = import.meta.glob('../asserts/*', {
  eager: true,
  query: '?url',
  import: 'default',
})

export function resolveAvatar(avatarPath) {
  if (!avatarPath) return ''

  const normalizedPath = avatarPath.replace(/^\/+/, '')
  const prefix = 'src/asserts/'
  if (!normalizedPath.startsWith(prefix)) return `/${normalizedPath}`

  const filename = normalizedPath.slice(prefix.length)
  return bundledAvatars[`../asserts/${filename}`] || `/${normalizedPath}`
}
