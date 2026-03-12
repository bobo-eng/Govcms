import api from '../utils/api'

export interface CategoryPayload {
  siteId: number
  parentId?: number | null
  name: string
  code: string
  type: string
  slug: string
  sortOrder?: number
  status?: string
  navVisible?: boolean
  breadcrumbVisible?: boolean
  publicVisible?: boolean
  listTemplateId?: number | null
  detailTemplateId?: number | null
  aggregationMode?: string
  description?: string | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
}

export interface CategoryMovePayload {
  siteId: number
  targetParentId?: number | null
}

export interface CategorySortPayload {
  siteId: number
  sortOrder: number
}

export interface CategoryStatusPayload {
  siteId: number
  status: string
}

export const fetchCategoryTree = (params: Record<string, any>) => api.get('/categories/tree', { params })
export const fetchCategories = (params: Record<string, any>) => api.get('/categories', { params })
export const fetchCategoryDetail = (id: number, params: Record<string, any> = {}) => api.get(`/categories/${id}`, { params })
export const createCategory = (payload: CategoryPayload) => api.post('/categories', payload)
export const updateCategory = (id: number, payload: CategoryPayload) => api.put(`/categories/${id}`, payload)
export const updateCategorySort = (id: number, payload: CategorySortPayload) => api.put(`/categories/${id}/sort`, payload)
export const moveCategory = (id: number, payload: CategoryMovePayload) => api.put(`/categories/${id}/move`, payload)
export const updateCategoryStatus = (id: number, payload: CategoryStatusPayload) => api.put(`/categories/${id}/status`, payload)
export const fetchCategoryImpact = (id: number, params: Record<string, any>) => api.get(`/categories/${id}/impact`, { params })
export const deleteCategory = (id: number, params: Record<string, any>) => api.delete(`/categories/${id}`, { params })
