import request from '@/utils/request'

export function listUsers() {
  return request.get('/admin/users')
}

// 后端分页查询：page 从 1 开始；keyword 匹配账号/姓名；source 为来源枚举（LOCAL/LDAP）；roleId 按角色过滤
export function pageUsers(params) {
  return request.get('/admin/users/page', { params })
}

// 用户来源枚举（后端维护，[{code,label}]）
export function listUserSources() {
  return request.get('/admin/users/sources')
}

// 查询某用户已绑定的可见部门（relType=USER）
export function listUserVisibleDepts(userId) {
  return request.get('/admin/visible-depts', { params: { relType: 'USER', relId: userId } })
}

export function createUser(data) {
  return request.post('/admin/users', data)
}

export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

export function resetPassword(id, newPassword) {
  return request.post(`/admin/users/${id}/reset-password`, { newPassword })
}
