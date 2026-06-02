import api from '../utils/api'

export interface TopicItem {
  id: number
  siteId: number
  name: string
  code: string
  slug: string
  summary?: string | null
  status: string
  templateId?: number | null
  aggregationMode: string
  ruleCategoryId?: number | null
  ruleLimit?: number | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
  navVisible: boolean
}

export interface TopicPayload {
  siteId: number
  name: string
  code: string
  slug: string
  summary?: string | null
  status?: string
  templateId?: number | null
  aggregationMode?: string
  ruleCategoryId?: number | null
  ruleLimit?: number | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
  navVisible?: boolean
}

export interface TopicContentItemData {
  id: number
  topicId: number
  articleId: number
  sortOrder: number
}

export const fetchTopics = (params: Record<string, any>) => api.get<TopicItem[]>('/topics', { params })
export const fetchTopicDetail = (id: number, params: Record<string, any>) => api.get<TopicItem>(`/topics/${id}`, { params })
export const createTopic = (payload: TopicPayload) => api.post<TopicItem>('/topics', payload)
export const updateTopic = (id: number, payload: TopicPayload) => api.put<TopicItem>(`/topics/${id}`, payload)
export const deleteTopic = (id: number, params: Record<string, any>) => api.delete(`/topics/${id}`, { params })
export const fetchTopicContentItems = (id: number, params: Record<string, any>) => api.get<TopicContentItemData[]>(`/topics/${id}/content-items`, { params })
export const replaceTopicContentItems = (id: number, payload: { siteId: number; articleIds: number[] }) => api.post<TopicContentItemData[]>(`/topics/${id}/content-items`, payload)
