import api from '../utils/api'

export interface NavigationItemData {
  id: number
  siteId: number
  parentId?: number | null
  name: string
  code: string
  targetType: string
  targetId?: number | null
  targetValue?: string | null
  sortOrder: number
  status: string
  primaryNav: boolean
  breadcrumbEnabled: boolean
}

export interface NavigationPayload {
  siteId: number
  parentId?: number | null
  name: string
  code: string
  targetType: string
  targetId?: number | null
  targetValue?: string | null
  sortOrder?: number
  status?: string
  primaryNav?: boolean
  breadcrumbEnabled?: boolean
}

export const fetchNavigationItems = (params: Record<string, any>) => api.get<NavigationItemData[]>('/navigation', { params })
export const fetchNavigationDetail = (id: number, params: Record<string, any>) => api.get<NavigationItemData>(`/navigation/${id}`, { params })
export const createNavigationItem = (payload: NavigationPayload) => api.post<NavigationItemData>('/navigation', payload)
export const updateNavigationItem = (id: number, payload: NavigationPayload) => api.put<NavigationItemData>(`/navigation/${id}`, payload)
export const updateNavigationSort = (id: number, payload: { siteId: number; sortOrder: number }) => api.put<NavigationItemData>(`/navigation/${id}/sort`, payload)
export const deleteNavigationItem = (id: number, params: Record<string, any>) => api.delete(`/navigation/${id}`, { params })