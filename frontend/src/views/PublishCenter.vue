<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { fetchArticles, type ArticleItem } from '../api/articles'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import { fetchCategories } from '../api/categories'
import { fetchNavigationItems } from '../api/navigation'
import { fetchTopics } from '../api/topics'
import { fetchTemplates } from '../api/templates'
import {
  fetchSearchIndexStatus,
  rebuildSearchIndexCategory,
  rebuildSearchIndexContent,
  rebuildSearchIndexSite,
  rebuildSearchIndexTopic,
  type SearchIndexStatusData
} from '../api/searchIndex'
import {
  createPublishJob,
  fetchPublishArtifacts,
  fetchPublishAudits,
  fetchPublishImpacts,
  fetchPublishJobs,
  fetchPublishLogs,
  fetchPublishRollbackRecords,
  PUBLISH_STATUS_ORDER,
  publishCheck,
  publishImpact,
  publishStatusLabel,
  publishStatusMeta,
  retryPublishJob,
  rollbackPublishJob,
  type AuditLogItem,
  type PublishArtifactItem,
  type PublishCheckResponseData,
  type PublishImpactItemData,
  type PublishImpactResponseData,
  type PublishJobItem,
  type PublishRequestPayload,
  type PublishRollbackRecordItem
} from '../api/publish'

type SiteOption = SiteOptionItem
interface OptionItem { value: number; label: string }

const route = useRoute()
const sites = ref<SiteOption[]>([])
const unitOptions = ref<OptionItem[]>([])
const selectedJob = ref<PublishJobItem | null>(null)
const impacts = ref<PublishImpactItemData[]>([])
const artifacts = ref<PublishArtifactItem[]>([])
const logs = ref<string[]>([])
const auditLogs = ref<AuditLogItem[]>([])
const rollbackRecords = ref<PublishRollbackRecordItem[]>([])
const detailOpen = ref(false)
const detailTab = ref('overview')
const checking = ref(false)
const impactLoading = ref(false)
const executing = ref(false)
const jobsLoading = ref(false)
const checkResult = ref<PublishCheckResponseData | null>(null)
const impactResult = ref<PublishImpactResponseData | null>(null)
const jobs = ref<PublishJobItem[]>([])
const searchIndexStatus = ref<SearchIndexStatusData | null>(null)
const searchIndexLoading = ref(false)
const rebuildingSearchIndex = ref(false)
const filters = ref({ siteId: undefined as number | undefined, unitType: 'content', mode: 'incremental', unitId: undefined as number | undefined, operatorComment: '' })
const jobFilters = ref({ siteId: undefined as number | undefined, status: '', mode: '', unitType: '' })
const activeStatusFilter = ref('')

const contentStatus = computed(() => filters.value.mode === 'offline' ? 'published' : 'approved')
const canExecute = computed(() => Boolean(filters.value.siteId && (filters.value.unitType === 'site' || filters.value.unitId)))
const canRebuildObjectIndex = computed(() => Boolean(filters.value.unitId && ['content', 'topic', 'category'].includes(filters.value.unitType)))
const searchIndexObjectLabel = computed(() => ({ content: '当前内容', topic: '当前专题', category: '当前栏目' }[filters.value.unitType] || '当前对象'))
const searchIndexWarnings = computed(() => {
  const items = impacts.value
    .filter(item => item.pageType === 'search-index')
    .map(item => item.summary || item.path)
    .filter((item): item is string => Boolean(item))
  if (selectedJob.value?.failureReason && selectedJob.value.failureReason.includes('索引')) {
    items.unshift(selectedJob.value.failureReason)
  }
  return Array.from(new Set(items))
})

const pendingCount = computed(() => jobs.value.filter(j => j.status === 'staging_ready').length)
const todaySuccessCount = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return jobs.value.filter(j => (j.status === 'published' || j.status === 'success') && j.finishedAt?.startsWith(today)).length
})
const failedCount = computed(() => jobs.value.filter(j => j.status === 'failed' || j.status === 'rollback_failed').length)
const lastPublishAt = computed(() => {
  const latest = jobs.value
    .filter(j => j.status === 'published' || j.status === 'success')
    .sort((a, b) => (b.finishedAt || '').localeCompare(a.finishedAt || ''))[0]
  return latest?.finishedAt ? latest.finishedAt.replace('T', ' ').slice(0, 16) : '暂无'
})

const filteredJobs = computed(() => {
  let list = jobs.value
  if (jobFilters.value.siteId) list = list.filter(j => j.siteId === jobFilters.value.siteId)
  if (jobFilters.value.status) list = list.filter(j => j.status === jobFilters.value.status)
  if (jobFilters.value.mode) list = list.filter(j => j.mode === jobFilters.value.mode)
  if (jobFilters.value.unitType) list = list.filter(j => j.unitType === jobFilters.value.unitType)
  if (activeStatusFilter.value) {
    if (activeStatusFilter.value === 'pending') list = list.filter(j => j.status === 'staging_ready')
    else if (activeStatusFilter.value === 'failed') list = list.filter(j => j.status === 'failed' || j.status === 'rollback_failed')
  }
  return list.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
})

const stateMachineSteps = computed(() => {
  const status = selectedJob.value?.status || ''
  const exceptionStatuses = ['failed', 'rejected', 'rollback_failed']
  const isException = exceptionStatuses.includes(status)
  const steps: { key: string; label: string; passed: boolean; current: boolean }[] = PUBLISH_STATUS_ORDER.map((s, i) => {
    const meta = publishStatusMeta[s]
    const index = PUBLISH_STATUS_ORDER.indexOf(status as typeof PUBLISH_STATUS_ORDER[number])
    const passed = index > -1 && i < index
    const current = s === status
    return { key: s, label: meta?.label || s, passed, current }
  })
  if (isException) {
    steps.push({ key: status, label: publishStatusMeta[status]?.label || status, passed: false, current: true })
  }
  return steps
})

const statusColor = (status: string) => publishStatusMeta[status]?.color || '#94a3b8'
const statusBg = (status: string) => publishStatusMeta[status]?.bg || '#f1f5f9'

const isRunning = (status: string) => status === 'staging_rendering' || status === 'production_rendering'

const canRetry = (job: PublishJobItem) => job.status === 'failed'
const canRollback = (job: PublishJobItem) => job.status === 'published' || job.status === 'success' || job.status === 'rollback_success'

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
  if (!filters.value.siteId && sites.value.length) {
    filters.value.siteId = sites.value[0].id
  }
  if (!jobFilters.value.siteId && sites.value.length === 1) {
    jobFilters.value.siteId = sites.value[0].id
  }
}

const loadUnitOptions = async () => {
  unitOptions.value = []
  if (!filters.value.siteId) return
  try {
    if (filters.value.unitType === 'content') {
      const response = await fetchArticles({ page: 0, size: 100, siteId: filters.value.siteId, status: contentStatus.value })
      unitOptions.value = (response.data.content || []).map((item: ArticleItem) => ({ value: item.id, label: item.title }))
    } else if (filters.value.unitType === 'category') {
      const response = await fetchCategories({ siteId: filters.value.siteId })
      unitOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
    } else if (filters.value.unitType === 'template') {
      const response = await fetchTemplates({ siteId: filters.value.siteId })
      unitOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
    } else if (filters.value.unitType === 'navigation') {
      const response = await fetchNavigationItems({ siteId: filters.value.siteId })
      unitOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
    } else if (filters.value.unitType === 'topic') {
      const response = await fetchTopics({ siteId: filters.value.siteId, status: 'active' })
      unitOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
    } else if (filters.value.unitType === 'site') {
      const site = sites.value.find(item => item.id === filters.value.siteId)
      unitOptions.value = site ? [{ value: site.id, label: site.name }] : []
      filters.value.unitId = filters.value.siteId
      filters.value.mode = 'full'
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载发布对象失败')
  }
}

const loadSearchIndexStatus = async (siteId?: number) => {
  if (!siteId) {
    searchIndexStatus.value = null
    return
  }
  searchIndexLoading.value = true
  try {
    const response = await fetchSearchIndexStatus(siteId)
    searchIndexStatus.value = response.data
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载搜索索引状态失败')
  } finally {
    searchIndexLoading.value = false
  }
}

const handleRebuildSiteIndex = async () => {
  if (!filters.value.siteId) {
    message.warning('请选择站点后再重建索引')
    return
  }
  rebuildingSearchIndex.value = true
  try {
    const response = await rebuildSearchIndexSite(filters.value.siteId)
    searchIndexStatus.value = response.data
    message.success('站点搜索索引已重建')
  } catch (error: any) {
    message.error(error.response?.data?.message || '站点搜索索引重建失败')
  } finally {
    rebuildingSearchIndex.value = false
  }
}

const handleRebuildObjectIndex = async () => {
  if (!filters.value.unitId || !['content', 'topic', 'category'].includes(filters.value.unitType)) {
    message.warning('当前发布单位不支持单对象索引重建')
    return
  }
  rebuildingSearchIndex.value = true
  try {
    let response
    if (filters.value.unitType === 'content') {
      response = await rebuildSearchIndexContent(filters.value.unitId)
    } else if (filters.value.unitType === 'topic') {
      response = await rebuildSearchIndexTopic(filters.value.unitId)
    } else {
      response = await rebuildSearchIndexCategory(filters.value.unitId)
    }
    searchIndexStatus.value = response.data
    message.success(`${searchIndexObjectLabel.value}搜索索引已重建`)
  } catch (error: any) {
    message.error(error.response?.data?.message || '对象搜索索引重建失败')
  } finally {
    rebuildingSearchIndex.value = false
  }
}

const loadJobs = async () => {
  jobsLoading.value = true
  try {
    const response = await fetchPublishJobs({
      siteId: jobFilters.value.siteId,
      status: jobFilters.value.status || undefined,
      mode: jobFilters.value.mode || undefined,
      unitType: jobFilters.value.unitType || undefined
    })
    jobs.value = response.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取发布任务失败')
  } finally {
    jobsLoading.value = false
  }
}

const currentPayload = (): PublishRequestPayload => ({
  siteId: Number(filters.value.siteId),
  unitType: filters.value.unitType,
  unitIds: filters.value.unitType === 'site' ? [Number(filters.value.siteId)] : [Number(filters.value.unitId)],
  mode: filters.value.unitType === 'site' ? 'full' : filters.value.mode,
  operatorComment: filters.value.operatorComment || null
})

const doCheck = async () => {
  if (!canExecute.value) {
    message.warning('请选择站点和发布对象')
    return
  }
  checking.value = true
  try {
    const response = await publishCheck(currentPayload())
    checkResult.value = response.data
  } catch (error: any) {
    message.error(error.response?.data?.message || '发布前校验失败')
  } finally {
    checking.value = false
  }
}

const doImpact = async () => {
  if (!canExecute.value) {
    message.warning('请选择站点和发布对象')
    return
  }
  impactLoading.value = true
  try {
    const response = await publishImpact(currentPayload())
    impactResult.value = response.data
  } catch (error: any) {
    message.error(error.response?.data?.message || '影响范围计算失败')
  } finally {
    impactLoading.value = false
  }
}

const doExecute = async () => {
  if (!canExecute.value) {
    message.warning('请选择站点和发布对象')
    return
  }
  executing.value = true
  try {
    const response = await createPublishJob(currentPayload())
    message.success(`发布任务 #${response.data.id} 已创建`)
    await loadJobs()
    await loadSearchIndexStatus(response.data.siteId)
  } catch (error: any) {
    message.error(error.response?.data?.message || '执行发布失败')
  } finally {
    executing.value = false
  }
}

const selectJob = (job: PublishJobItem) => {
  selectedJob.value = job
}

const openJobDetail = async (job: PublishJobItem) => {
  selectedJob.value = job
  detailTab.value = 'overview'
  detailOpen.value = true
  try {
    const [impactResponse, artifactResponse, logResponse, auditResponse, rollbackResponse] = await Promise.all([
      fetchPublishImpacts(job.id),
      fetchPublishArtifacts(job.id),
      fetchPublishLogs(job.id),
      fetchPublishAudits(job.id),
      fetchPublishRollbackRecords(job.id)
    ])
    impacts.value = impactResponse.data || []
    artifacts.value = artifactResponse.data || []
    logs.value = logResponse.data || []
    auditLogs.value = auditResponse.data || []
    rollbackRecords.value = rollbackResponse.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载任务详情失败')
  }
}

const handleRetry = (job: PublishJobItem) => {
  Modal.confirm({
    title: '重试任务',
    content: `确定重试任务 #${job.id} 吗？`,
    okText: '重试',
    cancelText: '取消',
    async onOk() {
      try {
        const response = await retryPublishJob(job.id)
        message.success(`已重试任务 #${response.data.id}`)
        await loadJobs()
      } catch (error: any) {
        message.error(error.response?.data?.message || '重试失败')
      }
    }
  })
}

const handleRollback = (job: PublishJobItem) => {
  Modal.confirm({
    title: '回滚任务',
    content: '确定回滚该任务吗？回滚将恢复到上一个可用版本。',
    okText: '确认回滚',
    cancelText: '取消',
    async onOk() {
      try {
        const response = await rollbackPublishJob(job.id, { siteId: job.siteId, targetJobId: job.id, reason: 'Manual rollback' })
        message.success(`回滚任务 #${response.data.id} 已完成`)
        await loadJobs()
        await loadSearchIndexStatus(job.siteId)
      } catch (error: any) {
        message.error(error.response?.data?.message || '回滚失败')
      }
    }
  })
}

watch(() => [filters.value.siteId, filters.value.unitType, filters.value.mode], async () => {
  filters.value.unitId = undefined
  checkResult.value = null
  impactResult.value = null
  await loadUnitOptions()
  await loadSearchIndexStatus(filters.value.siteId)
})

onMounted(async () => {
  await loadSites()
  const querySiteId = route.query.siteId ? Number(route.query.siteId) : undefined
  const queryUnitType = typeof route.query.unitType === 'string' ? route.query.unitType : undefined
  const queryUnitId = route.query.unitId ? Number(route.query.unitId) : undefined
  const queryMode = typeof route.query.mode === 'string' ? route.query.mode : undefined
  if (querySiteId) filters.value.siteId = querySiteId
  if (queryUnitType) filters.value.unitType = queryUnitType
  if (queryMode) filters.value.mode = queryMode
  await loadUnitOptions()
  if (queryUnitId) filters.value.unitId = queryUnitId
  jobFilters.value.siteId = querySiteId
  await loadJobs()
  await loadSearchIndexStatus(filters.value.siteId)
})
</script>

<template>
  <div class="admin-page publish-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">发布中心</h1>
        <p class="admin-page-desc">统一执行正式发布、影响范围预计算、任务追踪、重试与回滚。</p>
      </div>
    </div>

    <!-- Hero Stats -->
    <div class="admin-stats-row">
      <div class="admin-stat-card" :class="{ active: activeStatusFilter === 'pending' }" @click="activeStatusFilter = activeStatusFilter === 'pending' ? '' : 'pending'">
        <div class="admin-stat-value" style="color: #d97706;">{{ pendingCount }}</div>
        <div class="admin-stat-label">待处理</div>
      </div>
      <div class="admin-stat-card">
        <div class="admin-stat-value" style="color: #15803d;">{{ todaySuccessCount }}</div>
        <div class="admin-stat-label">今日成功</div>
      </div>
      <div class="admin-stat-card" :class="{ active: activeStatusFilter === 'failed' }" @click="activeStatusFilter = activeStatusFilter === 'failed' ? '' : 'failed'">
        <div class="admin-stat-value" style="color: #dc2626;">{{ failedCount }}</div>
        <div class="admin-stat-label">失败 / 需关注</div>
      </div>
      <div class="admin-stat-card">
        <div class="admin-stat-value" style="font-size: 18px; line-height: 28px; margin-top: 10px;">{{ lastPublishAt }}</div>
        <div class="admin-stat-label">最近发布</div>
      </div>
    </div>

    <!-- Search Index -->
    <div class="admin-card publish-search-card">
      <div class="admin-card-header">
        <h3 class="admin-card-title">搜索索引状态</h3>
        <div class="admin-toolbar-row">
          <button class="admin-secondary-btn" :disabled="rebuildingSearchIndex || !filters.siteId" @click="handleRebuildSiteIndex">{{ rebuildingSearchIndex ? '重建中...' : '重建当前站点索引' }}</button>
          <button class="admin-secondary-btn" :disabled="rebuildingSearchIndex || !canRebuildObjectIndex" @click="handleRebuildObjectIndex">{{ rebuildingSearchIndex ? '处理中...' : `重建${searchIndexObjectLabel}索引` }}</button>
        </div>
      </div>
      <div v-if="searchIndexLoading" class="admin-empty-state">正在加载搜索索引状态...</div>
      <div v-else-if="searchIndexStatus">
        <div class="publish-stats-grid">
          <div class="publish-metric-card"><div class="admin-sub-text">索引总量</div><div class="publish-metric-value">{{ searchIndexStatus.totalEntries }}</div></div>
          <div class="publish-metric-card"><div class="admin-sub-text">最近重建</div><div>{{ searchIndexStatus.lastRebuildAt ? searchIndexStatus.lastRebuildAt.replace('T', ' ').slice(0, 16) : '暂无' }}</div><div class="admin-sub-text">{{ searchIndexStatus.lastRebuildSummary || '等待首次重建' }}</div></div>
          <div class="publish-metric-card"><div class="admin-sub-text">最近失败</div><div>{{ searchIndexStatus.lastFailureReason || '暂无失败记录' }}</div></div>
        </div>
        <div class="publish-keyword-grid">
          <div class="admin-card inner-card">
            <div class="admin-card-header"><h3 class="admin-card-title">热门关键词</h3></div>
            <div v-if="searchIndexStatus.hotKeywords.length" class="tag-list"><div v-for="item in searchIndexStatus.hotKeywords" :key="`hot-${item.keyword}`" class="tag-chip">{{ item.keyword }} · {{ item.count }}</div></div>
            <div v-else class="admin-empty-state">暂无热门关键词</div>
          </div>
          <div class="admin-card inner-card">
            <div class="admin-card-header"><h3 class="admin-card-title">零结果关键词</h3></div>
            <div v-if="searchIndexStatus.zeroResultKeywords.length" class="tag-list"><div v-for="item in searchIndexStatus.zeroResultKeywords" :key="`zero-${item.keyword}`" class="tag-chip warning">{{ item.keyword }} · {{ item.count }}</div></div>
            <div v-else class="admin-empty-state">暂无零结果关键词</div>
          </div>
        </div>
      </div>
      <div v-else class="admin-empty-state">请选择站点查看搜索索引状态。</div>
    </div>

    <!-- Publish Entry + Summary -->
    <div class="publish-top-grid">
      <div class="admin-card">
        <div class="admin-card-header"><h3 class="admin-card-title">发布入口</h3></div>
        <div class="publish-form-grid">
          <label><span>站点</span><select v-model="filters.siteId" class="admin-form-select"><option :value="undefined">请选择站点</option><option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option></select></label>
          <label><span>发布单位</span><select v-model="filters.unitType" class="admin-form-select"><option value="content">内容</option><option value="category">栏目</option><option value="template">模板</option><option value="navigation">导航</option><option value="topic">专题</option><option value="site">站点</option></select></label>
          <label v-if="filters.unitType !== 'site'"><span>发布模式</span><select v-model="filters.mode" class="admin-form-select"><option value="incremental">增量发布</option><option v-if="filters.unitType === 'content'" value="offline">下线发布</option></select></label>
          <label v-if="filters.unitType !== 'site'" class="full-row"><span>发布对象</span><select v-model="filters.unitId" class="admin-form-select"><option :value="undefined">请选择对象</option><option v-for="item in unitOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
          <label class="full-row"><span>备注</span><input v-model="filters.operatorComment" class="admin-form-input" placeholder="可选备注" /></label>
        </div>
        <div class="admin-toolbar-row publish-actions">
          <button class="admin-secondary-btn" :disabled="checking" @click="doCheck">{{ checking ? '校验中...' : '发布前校验' }}</button>
          <button class="admin-secondary-btn" :disabled="impactLoading" @click="doImpact">{{ impactLoading ? '计算中...' : '影响范围计算' }}</button>
          <button class="admin-primary-btn" :disabled="executing || !canExecute" @click="doExecute">{{ executing ? '执行中...' : '创建并执行发布' }}</button>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-header"><h3 class="admin-card-title">实时摘要</h3></div>
        <div v-if="checkResult" class="publish-summary-box">
          <div v-if="checkResult.warnings?.some(item => item.includes('整站'))" class="admin-chip admin-chip--warning">建议整站发布</div>
          <div :class="['admin-status-chip', checkResult.publishable ? 'admin-status-chip--published' : 'admin-status-chip--rejected']">{{ checkResult.publishable ? '可发布' : '不可发布' }}</div>
          <div class="admin-sub-text">影响项：{{ checkResult.impactCount }}</div>
          <ul class="summary-list">
            <li v-for="item in checkResult.reasons" :key="`r-${item}`">{{ item }}</li>
            <li v-for="item in checkResult.warnings" :key="`w-${item}`">{{ item }}</li>
          </ul>
        </div>
        <div v-else class="admin-empty-state">点击"发布前校验"后，在这里查看当前发布摘要。</div>
      </div>
    </div>

    <!-- Impact Scope -->
    <div class="admin-card">
      <div class="admin-card-header"><h3 class="admin-card-title">影响范围</h3></div>
      <div v-if="impactResult" class="impact-list">
        <div v-if="impactResult.warnings?.length" class="result-box">
          <div class="admin-sub-text small-title">告警</div>
          <ul><li v-for="item in impactResult.warnings" :key="item">{{ item }}</li></ul>
        </div>
        <div class="admin-sub-text">共 {{ impactResult.totalItems }} 项</div>
        <div v-for="item in impactResult.items" :key="`${item.action}-${item.path}`" class="impact-item">
          <div>{{ item.pageType }} · {{ item.action }}</div>
          <div class="admin-sub-text">{{ item.path }}</div>
          <div class="admin-sub-text">{{ item.summary || '-' }}</div>
        </div>
      </div>
      <div v-else class="admin-empty-state">点击"影响范围计算"查看受影响页面集合。</div>
    </div>

    <!-- Pipeline + Sidebar -->
    <div class="publish-history-grid">
      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">发布流水线</h3>
          <div class="admin-toolbar-row">
            <select v-model="jobFilters.siteId" class="admin-filter-select small-select" @change="loadJobs"><option :value="undefined">全部站点</option><option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option></select>
            <select v-model="jobFilters.status" class="admin-filter-select small-select" @change="loadJobs"><option value="">全部状态</option><option value="published">已发布</option><option value="success">已成功</option><option value="failed">失败</option><option value="rollback_success">回滚成功</option><option value="rollback_failed">回滚失败</option></select>
            <select v-model="jobFilters.mode" class="admin-filter-select small-select" @change="loadJobs"><option value="">全部模式</option><option value="incremental">增量</option><option value="full">全量</option><option value="offline">下线</option></select>
            <select v-model="jobFilters.unitType" class="admin-filter-select small-select" @change="loadJobs"><option value="">全部对象</option><option value="content">内容</option><option value="category">栏目</option><option value="template">模板</option><option value="navigation">导航</option><option value="topic">专题</option><option value="site">站点</option></select>
            <button class="admin-secondary-btn" @click="loadJobs">刷新</button>
          </div>
        </div>
        <div v-if="jobsLoading" class="admin-empty-state">加载中...</div>
        <ul v-else class="publish-pipeline-list">
          <li
            v-for="job in filteredJobs"
            :key="job.id"
            class="publish-pipeline-item"
            :class="{ active: selectedJob?.id === job.id }"
            :style="{ '--status-color': statusColor(job.status) }"
            @click="selectJob(job)"
          >
            <div class="pipeline-status-bar" />
            <div class="pipeline-body">
              <div class="pipeline-header">
                <div class="pipeline-title">
                  <span class="pipeline-id">#{{ job.id }}</span>
                  <span class="pipeline-unit">{{ job.unitType }} · {{ job.mode }}</span>
                </div>
                <div class="pipeline-actions">
                  <button class="admin-link-action" @click.stop="openJobDetail(job)">详情</button>
                  <button v-if="canRetry(job)" class="admin-link-action" style="color: #d97706;" @click.stop="handleRetry(job)">重试</button>
                  <button v-if="canRollback(job)" class="admin-link-action" style="color: #dc2626;" @click.stop="handleRollback(job)">回滚</button>
                </div>
              </div>
              <div class="pipeline-meta">
                <span>{{ job.operatorName }}</span>
                <span>{{ job.createdAt ? job.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</span>
              </div>
              <div class="pipeline-status-row">
                <span class="pipeline-status-dot" :class="{ pulse: isRunning(job.status) }" :style="{ background: statusColor(job.status) }" />
                <span class="pipeline-status-label" :style="{ background: statusBg(job.status), color: statusColor(job.status) }">{{ publishStatusLabel(job.status) }}</span>
              </div>
              <div v-if="job.failureReason" class="pipeline-error">{{ job.failureReason }}</div>
            </div>
          </li>
          <li v-if="!filteredJobs.length" class="admin-empty-state">暂无发布任务</li>
        </ul>
      </div>

      <div class="admin-card publish-summary-sidebar">
        <div class="admin-card-header"><h3 class="admin-card-title">任务摘要</h3></div>
        <div v-if="selectedJob" class="summary-stack">
          <div class="summary-item"><label>ID</label><strong>#{{ selectedJob.id }}</strong></div>
          <div class="summary-item"><label>对象</label><span>{{ selectedJob.unitType }} / {{ selectedJob.unitIds }}</span></div>
          <div class="summary-item"><label>模式</label><span>{{ selectedJob.mode }}</span></div>
          <div class="summary-item"><label>状态</label><span class="pipeline-status-label" :style="{ background: statusBg(selectedJob.status), color: statusColor(selectedJob.status) }">{{ publishStatusLabel(selectedJob.status) }}</span></div>
          <div class="summary-item"><label>操作者</label><span>{{ selectedJob.operatorName }}</span></div>
          <div class="summary-item"><label>时间</label><span>{{ selectedJob.createdAt ? selectedJob.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</span></div>
          <div class="summary-item"><label>摘要</label><p>{{ selectedJob.resultSummary || selectedJob.failureReason || '暂无摘要' }}</p></div>
          <div v-if="searchIndexWarnings.length" class="warning-summary-box">
            <div class="admin-sub-text small-title">风险提示</div>
            <ul><li v-for="item in searchIndexWarnings" :key="item">{{ item }}</li></ul>
          </div>
          <div class="admin-toolbar-row sidebar-actions">
            <button class="admin-secondary-btn" @click="openJobDetail(selectedJob)">查看详情</button>
            <button v-if="canRetry(selectedJob)" class="admin-secondary-btn" @click="handleRetry(selectedJob)">重试任务</button>
            <button v-if="canRollback(selectedJob)" class="admin-danger-btn" @click="handleRollback(selectedJob)">回滚任务</button>
          </div>
        </div>
        <div v-else class="admin-empty-state">选择左侧任务后，在这里查看任务摘要与快捷操作。</div>
      </div>
    </div>

    <!-- Detail Modal -->
    <a-modal v-model:open="detailOpen" title="发布任务详情" width="1000px" :footer="null" destroy-on-close>
      <div v-if="selectedJob" class="detail-modal">
        <!-- State Machine Bar -->
        <div class="state-machine-bar">
          <template v-for="(step, index) in stateMachineSteps" :key="step.key">
            <div class="state-step" :class="{ passed: step.passed, current: step.current }">
              <span class="state-dot" :style="{ background: step.current ? '#2563eb' : (step.passed ? '#22c55e' : '#cbd5e1') }" />
              <span class="state-label">{{ step.label }}</span>
            </div>
            <span v-if="index < stateMachineSteps.length - 1" style="color: #cbd5e1;">→</span>
          </template>
        </div>

        <a-tabs v-model:activeKey="detailTab" class="detail-tabs">
          <a-tab-pane key="overview" tab="概览">
            <div class="admin-detail-grid">
              <div class="admin-detail-card">
                <div class="admin-card-title">任务信息</div>
                <div class="summary-stack" style="margin-top: 12px;">
                  <div class="summary-item"><label>ID</label><strong>#{{ selectedJob.id }}</strong></div>
                  <div class="summary-item"><label>对象</label><span>{{ selectedJob.unitType }} / {{ selectedJob.unitIds }}</span></div>
                  <div class="summary-item"><label>模式</label><span>{{ selectedJob.mode }}</span></div>
                  <div class="summary-item"><label>状态</label><span class="pipeline-status-label" :style="{ background: statusBg(selectedJob.status), color: statusColor(selectedJob.status) }">{{ publishStatusLabel(selectedJob.status) }}</span></div>
                  <div class="summary-item"><label>操作者</label><span>{{ selectedJob.operatorName }}</span></div>
                  <div class="summary-item"><label>环境</label><span>{{ selectedJob.environment || 'production' }}</span></div>
                  <div class="summary-item"><label>创建时间</label><span>{{ selectedJob.createdAt ? selectedJob.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</span></div>
                  <div class="summary-item"><label>完成时间</label><span>{{ selectedJob.finishedAt ? selectedJob.finishedAt.replace('T', ' ').slice(0, 16) : '-' }}</span></div>
                </div>
              </div>
              <div class="admin-detail-card">
                <div class="admin-card-title">操作</div>
                <div class="admin-content-box" style="display: flex; flex-direction: column; gap: 12px; justify-content: center;">
                  <div class="admin-sub-text">{{ selectedJob.resultSummary || selectedJob.failureReason || '暂无摘要' }}</div>
                  <div class="admin-toolbar-row" style="justify-content: center;">
                    <button v-if="canRetry(selectedJob)" class="admin-secondary-btn" @click="handleRetry(selectedJob)">重试任务</button>
                    <button v-if="canRollback(selectedJob)" class="admin-danger-btn" @click="handleRollback(selectedJob)">回滚任务</button>
                  </div>
                </div>
              </div>
            </div>
          </a-tab-pane>

          <a-tab-pane key="impacts" tab="影响项">
            <div v-if="impacts.length" class="impact-list">
              <div v-for="item in impacts" :key="`${item.action}-${item.path}`" class="impact-item">
                <div>{{ item.pageType }} · {{ item.action }}</div>
                <div class="admin-sub-text">{{ item.path }}</div>
                <div class="admin-sub-text">{{ item.summary || '-' }}</div>
              </div>
            </div>
            <div v-else class="admin-empty-state">暂无影响项</div>
          </a-tab-pane>

          <a-tab-pane key="artifacts" tab="产物">
            <div v-if="artifacts.length" class="impact-list">
              <div v-for="item in artifacts" :key="item.id" class="impact-item">
                <div>{{ item.artifactType }} · {{ item.outputPath }}</div>
                <div class="admin-sub-text">版本: {{ item.version || '-' }} | 校验和: {{ item.checksum || '-' }}</div>
              </div>
            </div>
            <div v-else class="admin-empty-state">暂无产物</div>
          </a-tab-pane>

          <a-tab-pane key="logs" tab="日志">
            <pre class="log-box">{{ logs.join('\n') || '暂无日志' }}</pre>
          </a-tab-pane>

          <a-tab-pane key="audits" tab="审计">
            <div v-if="auditLogs.length" class="admin-history-list">
              <div v-for="item in auditLogs" :key="item.id" class="admin-history-item">
                <div><strong>{{ item.actionType }}</strong> · {{ item.result }}</div>
                <div class="admin-sub-text">{{ item.operatorName }} · {{ item.createdAt ? item.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</div>
                <div class="admin-sub-text">{{ item.summary || item.failureReason || '-' }}</div>
              </div>
            </div>
            <div v-else class="admin-empty-state">暂无审计记录</div>
          </a-tab-pane>

          <a-tab-pane key="rollback" tab="回滚">
            <div v-if="rollbackRecords.length" class="admin-history-list">
              <div v-for="item in rollbackRecords" :key="item.id" class="admin-history-item">
                <div>目标任务 #{{ item.targetJobId }} → 回滚任务 #{{ item.rollbackJobId }}</div>
                <div class="admin-sub-text">操作者: {{ item.operatorName }}</div>
                <div class="admin-sub-text">原因: {{ item.reason || '-' }}</div>
                <div class="admin-sub-text">时间: {{ item.createdAt ? item.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</div>
              </div>
            </div>
            <div v-else class="admin-empty-state">暂无回滚记录</div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.publish-page .publish-stats-grid,
.publish-page .publish-keyword-grid,
.publish-page .publish-top-grid,
.publish-page .publish-history-grid,
.publish-page .publish-form-grid {
  display: grid;
  gap: 16px;
}

.publish-page .publish-stats-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 12px;
}

.publish-page .publish-metric-card,
.publish-page .publish-summary-box,
.publish-page .result-box,
.publish-page .impact-item,
.publish-page .empty-box {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
}

.publish-page .publish-metric-value {
  font-size: 28px;
  font-weight: 700;
  margin-top: 6px;
}

.publish-page .publish-keyword-grid,
.publish-page .publish-top-grid,
.publish-page .publish-history-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.publish-page .publish-form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.publish-page .publish-form-grid label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.publish-page .full-row {
  grid-column: 1 / -1;
}

.publish-page .publish-actions,
.publish-page .sidebar-actions,
.publish-page .tag-list,
.publish-page .impact-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.publish-page .impact-list {
  flex-direction: column;
}

.publish-page .tag-chip {
  background: #e2e8f0;
  color: #0f172a;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
}

.publish-page .tag-chip.warning,
.publish-page .warning-summary-box,
.publish-page .warning-box {
  background: #fffbeb;
  border: 1px solid #fbbf24;
  color: #92400e;
}

.publish-page .small-select {
  min-width: 160px;
}

.publish-page .summary-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.publish-page .summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.publish-page .summary-item label {
  font-size: 13px;
  line-height: 20px;
  color: #475569;
}

.publish-page .summary-item p,
.publish-page .summary-item span,
.publish-page .summary-item strong {
  margin: 0;
  color: #0f172a;
  line-height: 22px;
}

.publish-page .summary-list {
  margin: 8px 0 0;
  padding-left: 18px;
}

@media (max-width: 1100px) {
  .publish-page .publish-top-grid,
  .publish-page .publish-history-grid,
  .publish-page .publish-keyword-grid,
  .publish-page .publish-form-grid,
  .publish-page .publish-stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
