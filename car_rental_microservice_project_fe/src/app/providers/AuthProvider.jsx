import { useEffect, useState } from 'react'
import { AuthContext } from './auth-context'
import { authSession, initializeAuth, keycloak } from '../../services/keycloak'
import { setAccessTokenProvider } from '../../services/axios-client'

export function AuthProvider({ children }) {
  const [session, setSession] = useState(authSession)
  const [ready, setReady] = useState(!keycloak)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    const refresh = () => { if (active) setSession(authSession()) }
    if (keycloak) {
      keycloak.onAuthSuccess = refresh
      keycloak.onAuthRefreshSuccess = refresh
      keycloak.onAuthLogout = refresh
      keycloak.onAuthRefreshError = () => { keycloak.clearToken(); refresh() }
    }
    setAccessTokenProvider(async () => {
      if (!keycloak?.authenticated) return null
      await keycloak.updateToken(30)
      return keycloak.token
    })
    initializeAuth().then(refresh).catch(() => {
      if (active) setError('Không thể kết nối hệ thống đăng nhập. Vui lòng thử lại sau.')
    }).finally(() => { if (active) setReady(true) })
    return () => { active = false }
  }, [])

  const value = {
    ...session, ready, error, configured: Boolean(keycloak),
    login: () => keycloak?.login({ redirectUri: window.location.href }),
    logout: () => keycloak?.logout({ redirectUri: window.location.origin }),
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
