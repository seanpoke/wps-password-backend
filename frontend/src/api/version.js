import request from '@/utils/request'

/** 获取平台最新版本（免 token，供客户端下载页）：platform=win/android */
export function getLatestVersion(platform) {
  return request.get('/config/version/latest', { params: { platform } })
}

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

/** 上传版本安装包（admin）：仅暂存，返回 { tempId, fileName, size }；保存版本时携带 tempId 才发布生效 */
export function uploadVersionPackage(formData) {
  return request.post('/config/version/upload', formData, {
    // 让 axios 按 FormData 自动设置 multipart 边界
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 丢弃暂存安装包（admin）：取消编辑 / 移除时调用，幂等 */
export function discardVersionPackage(tempId) {
  return request.delete(`/config/version/upload/${encodeURIComponent(tempId)}`)
}
