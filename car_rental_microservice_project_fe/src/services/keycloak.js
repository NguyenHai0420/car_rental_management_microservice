import Keycloak from 'keycloak-js'

const env = import.meta.env || {}
const url = env.VITE_KEYCLOAK_URL || 'http://localhost:8180'
const realm = env.VITE_KEYCLOAK_REALM || 'car-rental-realm'
const clientId = env.VITE_KEYCLOAK_CLIENT_ID || 'car-rental-fe'

export const keycloak = new Keycloak({ url, realm, clientId })
let initialization

export function initializeAuth() {
  if (!keycloak) return Promise.resolve(false)
  // Keep one initialization across React StrictMode remounts.
  initialization ??= keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
  return initialization
}

export const initKeycloak = initializeAuth

export function authSession() {
  const claims = keycloak?.tokenParsed
  const roles = [
    ...(claims?.realm_access?.roles ?? []),
    ...Object.values(claims?.resource_access ?? {}).flatMap((access) => access.roles ?? []),
  ].map((role) => role.toUpperCase())
  const managers = (env.VITE_MEDIA_MANAGEMENT_ROLES || 'ADMIN,STAFF,VEHICLE_OWNER')
    .split(',')
    .map((role) => role.trim().toUpperCase())
  return {
    authenticated: Boolean(keycloak?.authenticated),
    canManageFleet: Boolean(keycloak?.authenticated) && roles.some((role) => role === 'ADMIN' || role === 'STAFF' || role === 'VEHICLE_OWNER'),
    canManage: Boolean(keycloak?.authenticated) && roles.some((role) => managers.includes(role)),
    name: claims?.preferred_username || claims?.name || '',
    roles,
  }
}

export default keycloak
