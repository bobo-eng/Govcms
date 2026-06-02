import api from '../utils/api'

export interface AuditLogQueryParams {
  siteId?: number | null
  actionType?: string
  result?: string
  operatorName?: string
  page?: number
  size?: number
}

export interface AuditLogItem {
  id: number
  actionType: string
  objectType: string
  objectId: number | null
  siteId: number | null
  operatorName: string
  result: string
  summary: string | null
  failureReason: string | null
  relatedJobId: number | null
  createdAt: string
}

export interface AuditLogPage {
  content: AuditLogItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const getAuditLogs = (params: AuditLogQueryParams) =>
  api.get<AuditLogPage>('/audit-logs', { params })
