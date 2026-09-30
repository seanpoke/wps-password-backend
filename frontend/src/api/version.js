import request from '@/utils/request'

/** 获取版本列表（admin，分页）：platform 可选；status 可选筛选派生状态 */
export function listVersion(params) {
  return request.get('/config/version', { params })
}

/** 新增一个版本记录（admin） */
export function createVersion(payload) {
  return request.post('/config/version', payload)
}

/** 更新版本元数据（admin）：下载地址/说明/发布时间 */
export function updateVersion(id, payload) {
  return request.put(`/config/version/${id}`, payload)
}

/** 设为最低支持版本（admin） */
export function setMinVersion(id) {
  return request.put(`/config/version/${id}/min`)
}

/** 设为最新版本（admin） */
export function setLatestVersion(id) {
  return request.put(`/config/version/${id}/latest`)
}

/** 删除版本（admin） */
export function deleteVersion(id) {
  return request.delete(`/config/version/${id}`)
}

/** 上传版本安装包（admin）：返回可填入 downloadUrl 的相对路径 */
export function uploadVersionPackage(formData) {
  return request.post('/config/version/upload', formData, {
    // 让 axios 按 FormData 自动设置 multipart 边界
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
