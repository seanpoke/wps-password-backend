import request from '@/utils/request'

// 用户/角色 直接绑定可见部门（无权限组中间层）
// relType: USER | ROLE；relId: 用户ID / 角色ID；deptId: 部门ID

export function listVisibleDepts(relType, relId) {
  return request.get('/admin/visible-depts', { params: { relType, relId } })
}

// 部门树徽标数据：DeptRefVo{id, labels(List<String>), docAuthCount}
export function getDeptRefs() {
  return request.get('/admin/dept-refs')
}
