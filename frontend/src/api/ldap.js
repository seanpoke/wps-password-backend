import request from '@/utils/request'

export function ldapTree() {
  return request.get('/admin/ldap-tree')
}

export function refreshLdap() {
  return request.post('/admin/ldap-tree/refresh')
}

export function searchLdap(keyword) {
  return request.get('/admin/ldap-search', { params: { keyword } })
}

export function syncLdap() {
  return request.post('/admin/ldap-sync')
}

/** 同步预览：返回 DB 与 LDAP 的合并 diff 树（NEW/GONE/CHANGED/SAME） */
export function previewSync() {
  return request.get('/admin/ldap-sync/preview')
}

/** 同步确认：按管理员勾选应用（addDns 新增/更新，removeDeptIds/removeUserIds 移除） */
export function applySync(payload) {
  return request.post('/admin/ldap-sync/apply', payload)
}
