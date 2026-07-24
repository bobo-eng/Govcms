import api from '../utils/api'

export const PUBLISH_STATUS_ORDER = [
  'created',
  'queued',
  'staging_rendering',
  'staging_ready',
  'approved',
  'production_rendering',
  'published'
] as const

export type PublishStatus = typeof PUBLISH_STATUS_ORDER[number] | 'success' | 'failed' | 'rejected' | 'rolled_back' | 'rollback_success' | 'rollback_failed'

export const publishStatusMeta: Record<string, { label: string; color: string; bg: string; isTerminal?: boolean; isException?: boolean }> = {
  created: { label: '已创建', color: '#64748b', bg: '#f1f5f9' },
  queued: { label: '排队中', color: '#64748b', bg: '#f1f5f9' },
  staging_rendering: { label: 'Staging渲染中', color: '#2563eb', bg: '#dbeafe' },
  staging_ready: { label: '待审批', color: '#d97706', bg: '#fef3c7' },
  approved: { label: '已批准', color: '#0891b2', bg: '#cffafe' },
  production_rendering: { label: 'Production渲染中', color: '#2563eb', bg: '#dbeafe' },
  published: { label: '已发布', color: '#15803d', bg: '#dcfce7', isTerminal: true },
  success: { label: '已成功', color: '#15803d', bg: '#dcfce7', isTerminal: true },
  failed: { label: '失败', color: '#dc2626', bg: '#fee2e2', isException: true },
  rejected: { label: '已拒绝', color: '#991b1b', bg: '#fee2e2', isException: true },
  rolled_back: { label: '已回滚', color: '#7c3aed', bg: '#ede9fe', isTerminal: true },
  rollback_success: { label: '回滚成功', color: '#7c3aed', bg: '#ede9fe', isTerminal: true },
  rollback_failed: { label: '回滚失败', color: '#be123c', bg: '#ffe4e6', isException: true }
}

export const publishStatusLabel = (status?: string | null): string =>
  publishStatusMeta[status || '']?.label || status || '-'

export interface PublishJobItem {
  id: number
  siteId: number
  unitType: string
  unitIds: string
  mode: string
  status: string
  operatorName: string
  environment?: string | null
  approvalStatus?: string | null
  scheduledAt?: string | null
  previewToken?: string | null
  outputRoot?: string | null
  resultSummary?: string | null
  failureReason?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PublishImpactItemData {
  id?: number
  jobId?: number
  pageType: string
  sourceType: string
  sourceId?: number | null
  objectType: string
  objectId: number
  path: string
  action: string
  summary?: string | null
}

export interface PublishArtifactItem {
  id: number
  jobId: number
  artifactType: string
  outputPath: string
  backupPath?: string | null
  checksum?: string | null
  version?: string | null
  createdAt?: string | null
}

export interface AuditLogItem {
  id: number
  actionType: string
  objectType: string
  objectId?: number | null
  siteId?: number | null
  operatorName: string
  result: string
  summary?: string | null
  failureReason?: string | null
  relatedJobId?: number | null
  createdAt?: string | null
}

export interface PublishRollbackRecordItem {
  id: number
  rollbackJobId: number
  targetJobId: number
  reason?: string | null
  operatorName: string
  createdAt?: string | null
}

export interface PublishCheckResponseData {
  siteId: number
  unitType: string
  mode: string
  publishable: boolean
  impactCount: number
  reasons: string[]
  warnings: string[]
}

export interface PublishImpactResponseData {
  siteId: number
  unitType: string
  mode: string
  totalItems: number
  warnings: string[]
  items: PublishImpactItemData[]
}

export interface PublishRequestPayload {
  siteId: number
  unitType: string
  unitIds: number[]
  mode: string
  operatorComment?: string | null
}

export interface PublishRollbackPayload {
  siteId?: number | null
  targetJobId?: number | null
  reason?: string | null
}

export const publishCheck = (payload: PublishRequestPayload) => api.post<PublishCheckResponseData>('/publish/check', payload)
export const publishImpact = (payload: PublishRequestPayload) => api.post<PublishImpactResponseData>('/publish/impact', payload)
export const createPublishJob = (payload: PublishRequestPayload, environment = 'production', scheduledAt?: string | null) =>
  api.post<PublishJobItem>('/publish/jobs', payload, { params: { environment, scheduledAt } })
export const approvePublishJob = (id: number) => api.post<PublishJobItem>(`/publish/jobs/${id}/approve`)
export const rejectPublishJob = (id: number) => api.post<PublishJobItem>(`/publish/jobs/${id}/reject`)
export const fetchPreviewToken = (id: number) => api.get<string>(`/publish/jobs/${id}/preview`)
export const fetchPublishJobs = (params: Record<string, any> = {}) => api.get<PublishJobItem[]>('/publish/jobs', { params })
export const fetchPublishJobDetail = (id: number) => api.get<PublishJobItem>(`/publish/jobs/${id}`)
export const fetchPublishImpacts = (id: number) => api.get<PublishImpactItemData[]>(`/publish/jobs/${id}/impacts`)
export const fetchPublishArtifacts = (id: number) => api.get<PublishArtifactItem[]>(`/publish/jobs/${id}/artifacts`)
export const fetchPublishLogs = (id: number) => api.get<string[]>(`/publish/jobs/${id}/logs`)
export const fetchPublishAudits = (id: number) => api.get<AuditLogItem[]>(`/publish/jobs/${id}/audits`)
export const fetchPublishRollbackRecords = (id: number) => api.get<PublishRollbackRecordItem[]>(`/publish/jobs/${id}/rollback-records`)
export const retryPublishJob = (id: number) => api.post<PublishJobItem>(`/publish/jobs/${id}/retry`)
export const rollbackPublishJob = (id: number, payload: PublishRollbackPayload) => api.post<PublishJobItem>(`/publish/jobs/${id}/rollback`, payload)
