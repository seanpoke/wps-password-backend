import request from '@/utils/request'

// 用户/角色 直接绑定可见部门（无权限组中间层）
// relType: USER | ROLE；relId: 用户ID / 角色ID；deptId: 部门ID

export function listVisibleDepts(relType, relId) {
  return request.get('/admin/visible-depts', { params: { relType, relId } })
}

export function addVisibleDept(data) {
  return request.post('/admin/visible-depts', data)
}

export function deleteVisibleDept(relType, relId, deptId) {
  return request.delete('/admin/visible-depts', { params: { relType, relId, deptId } })
}

// 部门反向查询：哪些用户/角色把它设为可见（DeptVisibleRef{id,relType,relId,relName}）
export function listDeptVisibleRefs(deptId) {
  return request.get(`/admin/depts/${deptId}/refs`)
}

// 部门树徽标数据：DeptRefVo{id, labels(List<String>), docAuthCount}
export function getDeptRefs() {
  return request.get('/admin/dept-refs')
}
