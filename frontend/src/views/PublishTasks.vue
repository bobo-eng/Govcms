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
  publishStatusLabel,
  publishStatusMeta,
  type PublishJobItem
} from '../api/publish'

const sites = ref<SiteOptionItem[]>([])
const jobs = ref<PublishJobItem[]>([])
const loading = ref(false)
const selectedSiteId = ref<number | undefined>(undefined)
const statusFilter = ref('')

const isSingleSite = computed(() => sites.value.length <= 1)

const pendingCount = computed(() => jobs.value.filter(j => j.status === 'staging_ready').length)
const failedCount = computed(() => jobs.value.filter(j => j.status === 'failed').length)
const rejectedCount = computed(() => jobs.value.filter(j => j.status === 'rejected').length)

const filteredJobs = computed(() => {
  let list = jobs.value
    .filter(j => ['staging_ready', 'failed', 'rejected'].includes(j.status))
    .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
  if (statusFilter.value) {
    list = list.filter(j => j.status === statusFilter.value)
  }
  return list
})

const statusColor = (status: string) => publishStatusMeta[status]?.color || '#94a3b8'
const statusBg = (status: string) => publishStatusMeta[status]?.bg || '#f1f5f9'

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

onMounted(async () => {
  await loadSites()
  await loadJobs()
})
</script>

<template>
  <div class="admin-page publish-tasks-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">发布任务审批</h1>
        <p class="admin-page-desc">审批、预览和处理需要人工关注的发布任务。</p>
      </div>
    </div>

    <!-- Hero Stats -->
    <div class="admin-stats-row">
      <div class="admin-stat-card">
        <div class="admin-stat-value" style="color: #d97706;">{{ pendingCount }}</div>
        <div class="admin-stat-label">待审批</div>
      </div>
      <div class="admin-stat-card">
        <div class="admin-stat-value" style="color: #dc2626;">{{ failedCount }}</div>
        <div class="admin-stat-label">失败</div>
      </div>
      <div class="admin-stat-card">
        <div class="admin-stat-value" style="color: #991b1b;">{{ rejectedCount }}</div>
        <div class="admin-stat-label">已拒绝</div>
      </div>
    </div>

    <div class="admin-toolbar-card">
      <select v-model="selectedSiteId" class="admin-filter-select" :disabled="isSingleSite" @change="loadJobs">
        <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
      </select>
      <select v-model="statusFilter" class="admin-filter-select" @change="loadJobs">
        <option value="">全部关注状态</option>
        <option value="staging_ready">待审批</option>
        <option value="failed">失败</option>
        <option value="rejected">已拒绝</option>
      </select>
      <button class="admin-secondary-btn" @click="loadJobs">刷新</button>
    </div>

    <div v-if="loading" class="admin-empty-state">加载中...</div>
    <ul v-else class="publish-pipeline-list">
      <li
        v-for="job in filteredJobs"
        :key="job.id"
        class="publish-pipeline-item"
        :style="{ '--status-color': statusColor(job.status) }"
      >
        <div class="pipeline-status-bar" />
        <div class="pipeline-body">
          <div class="pipeline-header">
            <div class="pipeline-title">
              <span class="pipeline-id">#{{ job.id }}</span>
              <span class="pipeline-unit">{{ job.unitType }} · {{ job.mode }}</span>
            </div>
            <div class="pipeline-actions">
              <button v-if="job.status === 'staging_ready'" class="admin-primary-btn" @click="approve(job.id)">批准上线</button>
              <button v-if="job.status === 'staging_ready'" class="admin-danger-btn" @click="reject(job.id)">拒绝</button>
              <button v-if="job.previewToken" class="admin-secondary-btn" @click="preview(job)">预览</button>
            </div>
          </div>
          <div class="pipeline-meta">
            <span>{{ job.operatorName }}</span>
            <span>{{ job.createdAt ? job.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</span>
            <span v-if="job.scheduledAt">计划: {{ job.scheduledAt.replace('T', ' ').slice(0, 16) }}</span>
          </div>
          <div class="pipeline-status-row">
            <span class="pipeline-status-dot" :style="{ background: statusColor(job.status) }" />
            <span class="pipeline-status-label" :style="{ background: statusBg(job.status), color: statusColor(job.status) }">{{ publishStatusLabel(job.status) }}</span>
          </div>
          <div v-if="job.failureReason" class="pipeline-error">{{ job.failureReason }}</div>
        </div>
      </li>
      <li v-if="!filteredJobs.length" class="admin-empty-state">当前没有待人工关注的发布任务</li>
    </ul>
  </div>
</template>

<style scoped>
.publish-tasks-page .admin-stats-row {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

@media (max-width: 640px) {
  .publish-tasks-page .admin-stats-row {
    grid-template-columns: 1fr;
  }
}
</style>
