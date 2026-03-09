import { getPermissions } from '../utils/session'

const getPermissionSet = () => new Set(getPermissions())

export const usePermission = () => {
  const hasPermission = (code: string) => {
    if (!code) {
      return true
    }
    return getPermissionSet().has(code)
  }

  const hasAnyPermission = (codes: string[]) => {
    if (!codes.length) {
      return true
    }
    const permissionSet = getPermissionSet()
    return codes.some(code => permissionSet.has(code))
  }

  const hasAllPermissions = (codes: string[]) => {
    if (!codes.length) {
      return true
    }
    const permissionSet = getPermissionSet()
    return codes.every(code => permissionSet.has(code))
  }

  return {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions
  }
}
