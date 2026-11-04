import Keycloak from 'keycloak-js'

const { VITE_KEYCLOAK_URL: url, VITE_KEYCLOAK_REALM: realm, VITE_KEYCLOAK_CLIENT_ID: clientId } = import.meta.env
export const keycloak = url && realm && clientId ? new Keycloak({ url, realm, clientId }) : null
let initialization

export function initializeAuth() {
  if (!keycloak) return Promise.resolve(false)
  // Keep one initialization across React StrictMode remounts.
  initialization ??= keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
  return initialization
}

export function authSession() {
  const claims = keycloak?.tokenParsed
  const roles = [
    ...(claims?.realm_access?.roles ?? []),
    ...Object.values(claims?.resource_access ?? {}).flatMap((access) => access.roles ?? []),
  ].map((role) => role.toUpperCase())
  const managers = (import.meta.env.VITE_MEDIA_MANAGEMENT_ROLES || 'ADMIN,STAFF')
    .split(',').map((role) => role.trim().toUpperCase())
  return {
    authenticated: Boolean(keycloak?.authenticated),
    canManage: Boolean(keycloak?.authenticated) && roles.some((role) => managers.includes(role)),
    name: claims?.preferred_username || claims?.name || '',
  }
}
