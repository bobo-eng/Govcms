import axios from 'axios'
import { message } from 'ant-design-vue'
import { clearSession, getToken } from './session'

const api = axios.create({
  baseURL: '/api'
})

let redirecting = false

api.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      clearSession()
      if (!redirecting) {
        redirecting = true
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        window.setTimeout(() => {
          redirecting = false
        }, 300)
      }
    }

    return Promise.reject(error)
  }
)

export default api
