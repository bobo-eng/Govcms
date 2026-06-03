<script setup lang="ts">
import '../styles/admin-refresh.css'
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import {
  fetchPublishJobs,
  approvePublishJob,
  rejectPublishJob,
  fetchPreviewToken,
  type PublishJobItem
} from '../api/publish'

const sites = ref<SiteOptionItem[]>([])
const jobs = ref<PublishJobItem[]>([])
const loading = ref(false)
const selectedSiteId = ref<number | undefined>(undefined)
const statusFilter = ref('')

const isSingleSite = computed(() => sites.value.length <= 1)

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
  if (!selectedSiteId.value && sites.value.length) {
    selectedSiteId.value = sites.value[0].id
  }
}

const loadJobs = async () => {
  if (!selectedSiteId.value) return
  loading.value = true
  try {
    const response = await fetchPublishJobs({
      siteId: selectedSiteId.value,
      status: statusFilter.value || undefined
    })
    jobs.value = response.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载发布任务失败')
  } finally {
    loading.value = false
  }
}

const approve = async (id: number) => {
  try {
    await approvePublishJob(id)
    message.success('已批准发布')
    await loadJobs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '批准失败')
  }
}

const reject = async (id: number) => {
  try {
    await rejectPublishJob(id)
    message.success('已拒绝发布')
    await loadJobs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '拒绝失败')
  }
}

const preview = async (job: PublishJobItem) => {
  try {
    const response = await fetchPreviewToken(job.id)
    const token = response.data
    if (token) {
      window.open(`/preview/${token}`, '_blank')
    } else {
      message.warning('暂无预览令牌')
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取预览链接失败')
  }
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    created: '已创建',
    queued: '排队中',
    staging_rendering: 'Staging渲染中',
    staging_ready: '待审批',
    approved: '已批准',
    production_rendering: 'Production渲染中',
    published: '已发布',
    rejected: '已拒绝',
    failed: '失败',
    rolled_back: '已回滚'
  }
  return map[status] || status
}

onMounted(async () => {
  await loadSites()
  await loadJobs()
})
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <h1 class="admin-page-title">发布任务</h1>
    </div>
    <div class="admin-toolbar-card">
      <select v-model="selectedSiteId" class="admin-filter-select" :disabled="isSingleSite" @change="loadJobs">
        <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
      </select>
      <select v-model="statusFilter" class="admin-filter-select" @change="loadJobs">
        <option value="">全部状态</option>
        <option value="staging_ready">待审批</option>
        <option value="published">已发布</option>
        <option value="failed">失败</option>
      </select>
    </div>
    <div v-if="loading" class="admin-empty-state">加载中...</div>
    <div v-else class="admin-card-list">
      <div v-for="job in jobs" :key="job.id" class="admin-card">
        <div class="admin-card-header">
          <span>#{{ job.id }} {{ job.mode }} — {{ statusLabel(job.status) }}</span>
          <span class="admin-sub-text">{{ job.operatorName }}</span>
        </div>
        <div class="admin-sub-text">环境: {{ job.environment || 'production' }}</div>
        <div v-if="job.scheduledAt" class="admin-sub-text">计划时间: {{ job.scheduledAt.replace('T', ' ').slice(0, 16) }}</div>
        <div v-if="job.failureReason" class="searchops-warning-text">{{ job.failureReason }}</div>
        <div class="admin-toolbar-row">
          <button v-if="job.status === 'staging_ready'" class="admin-primary-btn" @click="approve(job.id)">批准上线</button>
          <button v-if="job.status === 'staging_ready'" class="admin-danger-btn" @click="reject(job.id)">拒绝</button>
          <button v-if="job.previewToken" class="admin-secondary-btn" @click="preview(job)">预览</button>
        </div>
      </div>
      <div v-if="!jobs.length" class="admin-empty-state">暂无发布任务</div>
    </div>
  </div>
</template>
