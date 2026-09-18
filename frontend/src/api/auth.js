import request from '@/utils/request'

// 登录。注意：后端禁止 account=admin，真实管理员账号为 admin_local
export function login(account, password) {
  return request.post('/account/login', { account, password })
}

export function logout() {
  return request.post('/account/logout')
}
