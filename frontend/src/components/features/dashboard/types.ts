export interface ActivityItem {
  id: number
  user: string
  action: string
  target: string
  time: string
  type: 'publish' | 'edit' | 'upload' | 'review' | 'system'
}

export interface HealthItem {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  label: string
}

export interface TaskItem {
  id: number
  title: string
  author: string
  date: string
  type: string
}

export interface DashboardDto {
  articleCount?: number
  userCount?: number
  siteCount?: number
  pendingReviewCount?: number
  myDraftCount?: number
  publishQueueCount?: number
  failedTaskCount?: number
  showHealthPanel?: boolean
  recentActivities?: ActivityItem[]
  pendingArticles?: TaskItem[]
}
