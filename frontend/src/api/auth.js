import request from '@/utils/request'

// 后台管理平台登录：/admin/login（仅允许拥有 admin 角色的账号登录）
export function login(account, password) {
  return request.post('/admin/login', { account, password })
}

// 用户侧（客户端下载页）登录：复用 /account/login
export function userLogin(account, password) {
  return request.post('/account/login', { account, password })
}

export function logout() {
  return request.post('/account/logout')
}
