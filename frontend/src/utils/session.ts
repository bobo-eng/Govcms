const TOKEN_KEY = 'token'
const USERNAME_KEY = 'username'
const ROLES_KEY = 'roles'
const PERMISSIONS_KEY = 'permissions'

export interface SessionPayload {
  token: string
  username: string
  roles?: string[]
  permissions?: string[]
}

const parseStringArray = (value: string | null): string[] => {
  if (!value) {
    return []
  }

  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

export const saveSession = ({ token, username, roles = [], permissions = [] }: SessionPayload) => {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USERNAME_KEY, username)
  localStorage.setItem(ROLES_KEY, JSON.stringify(roles))
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(permissions))
}

export const clearSession = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
  localStorage.removeItem(ROLES_KEY)
  localStorage.removeItem(PERMISSIONS_KEY)
}

export const getToken = () => localStorage.getItem(TOKEN_KEY)

export const getUsername = () => localStorage.getItem(USERNAME_KEY) || ''

export const getRoles = () => parseStringArray(localStorage.getItem(ROLES_KEY))

export const getPermissions = () => parseStringArray(localStorage.getItem(PERMISSIONS_KEY))

export const hasStoredPermissions = () => {
  return localStorage.getItem(ROLES_KEY) !== null && localStorage.getItem(PERMISSIONS_KEY) !== null
}
