// 用户侧（客户端下载页）登录态，独立于后台管理员的 wp_admin_token。
// 用户登录复用 /account/login；本模块仅负责前端 token/用户信息的存取。
const USER_TOKEN_KEY = 'wp_user_token'
const USER_INFO_KEY = 'wp_user_info'

export function getUserToken() {
  return localStorage.getItem(USER_TOKEN_KEY)
}

export function setUserToken(token) {
  localStorage.setItem(USER_TOKEN_KEY, token)
}

export function getUserInfo() {
  const raw = localStorage.getItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

export function setUserInfo(user) {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user))
}

export function clearUserAuth() {
  localStorage.removeItem(USER_TOKEN_KEY)
  localStorage.removeItem(USER_INFO_KEY)
}
