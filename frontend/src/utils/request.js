import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/store/auth'
import { getUserToken, clearUserAuth } from '@/store/userAuth'
import router from '@/router'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  // 管理员 token 优先，其次用户 token；保证用户侧请求也携带用户令牌
  const token = getToken() || getUserToken()
  if (token) {
    config.headers['token'] = token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端 ApiResponse：status === 200 成功
    if (res && res.status === 200) {
      return res
    }
    // 业务失败
    const msg = (res && (res.msg || res.message)) || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error) => {
    const status = error.response ? error.response.status : 0
    if (status === 401) {
      // 依据当前所在区域分流：用户侧(/users)跳用户登录，其余跳后台登录
      const onUserSide = location.hash.indexOf('#/users') === 0
      if (onUserSide) {
        clearUserAuth()
        ElMessage.error('登录已过期，请重新登录')
        router.push({ name: 'user-login' })
      } else {
        ElMessage.error('登录已过期，请重新登录')
        clearAuth()
        router.push({ name: 'admin-login' })
      }
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else {
      ElMessage.error((error.response && error.response.data && error.response.data.msg) || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
