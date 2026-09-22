import request from '@/utils/request'

// 登录。管理平台登录走 /admin/login（仅允许拥有 admin 角色的账号登录）
export function login(account, password) {
  return request.post('/admin/login', { account, password })
}

export function logout() {
  return request.post('/account/logout')
}
