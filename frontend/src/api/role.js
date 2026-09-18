import request from '@/utils/request'

export function listRoles() {
  return request.get('/admin/roles')
}

export function createRole(data) {
  return request.post('/admin/roles', data)
}

export function updateRole(id, data) {
  return request.put(`/admin/roles/${id}`, data)
}

export function deleteRole(id) {
  return request.delete(`/admin/roles/${id}`)
}

export function findUser(account) {
  return request.get('/admin/users/find', { params: { account } })
}

export function listUserRoles(account) {
  return request.get('/admin/user-roles', { params: { account } })
}

export function bindUserRole(data) {
  return request.post('/admin/user-roles', data)
}

export function unbindUserRole(id) {
  return request.delete(`/admin/user-roles/${id}`)
}
