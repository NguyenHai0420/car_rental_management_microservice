import axios from 'axios'
import keycloak from './keycloak.js'

let tokenProvider = async () => null

export function setAccessTokenProvider(provider) {
  tokenProvider = provider
}

const env = import.meta.env || {}
const apiClient = axios.create({
  baseURL:
    env.VITE_API_BASE_URL ||
    env.VITE_API_GATEWAY_URL ||
    'http://localhost:8080',
  timeout: 60000,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use(
  async (config) => {
    let token = await tokenProvider()
    if (!token && keycloak?.authenticated) {
      if (keycloak.token && !keycloak.isTokenExpired(30)) {
        token = keycloak.token
      } else if (keycloak.refreshToken) {
        try {
          await keycloak.updateToken(30)
          token = keycloak.token
        } catch {
          if (keycloak.isTokenExpired(0)) {
            keycloak.clearToken()
            token = null
          }
        }
      } else if (keycloak.isTokenExpired(0)) {
        keycloak.clearToken()
        token = null
      } else {
        token = keycloak.token
      }
    }
    if (token) {
      if (typeof config.headers?.set === 'function') {
        config.headers.set('Authorization', `Bearer ${token}`)
      } else {
        config.headers = config.headers || {}
        config.headers['Authorization'] = `Bearer ${token}`
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const backendMessage =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'Không thể kết nối tới hệ thống máy chủ.'
    error.userMessage = backendMessage
    return Promise.reject(error)
  }
)

export default apiClient
