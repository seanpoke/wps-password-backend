import request from '@/utils/request'

export function listDepts() {
  return request.get('/admin/depts')
}

export function createDept(data) {
  return request.post('/admin/depts', data)
}

export function updateDept(id, data) {
  return request.put(`/admin/depts/${id}`, data)
}

export function deleteDept(id) {
  return request.delete(`/admin/depts/${id}`)
}

export function listDeptRefs() {
  return request.get('/admin/dept-refs')
}
