import api from '../utils/api'

export interface PublishJobItem {
  id: number
  siteId: number
  unitType: string
  unitIds: string
  mode: string
  status: string
  operatorName: string
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
export const createPublishJob = (payload: PublishRequestPayload) => api.post<PublishJobItem>('/publish/jobs', payload)
export const fetchPublishJobs = (params: Record<string, any> = {}) => api.get<PublishJobItem[]>('/publish/jobs', { params })
export const fetchPublishJobDetail = (id: number) => api.get<PublishJobItem>(`/publish/jobs/${id}`)
export const fetchPublishImpacts = (id: number) => api.get<PublishImpactItemData[]>(`/publish/jobs/${id}/impacts`)
export const fetchPublishArtifacts = (id: number) => api.get<PublishArtifactItem[]>(`/publish/jobs/${id}/artifacts`)
export const fetchPublishLogs = (id: number) => api.get<string[]>(`/publish/jobs/${id}/logs`)
export const retryPublishJob = (id: number) => api.post<PublishJobItem>(`/publish/jobs/${id}/retry`)
export const rollbackPublishJob = (id: number, payload: PublishRollbackPayload) => api.post<PublishJobItem>(`/publish/jobs/${id}/rollback`, payload)