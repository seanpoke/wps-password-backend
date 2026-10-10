import request from '@/utils/request'

/** 查询密码审计日志：后端按 uid/路径/用户/平台/时间过滤，实时读文件，不落库 */
export function queryAudit(payload) {
  return request.post('/admin/audit/query', payload)
}

/** 解密密文：复用 /config/decrypt（系统私钥），返回明文 */
export function decryptText(encryptedText, keyVersion) {
  return request.post('/config/decrypt', {
    encryptedText,
    keyVersion: keyVersion || 'default'
  })
}
