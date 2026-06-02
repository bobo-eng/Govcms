<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
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
  publishCheck,
  publishImpact,
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
    message.success(`发布任务 #${response.data.id} 执行完成`)
    await loadJobs()
    await openJobDetail(response.data)
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

const handleRetry = async (job: PublishJobItem) => {
  try {
    const response = await retryPublishJob(job.id)
    message.success(`已重试任务 #${response.data.id}`)
    await loadJobs()
    await openJobDetail(response.data)
    await loadSearchIndexStatus(response.data.siteId)
  } catch (error: any) {
    message.error(error.response?.data?.message || '重试失败')
  }
}

const handleRollback = async (job: PublishJobItem) => {
  const reason = window.prompt('请输入回滚原因', 'Manual rollback') || 'Manual rollback'
  try {
    const response = await rollbackPublishJob(job.id, { siteId: job.siteId, targetJobId: job.id, reason })
    message.success(`回滚任务 #${response.data.id} 已完成`)
    await loadJobs()
    await openJobDetail(response.data)
    await loadSearchIndexStatus(response.data.siteId)
  } catch (error: any) {
    message.error(error.response?.data?.message || '回滚失败')
  }
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

    <div class="admin-card publish-search-card">
      <div class="admin-card-header">
        <h3 class="admin-card-title">搜索索引状态</h3>
      </div>
      <div class="admin-toolbar-row">
        <button class="admin-secondary-btn" :disabled="rebuildingSearchIndex || !filters.siteId" @click="handleRebuildSiteIndex">{{ rebuildingSearchIndex ? '重建中...' : '重建当前站点索引' }}</button>
        <button class="admin-secondary-btn" :disabled="rebuildingSearchIndex || !canRebuildObjectIndex" @click="handleRebuildObjectIndex">{{ rebuildingSearchIndex ? '处理中...' : `重建${searchIndexObjectLabel}索引` }}</button>
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
        <div class="admin-card-header"><h3 class="admin-card-title">当前摘要</h3></div>
        <div v-if="checkResult" class="publish-summary-box">
          <div v-if="checkResult.warnings?.some(item => item.includes('整站'))" class="admin-chip admin-chip--warning">建议整站发布</div>
          <div :class="['admin-status-chip', checkResult.publishable ? 'admin-status-chip--published' : 'admin-status-chip--rejected']">{{ checkResult.publishable ? '可发布' : '不可发布' }}</div>
          <div class="admin-sub-text">影响项：{{ checkResult.impactCount }}</div>
          <ul class="summary-list">
            <li v-for="item in checkResult.reasons" :key="`r-${item}`">{{ item }}</li>
            <li v-for="item in checkResult.warnings" :key="`w-${item}`">{{ item }}</li>
          </ul>
        </div>
        <div v-else class="admin-empty-state">点击“发布前校验”后，在这里查看当前发布摘要。</div>
      </div>
    </div>

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
      <div v-else class="admin-empty-state">点击“影响范围计算”查看受影响页面集合。</div>
    </div>

    <div class="publish-history-grid">
      <div class="admin-card">
        <div class="admin-card-header"><h3 class="admin-card-title">历史任务</h3></div>
        <div class="admin-toolbar-row">
          <select v-model="jobFilters.siteId" class="admin-filter-select small-select"><option :value="undefined">全部站点</option><option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option></select>
          <select v-model="jobFilters.status" class="admin-filter-select small-select"><option value="">全部状态</option><option value="success">success</option><option value="failed">failed</option><option value="rollback_success">rollback_success</option><option value="rollback_failed">rollback_failed</option></select>
          <select v-model="jobFilters.mode" class="admin-filter-select small-select"><option value="">全部模式</option><option value="incremental">incremental</option><option value="full">full</option><option value="offline">offline</option><option value="rollback">rollback</option></select>
          <select v-model="jobFilters.unitType" class="admin-filter-select small-select"><option value="">全部对象</option><option value="content">content</option><option value="category">category</option><option value="template">template</option><option value="navigation">navigation</option><option value="topic">topic</option><option value="site">site</option></select>
          <button class="admin-secondary-btn" @click="loadJobs">刷新</button>
        </div>
        <table class="admin-data-table publish-jobs-table">
          <thead><tr><th>ID</th><th>单位</th><th>模式</th><th>状态</th><th>操作者</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="jobsLoading"><td colspan="7" class="admin-empty-cell">加载中...</td></tr>
            <tr v-else-if="!jobs.length"><td colspan="7" class="admin-empty-cell">暂无任务</td></tr>
            <tr v-for="job in jobs" :key="job.id" :class="{ 'row-active': selectedJob?.id === job.id }" @click="selectJob(job)">
              <td>#{{ job.id }}</td>
              <td>{{ job.unitType }} / {{ job.unitIds }}</td>
              <td>{{ job.mode }}</td>
              <td><span :class="['admin-status-chip', job.status.includes('success') ? 'admin-status-chip--published' : (job.status.includes('failed') ? 'admin-status-chip--rejected' : 'admin-status-chip--approved')]">{{ job.status }}</span></td>
              <td>{{ job.operatorName }}</td>
              <td>{{ job.createdAt ? job.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</td>
              <td><div class="job-actions"><button class="admin-link-action" @click.stop="openJobDetail(job)">详情</button><button v-if="job.status === 'failed'" class="admin-link-action warning-link" @click.stop="handleRetry(job)">重试</button><button v-if="job.status === 'success' || job.status === 'rollback_success'" class="admin-link-action danger-link" @click.stop="handleRollback(job)">回滚</button></div></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-card publish-summary-sidebar">
        <div class="admin-card-header"><h3 class="admin-card-title">任务摘要</h3></div>
        <div v-if="selectedJob" class="summary-stack">
          <div class="summary-item"><label>ID</label><strong>#{{ selectedJob.id }}</strong></div>
          <div class="summary-item"><label>对象</label><span>{{ selectedJob.unitType }} / {{ selectedJob.unitIds }}</span></div>
          <div class="summary-item"><label>模式</label><span>{{ selectedJob.mode }}</span></div>
          <div class="summary-item"><label>状态</label><span :class="['admin-status-chip', selectedJob.status.includes('success') ? 'admin-status-chip--published' : (selectedJob.status.includes('failed') ? 'admin-status-chip--rejected' : 'admin-status-chip--approved')]">{{ selectedJob.status }}</span></div>
          <div class="summary-item"><label>摘要</label><p>{{ selectedJob.resultSummary || selectedJob.failureReason || '暂无摘要' }}</p></div>
          <div v-if="searchIndexWarnings.length" class="warning-summary-box">
            <div class="admin-sub-text small-title">风险提示</div>
            <ul>
              <li v-for="item in searchIndexWarnings" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="admin-toolbar-row sidebar-actions">
            <button class="admin-secondary-btn" @click="openJobDetail(selectedJob)">查看详情</button>
            <button v-if="selectedJob.status === 'failed'" class="admin-secondary-btn" @click="handleRetry(selectedJob)">重试任务</button>
            <button v-if="selectedJob.status === 'success' || selectedJob.status === 'rollback_success'" class="admin-danger-btn" @click="handleRollback(selectedJob)">回滚任务</button>
          </div>
        </div>
        <div v-else class="admin-empty-state">选择左侧任务后，在这里查看任务摘要与快捷操作。</div>
      </div>
    </div>

    <a-modal v-model:open="detailOpen" title="发布任务详情" width="1100px" :footer="null" destroy-on-close>
      <div v-if="selectedJob" class="detail-grid">
        <div class="admin-card publish-detail-card">
          <div class="admin-card-title">任务概览</div>
          <div class="admin-sub-text">#{{ selectedJob.id }} · {{ selectedJob.unitType }} · {{ selectedJob.mode }} · {{ selectedJob.status }}</div>
          <div class="admin-sub-text">{{ selectedJob.resultSummary || selectedJob.failureReason || '暂无摘要' }}</div>
          <div class="footer-actions">
            <button v-if="selectedJob.status === 'failed'" class="admin-secondary-btn" @click="handleRetry(selectedJob)">重试任务</button>
            <button v-if="selectedJob.status === 'success' || selectedJob.status === 'rollback_success'" class="admin-danger-btn" @click="handleRollback(selectedJob)">回滚任务</button>
          </div>
        </div>
        <div class="admin-card publish-detail-card">
          <div class="admin-card-title">影响项</div>
          <div v-if="impacts.length" class="impact-list small">
            <div v-for="item in impacts" :key="`${item.action}-${item.path}`" class="impact-item"><div>{{ item.pageType }} · {{ item.action }}</div><div class="admin-sub-text">{{ item.path }}</div></div>
          </div>
          <div v-else class="admin-empty-state">暂无影响项</div>
        </div>
        <div class="admin-card publish-detail-card">
          <div class="admin-card-title">产物</div>
          <div v-if="artifacts.length" class="impact-list small">
            <div v-for="item in artifacts" :key="item.id" class="impact-item"><div>{{ item.artifactType }} · {{ item.outputPath }}</div><div class="admin-sub-text">{{ item.version || '-' }}</div></div>
          </div>
          <div v-else class="admin-empty-state">暂无产物</div>
        </div>
        <div class="admin-card publish-detail-card">
          <div class="admin-card-title">审计摘要</div>
          <div v-if="auditLogs.length" class="impact-list small">
            <div v-for="item in auditLogs" :key="item.id" class="impact-item"><div>{{ item.actionType }} · {{ item.result }}</div><div class="admin-sub-text">{{ item.summary || item.failureReason || '-' }}</div></div>
          </div>
          <div v-else class="admin-empty-state">暂无审计记录</div>
        </div>
        <div class="admin-card publish-detail-card">
          <div class="admin-card-title">索引告警</div>
          <div v-if="searchIndexWarnings.length" class="impact-list small">
            <div v-for="item in searchIndexWarnings" :key="item" class="impact-item warning-box">{{ item }}</div>
          </div>
          <div v-else class="admin-empty-state">暂无索引告警</div>
        </div>
        <div class="admin-card publish-detail-card">
          <div class="admin-card-title">回滚关系</div>
          <div v-if="rollbackRecords.length" class="impact-list small">
            <div v-for="item in rollbackRecords" :key="item.id" class="impact-item"><div>target #{{ item.targetJobId }} / rollback #{{ item.rollbackJobId }}</div><div class="admin-sub-text">{{ item.reason || '-' }}</div></div>
          </div>
          <div v-else class="admin-empty-state">暂无回滚关系</div>
        </div>
        <div class="admin-card publish-detail-card full-row">
          <div class="admin-card-title">执行日志</div>
          <pre class="log-box">{{ logs.join('\n') || '暂无日志' }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.publish-page .publish-stats-grid,
.publish-page .publish-keyword-grid,
.publish-page .publish-top-grid,
.publish-page .publish-history-grid,
.publish-page .detail-grid,
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
.publish-page .publish-history-grid,
.publish-page .detail-grid {
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
.publish-page .job-actions,
.publish-page .footer-actions,
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

.publish-page .publish-jobs-table tr {
  cursor: pointer;
}

.publish-page .publish-jobs-table tr.row-active td {
  background: #eff6ff;
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

.publish-page .link-btn {
  background: transparent;
  padding: 0;
  color: #2563eb;
  border: none;
  cursor: pointer;
}

.publish-page .warning-link { color: #b45309; }
.publish-page .danger-link { color: #dc2626; }
.publish-page .status-chip { display: inline-flex; padding: 4px 10px; border-radius: 999px; font-size: 12px; }
.publish-page .status-chip.approved { background: #dbeafe; color: #1d4ed8; }
.publish-page .status-chip.published { background: #dcfce7; color: #166534; }
.publish-page .status-chip.rejected { background: #fee2e2; color: #991b1b; }
.publish-page .panel-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 16px; }
.publish-page .panel-title { font-weight: 600; margin-bottom: 12px; }
.publish-page .sub-text,
.publish-page .small-title { color: #64748b; font-size: 12px; }
.publish-page .log-box { background: #0f172a; color: #e2e8f0; border-radius: 12px; padding: 12px; min-height: 220px; white-space: pre-wrap; }

@media (max-width: 1100px) {
  .publish-page .publish-top-grid,
  .publish-page .publish-history-grid,
  .publish-page .publish-keyword-grid,
  .publish-page .detail-grid,
  .publish-page .publish-form-grid,
  .publish-page .publish-stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>




