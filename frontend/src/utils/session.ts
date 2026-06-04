const SESSION_KEY = 'govcms_session'

export interface SessionData {
  token: string
  username: string
  roles?: string[]
  permissions?: string[]
  rememberMe?: boolean
}

export const saveSession = (data: SessionData) => {
  localStorage.setItem(SESSION_KEY, JSON.stringify(data))
}

export const loadSession = (): SessionData | null => {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as SessionData
  } catch {
    return null
  }
}

export const clearSession = () => {
  localStorage.removeItem(SESSION_KEY)
}

export const getToken = () => loadSession()?.token || null

export const getUsername = () => loadSession()?.username || ''

export const getRoles = () => loadSession()?.roles || []

export const getPermissions = () => loadSession()?.permissions || []

export const hasStoredPermissions = () => {
  const session = loadSession()
  return session !== null && Array.isArray(session.roles) && Array.isArray(session.permissions)
}
