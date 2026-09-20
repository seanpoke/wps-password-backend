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
