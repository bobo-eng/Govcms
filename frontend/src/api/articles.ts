import api from '../utils/api'

export interface ArticleItem {
  id: number
  siteId?: number | null
  primaryCategoryId?: number | null
  title: string
  content?: string | null
  summary?: string | null
  category?: string | null
  author?: string | null
  status: 'draft' | 'pending_review' | 'rejected' | 'approved' | 'published' | 'offline'
  views?: number
  submittedAt?: string | null
  submittedBy?: string | null
  approvedAt?: string | null
  approvedBy?: string | null
  publishedAt?: string | null
  publishedBy?: string | null
  offlineAt?: string | null
  offlineBy?: string | null
  rejectionReason?: string | null
  offlineReason?: string | null
  currentRevision?: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ArticlePublishCheckResponseData {
  articleId: number
  publishable: boolean
  templateId?: number | null
  templateName?: string | null
  reasons: string[]
  warnings: string[]
}

export interface ArticleLifecycleHistoryItem {
  id: number
  articleId: number
  action: string
  fromStatus?: string | null
  toStatus?: string | null
  operatorName: string
  reason?: string | null
  publishJobId?: number | null
  createdAt: string
}

export interface ArticlePayload {
  siteId?: number | null
  primaryCategoryId?: number | null
  title: string
  content?: string | null
  summary?: string | null
  author?: string | null
}

export const fetchArticles = (params: Record<string, any>) => api.get('/articles', { params })
export const fetchArticleDetail = (id: number) => api.get<ArticleItem>(`/articles/${id}`)
export const createArticle = (payload: ArticlePayload) => api.post<ArticleItem>('/articles', payload)
export const updateArticle = (id: number, payload: ArticlePayload) => api.put<ArticleItem>(`/articles/${id}`, payload)
export const deleteArticle = (id: number) => api.delete(`/articles/${id}`)
export const submitArticleReview = (id: number) => api.post<ArticleItem>(`/articles/${id}/submit-review`)
export const approveArticle = (id: number) => api.post<ArticleItem>(`/articles/${id}/approve`)
export const rejectArticle = (id: number, reason: string) => api.post<ArticleItem>(`/articles/${id}/reject`, { reason })
export const fetchArticlePublishCheck = (id: number) => api.get<ArticlePublishCheckResponseData>(`/articles/${id}/publish-check`)
export const offlineArticle = (id: number, reason: string) => api.post<ArticleItem>(`/articles/${id}/offline`, { reason })
export const fetchArticleHistories = (id: number) => api.get<ArticleLifecycleHistoryItem[]>(`/articles/${id}/histories`)