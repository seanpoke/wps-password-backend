import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/store/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = getToken()
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
      ElMessage.error('登录已过期，请重新登录')
      clearAuth()
      router.push({ name: 'login' })
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else {
      ElMessage.error((error.response && error.response.data && error.response.data.msg) || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
