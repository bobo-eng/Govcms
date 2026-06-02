import api from '../utils/api'

export interface SearchKeywordStatItem {
  keyword: string
  count: number
}

export interface SearchSuggestionItem {
  keyword: string
  source: 'history' | 'popular'
  count?: number | null
}

export interface SearchIndexStatusData {
  siteId: number
  totalEntries: number
  lastRebuildAt?: string | null
  lastRebuildSummary?: string | null
  lastFailureReason?: string | null
  hotKeywords: SearchKeywordStatItem[]
  zeroResultKeywords: SearchKeywordStatItem[]
  lowResultKeywords: SearchKeywordStatItem[]
}

export const fetchSearchIndexStatus = (siteId: number, limit = 10, days = 7) =>
  api.get<SearchIndexStatusData>('/search-index/status', { params: { siteId, limit, days } })

export const rebuildSearchIndexSite = (siteId: number, limit = 10, days = 7) =>
  api.post<SearchIndexStatusData>(`/search-index/rebuild/site/${siteId}`, null, { params: { limit, days } })

export const rebuildSearchIndexContent = (articleId: number, limit = 10, days = 7) =>
  api.post<SearchIndexStatusData>(`/search-index/rebuild/content/${articleId}`, null, { params: { limit, days } })

export const rebuildSearchIndexTopic = (topicId: number, limit = 10, days = 7) =>
  api.post<SearchIndexStatusData>(`/search-index/rebuild/topic/${topicId}`, null, { params: { limit, days } })

export const rebuildSearchIndexCategory = (categoryId: number, limit = 10, days = 7) =>
  api.post<SearchIndexStatusData>(`/search-index/rebuild/category/${categoryId}`, null, { params: { limit, days } })

export const fetchSearchSuggestions = (siteId: number, keyword = '', limit = 8) =>
  api.get<SearchSuggestionItem[]>('/portal/search/suggestions', { params: { siteId, keyword, limit } })
