import request from '@/utils/request'

/** 部门管理-LDAP 页签：直接读取内存缓存中的 LDAP 组织树（部门+用户，按来源+根节点分组） */
export function ldapTree() {
  return request.get('/admin/ldap-tree')
}

/** 同步预览：返回 DB 与 LDAP 的合并 diff 树（NEW/GONE/CHANGED/SAME） */
export function previewSync() {
  return request.get('/admin/ldap-sync/preview')
}

/** 同步确认：按管理员勾选应用（addDns 新增/更新，removeDeptIds/removeUserIds 移除） */
export function applySync(payload) {
  return request.post('/admin/ldap-sync/apply', payload)
}
