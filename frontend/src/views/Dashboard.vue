<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  FileTextOutlined, TeamOutlined, FolderOutlined, 
  EyeOutlined, ArrowUpOutlined, ArrowDownOutlined,
  PlusOutlined, ClockCircleOutlined, CheckCircleOutlined,
  EditOutlined, CloudUploadOutlined, UserAddOutlined
} from '@ant-design/icons-vue'
import axios from 'axios'

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

const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

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

onMounted(() => { 
  fetchDashboardData() 
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎区域 -->
    <div class="welcome-card">
      <div class="welcome-content">
        <h1>欢迎回来</h1>
        <p>{{ today }}</p>
      </div>
      <button class="action-btn">
        <PlusOutlined />
        <span>新建内容</span>
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div v-for="stat in statCards" :key="stat.key" class="stat-card">
        <div class="stat-header">
          <span class="stat-title">{{ stat.title }}</span>
          <div class="stat-icon">
            <component :is="stat.icon" />
          </div>
        </div>
        <div class="stat-value">{{ stats[stat.key as keyof DashboardStats] || 0 }}</div>
        <div class="stat-trend" :class="{ up: stat.up, down: !stat.up }">
          <component :is="stat.up ? ArrowUpOutlined : ArrowDownOutlined" />
          <span>{{ stat.trend }}</span>
          <span class="trend-label">较上周</span>
        </div>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content-grid">
      <!-- 最近活动 -->
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">
            <ClockCircleOutlined />
            最近活动
          </h3>
        </div>
        <div class="activity-list">
          <div v-for="item in stats.recentActivities" :key="item.id" class="activity-item">
            <div class="activity-icon" :class="item.type">
              <component :is="getActivityIcon(item.type)" />
            </div>
            <div class="activity-content">
              <div class="activity-main">
                <span class="activity-user">{{ item.user }}</span>
                <span class="activity-action">{{ item.action }}</span>
              </div>
              <div class="activity-title">{{ item.target }}</div>
            </div>
            <div class="activity-time">{{ item.time }}</div>
          </div>
          <div v-if="!stats.recentActivities?.length" class="empty-tip">
            暂无活动记录
          </div>
        </div>
      </div>

      <!-- 待审核 -->
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">
            <CheckCircleOutlined />
            待审核内容
          </h3>
          <span class="badge" v-if="stats.pendingReviewCount">{{ stats.pendingReviewCount }} 项</span>
        </div>
        <div class="review-list">
          <div v-for="item in stats.pendingArticles" :key="item.id" class="review-item">
            <div class="review-info">
              <span class="review-title">{{ item.title }}</span>
              <div class="review-meta">
                <span class="review-type">{{ item.type }}</span>
                <span class="review-divider">·</span>
                <span>{{ item.author }}</span>
              </div>
            </div>
            <button class="review-btn">审核</button>
          </div>
          <div v-if="!stats.pendingArticles?.length" class="empty-tip">
            暂无待审核内容
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1400px;
}

/* 欢迎卡片 */
.welcome-card {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  box-shadow: 0 4px 14px -3px rgba(37, 99, 235, 0.4);
}

.welcome-content h1 {
  font-size: 28px;
  font-weight: 600;
  color: #fff;
  margin: 0 0 8px;
}

.welcome-content p {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-1px);
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

@media (max-width: 1200px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 576px) {
  .stats-grid { grid-template-columns: 1fr; }
}

.stat-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.2s;
}

.stat-card:hover {
  border-color: #2563eb;
  box-shadow: 0 4px 12px -3px rgba(37, 99, 235, 0.15);
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.stat-title {
  font-size: 14px;
  color: #64748b;
}

.stat-icon {
  width: 40px;
  height: 40px;
  background: #eff6ff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
  font-size: 18px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #1e293b;
  letter-spacing: -1px;
  margin-bottom: 8px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
}

.stat-trend.up {
  color: #10b981;
}

.stat-trend.down {
  color: #ef4444;
}

.trend-label {
  color: #94a3b8;
  font-weight: 400;
  margin-left: 4px;
}

/* 内容区域 */
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

@media (max-width: 992px) {
  .content-grid { grid-template-columns: 1fr; }
}

.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #f1f5f9;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.card-title :deep(.anticon) {
  color: #2563eb;
}

.badge {
  background: #fef3c7;
  color: #d97706;
  font-size: 13px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 20px;
}

/* 活动列表 */
.activity-list {
  padding: 8px 0;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px;
  transition: background 0.15s;
  cursor: pointer;
}

.activity-item:hover {
  background: #f8fafc;
}

.activity-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.activity-icon.publish {
  background: #ecfdf5;
  color: #10b981;
}

.activity-icon.edit {
  background: #eff6ff;
  color: #2563eb;
}

.activity-icon.upload {
  background: #f5f3ff;
  color: #7c3aed;
}

.activity-icon.create {
  background: #fef3c7;
  color: #d97706;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-main {
  font-size: 14px;
  margin-bottom: 4px;
}

.activity-user {
  font-weight: 500;
  color: #1e293b;
}

.activity-action {
  color: #64748b;
  margin-left: 4px;
}

.activity-title {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.activity-time {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

/* 审核列表 */
.review-list {
  padding: 8px 0;
}

.review-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  transition: background 0.15s;
}

.review-item:hover {
  background: #f8fafc;
}

.review-info {
  flex: 1;
  min-width: 0;
}

.review-title {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 4px;
}

.review-meta {
  font-size: 13px;
  color: #94a3b8;
}

.review-type {
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.review-divider {
  margin: 0 6px;
}

.review-btn {
  padding: 6px 14px;
  background: #2563eb;
  border: none;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.review-btn:hover {
  background: #1d4ed8;
}

/* Empty tip */
.empty-tip {
  text-align: center;
  padding: 40px 20px;
  color: #94a3b8;
  font-size: 14px;
}
</style>
