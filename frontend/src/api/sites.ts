import api from '../utils/api'

export interface SiteOptionItem {
  id: number
  name: string
  status?: string | null
}

export const fetchSiteOptions = () => api.get<SiteOptionItem[]>('/sites/options')