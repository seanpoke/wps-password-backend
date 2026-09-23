import request from '@/utils/request'

/** 获取 LDAP 配置（不含密码）：url/base/username/trees/syncTimes/syncEnabled */
export function getLdapConfig() {
  return request.get('/config/ldap')
}

/** 结构化保存 LDAP 配置：连接信息 + subTree 多值 + 同步周期 + 开关 */
export function updateLdapConfig(payload) {
  return request.put('/config/ldap', payload)
}

/** 立即触发全量同步（按当前 subTree 配置） */
export function triggerFullSync() {
  return request.post('/admin/ldap-sync/full')
}

/** 查询上次同步状态 */
export function getSyncStatus() {
  return request.get('/admin/ldap-sync/status')
}
