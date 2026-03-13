import api from '../utils/api'

export interface TemplateItem {
  id: number
  siteId: number
  name: string
  code: string
  type: string
  status: string
  description?: string | null
  currentVersionId?: number | null
  latestVersionNo: number
  defaultPreviewSource?: string | null
  bindingCount: number
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface TemplateVersionItem {
  id: number
  templateId: number
  versionNo: number
  layoutSchema: string
  blockSchema: string
  seoSchema?: string | null
  styleSchema?: string | null
  changeLog?: string | null
  createdBy?: string
  createdAt?: string
}

export interface TemplateBindingItem {
  id: number
  siteId: number
  templateId: number
  templateVersionId?: number | null
  targetType: string
  targetId: number
  bindingSlot: string
  status: string
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface TemplatePreviewResponseData {
  templateId: number
  templateName: string
  templateVersionId: number
  templateType: string
  versionNo: number
  pageType: string
  sourceType: string
  sourceId?: number | null
  layoutSchema: string
  blockSchema: string
  seoSchema?: string | null
  styleSchema?: string | null
  context: Record<string, any>
  summary: Record<string, any>
  warnings: string[]
  renderedHtml?: string
  renderEngine?: string
  renderTemplateName?: string
  message: string
}

export interface TemplateImpactResponseData {
  templateId: number
  templateName: string
  templateType: string
  activeBindingCount: number
  targetTypeCounts: Record<string, number>
  sampleTargets: string[]
  warnings: string[]
}

export interface TemplatePayload {
  siteId: number
  name: string
  code: string
  type: string
  status: string
  description?: string | null
  layoutSchema: string
  blockSchema: string
  seoSchema?: string | null
  styleSchema?: string | null
  changeLog?: string | null
  defaultPreviewSource?: string | null
}

export interface TemplateStatusPayload {
  siteId: number
  status: string
}

export interface TemplateVersionPayload {
  layoutSchema: string
  blockSchema: string
  seoSchema?: string | null
  styleSchema?: string | null
  changeLog?: string | null
}

export interface TemplateRollbackPayload {
  siteId: number
  versionId?: number
  versionNo?: number
}

export interface TemplateBindingPayload {
  siteId: number
  targetType: string
  targetId: number
  bindingSlot: string
  templateVersionId?: number | null
  replaceExisting?: boolean
}

export interface TemplatePreviewPayload {
  siteId: number
  sourceType?: string | null
  sourceId?: number | null
}

export const fetchTemplates = (params: Record<string, any>) => api.get<TemplateItem[]>('/templates', { params })
export const fetchTemplateDetail = (id: number, params: Record<string, any> = {}) => api.get<TemplateItem>(`/templates/${id}`, { params })
export const createTemplate = (payload: TemplatePayload) => api.post<TemplateItem>('/templates', payload)
export const updateTemplate = (id: number, payload: Partial<TemplatePayload>) => api.put<TemplateItem>(`/templates/${id}`, payload)
export const updateTemplateStatus = (id: number, payload: TemplateStatusPayload) => api.put<TemplateItem>(`/templates/${id}/status`, payload)
export const fetchTemplateVersions = (id: number, params: Record<string, any> = {}) => api.get<TemplateVersionItem[]>(`/templates/${id}/versions`, { params })
export const createTemplateVersion = (id: number, payload: TemplateVersionPayload) => api.post<TemplateVersionItem>(`/templates/${id}/versions`, payload)
export const rollbackTemplateVersion = (id: number, payload: TemplateRollbackPayload) => api.post<TemplateItem>(`/templates/${id}/rollback`, payload)
export const fetchTemplateBindings = (id: number, params: Record<string, any>) => api.get<TemplateBindingItem[]>(`/templates/${id}/bindings`, { params })
export const createTemplateBinding = (id: number, payload: TemplateBindingPayload) => api.post<TemplateBindingItem>(`/templates/${id}/bindings`, payload)
export const deleteTemplateBinding = (bindingId: number, params: Record<string, any>) => api.delete(`/templates/bindings/${bindingId}`, { params })
export const previewTemplate = (id: number, payload: TemplatePreviewPayload) => api.post<TemplatePreviewResponseData>(`/templates/${id}/preview`, payload)
export const fetchTemplateImpact = (id: number, params: Record<string, any>) => api.get<TemplateImpactResponseData>(`/templates/${id}/impact`, { params })
