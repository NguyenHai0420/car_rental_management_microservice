import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthContext } from './auth-context'
import { authSession, initializeAuth, keycloak } from '../../services/keycloak'
import { setAccessTokenProvider } from '../../services/axios-client'

export function AuthProvider({ children }) {
  const [session, setSession] = useState(authSession)
  const [ready, setReady] = useState(!keycloak)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    const refresh = () => {
      if (active) setSession(authSession())
    }

    if (keycloak) {
      keycloak.onAuthSuccess = refresh
      keycloak.onAuthRefreshSuccess = refresh
      keycloak.onAuthLogout = refresh
      keycloak.onAuthRefreshError = () => {
        keycloak.clearToken()
        refresh()
      }
      keycloak.onTokenExpired = () => {
        keycloak.updateToken(30)
          .then((refreshed) => {
            if (refreshed) refresh()
          })
          .catch(() => {
            keycloak.clearToken()
            refresh()
          })
      }
    }

    setAccessTokenProvider(async () => {
      if (!keycloak?.authenticated) return null
      try {
        await keycloak.updateToken(30)
      } catch {
        keycloak.clearToken()
      }
      return keycloak.token
    })

    initializeAuth()
      .then(refresh)
      .catch(() => {
        if (active) setError('Chưa kết nối được máy chủ xác thực Keycloak (:8180). Bạn vẫn có thể tra cứu xe công khai.')
      })
      .finally(() => {
        if (active) setReady(true)
      })

    return () => {
      active = false
    }
  }, [])

  const hasRole = useCallback(
    (...allowedRoles) => {
      if (!allowedRoles || allowedRoles.length === 0) return true
      const userRoles = session.roles || []
      return allowedRoles.some((role) => userRoles.includes(role))
    },
    [session.roles]
  )

  const login = useCallback((optionsOrRedirectUri) => {
    const redirectUri =
      typeof optionsOrRedirectUri === 'string'
        ? optionsOrRedirectUri
        : typeof optionsOrRedirectUri?.redirectUri === 'string'
          ? optionsOrRedirectUri.redirectUri
          : window.location.href
    return keycloak?.login({ redirectUri })
  }, [])

  const register = useCallback((optionsOrRedirectUri) => {
    const redirectUri =
      typeof optionsOrRedirectUri === 'string'
        ? optionsOrRedirectUri
        : typeof optionsOrRedirectUri?.redirectUri === 'string'
          ? optionsOrRedirectUri.redirectUri
          : window.location.href
    return keycloak?.register({ redirectUri })
  }, [])

  const logout = useCallback((optionsOrRedirectUri) => {
    const redirectUri =
      typeof optionsOrRedirectUri === 'string'
        ? optionsOrRedirectUri
        : typeof optionsOrRedirectUri?.redirectUri === 'string'
          ? optionsOrRedirectUri.redirectUri
          : window.location.origin
    return keycloak?.logout({ redirectUri })
  }, [])

  const value = useMemo(
    () => ({
      ...session,
      ready,
      initialized: ready,
      error,
      authError: error,
      configured: Boolean(keycloak),
      hasRole,
      login,
      register,
      logout,
    }),
    [session, ready, error, hasRole, login, register, logout]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
