import axios from 'axios'

let tokenProvider = async () => null

export function setAccessTokenProvider(provider) {
  tokenProvider = provider
}

const apiClient = axios.create({
  baseURL: import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 60_000,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use(async (config) => {
  const token = await tokenProvider()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  // Axios/browser supply the multipart boundary for FormData.
  return config
})

export default apiClient
