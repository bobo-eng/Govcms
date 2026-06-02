<script setup lang="ts">
import '../styles/admin-refresh.css'

import { ref, onMounted } from 'vue'
import {
  FileTextOutlined, TeamOutlined, FolderOutlined,
  EyeOutlined, ArrowUpOutlined, ArrowDownOutlined,
  PlusOutlined,
  EditOutlined, CloudUploadOutlined, UserAddOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import api from '../utils/api'

interface DashboardStats {
  articleCount: number
  userCount: number
  siteCount: number
  viewCount: number
  pendingReviewCount: number
  recentActivities: RecentActivity[]
  pendingArticles: PendingArticle[]
}

interface RecentActivity {
  id: number
  user: string
  action: string
  target: string
  time: string
  type: string
}

interface PendingArticle {
  id: number
  title: string
  type: string
  author: string
  date: string
}

const router = useRouter()
const loading = ref(false)
const stats = ref<DashboardStats>({
  articleCount: 0,
  userCount: 0,
  siteCount: 0,
  viewCount: 0,
  pendingReviewCount: 0,
  recentActivities: [],
  pendingArticles: []
})

const statCards = ref([
  { title: '内容总数', key: 'articleCount', icon: FileTextOutlined, trend: '+12%', up: true },
  { title: '用户总数', key: 'userCount', icon: TeamOutlined, trend: '+8%', up: true },
  { title: '站点总数', key: 'siteCount', icon: FolderOutlined, trend: '+3%', up: true },
  { title: '访问量', key: 'viewCount', icon: EyeOutlined, trend: '-5%', up: false }
])

const getActivityIcon = (type: string) => {
  const map: Record<string, any> = {
    publish: FileTextOutlined,
    edit: EditOutlined,
    upload: CloudUploadOutlined,
    create: UserAddOutlined
  }
  return map[type] || FileTextOutlined
}

const fetchDashboardData = async () => {
  loading.value = true
  try {
    const res = await api.get('/statistics/dashboard')
    stats.value = res.data
  } catch (e) {
    console.error('获取仪表盘数据失败:', e)
  } finally {
    loading.value = false
  }
}

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const goCreateContent = () => {
  router.push('/content')
}

onMounted(() => {
  fetchDashboardData()
})
</script>

<template>
  <div class="admin-page dashboard-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">欢迎回来</h1>
        <p class="admin-page-desc">{{ today }}</p>
      </div>
      <button class="admin-primary-btn" @click="goCreateContent">
        <PlusOutlined />
        <span>新建内容</span>
      </button>
    </div>

    <div class="admin-grid-2 dashboard-top-grid">
      <div class="admin-card welcome-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">工作台概览</h3>
        </div>
        <div class="admin-sub-text">快速查看站点运行与内容运营概况。</div>
      </div>
    </div>

    <div class="admin-grid-4 dashboard-stat-grid">
      <div v-for="stat in statCards" :key="stat.key" class="admin-metric-card dashboard-stat-card">
        <div class="dashboard-stat-header">
          <span class="dashboard-stat-title">{{ stat.title }}</span>
          <div class="dashboard-stat-icon">
            <component :is="stat.icon" />
          </div>
        </div>
        <div class="admin-metric-value">{{ stats[stat.key as keyof DashboardStats] || 0 }}</div>
        <div class="dashboard-trend" :class="{ up: stat.up, down: !stat.up }">
          <component :is="stat.up ? ArrowUpOutlined : ArrowDownOutlined" />
          <span>{{ stat.trend }}</span>
          <span class="admin-sub-text">较上周</span>
        </div>
      </div>
    </div>

    <div class="admin-grid-2 dashboard-content-grid">
      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">最近活动</h3>
        </div>
        <div v-if="stats.recentActivities?.length" class="dashboard-activity-list">
          <div v-for="item in stats.recentActivities" :key="item.id" class="dashboard-activity-item">
            <div class="dashboard-activity-icon" :class="item.type">
              <component :is="getActivityIcon(item.type)" />
            </div>
            <div class="dashboard-activity-content">
              <div class="dashboard-activity-main">
                <span class="dashboard-activity-user">{{ item.user }}</span>
                <span class="dashboard-activity-action">{{ item.action }}</span>
              </div>
              <div class="admin-sub-text">{{ item.target }}</div>
            </div>
            <div class="admin-sub-text">{{ item.time }}</div>
          </div>
        </div>
        <div v-else class="admin-empty-state">暂无活动记录</div>
      </div>

      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">待审核内容</h3>
          <span class="admin-chip admin-chip--warning" v-if="stats.pendingReviewCount">{{ stats.pendingReviewCount }} 项</span>
        </div>
        <div v-if="stats.pendingArticles?.length" class="dashboard-review-list">
          <div v-for="item in stats.pendingArticles" :key="item.id" class="dashboard-review-item">
            <div class="dashboard-review-info">
              <span class="dashboard-review-title">{{ item.title }}</span>
              <div class="admin-sub-text">{{ item.author }} · {{ item.date }}</div>
            </div>
            <span class="admin-chip">{{ item.type }}</span>
          </div>
        </div>
        <div v-else class="admin-empty-state">暂无待审核内容</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page .dashboard-top-grid {
  grid-template-columns: 1fr;
}

.dashboard-page .dashboard-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-page .dashboard-stat-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dashboard-page .dashboard-stat-header,
.dashboard-page .dashboard-trend,
.dashboard-page .dashboard-activity-main,
.dashboard-page .dashboard-activity-item,
.dashboard-page .dashboard-review-item {
  display: flex;
  align-items: center;
}

.dashboard-page .dashboard-stat-header,
.dashboard-page .dashboard-activity-item,
.dashboard-page .dashboard-review-item {
  justify-content: space-between;
}

.dashboard-page .dashboard-stat-title {
  font-size: 14px;
  color: #64748b;
}

.dashboard-page .dashboard-stat-icon,
.dashboard-page .dashboard-activity-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
}

.dashboard-page .dashboard-trend {
  gap: 6px;
  font-size: 13px;
}

.dashboard-page .dashboard-trend.up { color: #16a34a; }
.dashboard-page .dashboard-trend.down { color: #dc2626; }

.dashboard-page .dashboard-content-grid {
  align-items: start;
}

.dashboard-page .dashboard-activity-list,
.dashboard-page .dashboard-review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-page .dashboard-activity-item,
.dashboard-page .dashboard-review-item {
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 12px;
}

.dashboard-page .dashboard-activity-content,
.dashboard-page .dashboard-review-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dashboard-page .dashboard-activity-user,
.dashboard-page .dashboard-review-title {
  color: #0f172a;
  font-weight: 600;
}

.dashboard-page .dashboard-activity-action {
  color: #475569;
  margin-left: 6px;
}

@media (max-width: 1080px) {
  .dashboard-page .dashboard-stat-grid,
  .dashboard-page .dashboard-content-grid {
    grid-template-columns: 1fr;
  }
}
</style>

