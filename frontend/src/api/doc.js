import request from '@/utils/request'

// 文档分页查询：page 从 1 开始；keyword 模糊匹配 uid/文档名称/所属账号
export function pageDocs(params) {
  return request.get('/admin/docs/page', { params })
}

// 文档详情：基本信息 + 授权信息（部门/用户）
export function getDocDetail(id) {
  return request.get(`/admin/docs/${id}`)
}

// 删除文档及其授权关系
export function deleteDoc(id) {
  return request.delete(`/admin/docs/${id}`)
}

// 主动刷新组织树缓存（管理平台按钮触发，DB-only）：仅按 sys_dept/sys_user 重建
// 权限树与部门缓存、原子替换、不连 LDAP；重建后客户端（安卓等）拉取 /doc/auth/tree 即获最新结构。
export function refreshOrgTree() {
  return request.post('/doc/auth/tree/refresh')
}
