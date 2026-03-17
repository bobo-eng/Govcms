<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { fetchArticles, type ArticleItem } from '../api/articles'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import { fetchCategories } from '../api/categories'
import { fetchTemplates } from '../api/templates'
import {
  createPublishJob,
  fetchPublishArtifacts,
  fetchPublishImpacts,
  fetchPublishJobs,
  fetchPublishLogs,
  publishCheck,
  publishImpact,
  retryPublishJob,
  rollbackPublishJob,
  type PublishArtifactItem,
  type PublishCheckResponseData,
  type PublishImpactItemData,
  type PublishImpactResponseData,
  type PublishJobItem,
  type PublishRequestPayload
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
const detailOpen = ref(false)
const checking = ref(false)
const impactLoading = ref(false)
const executing = ref(false)
const jobsLoading = ref(false)
const checkResult = ref<PublishCheckResponseData | null>(null)
const impactResult = ref<PublishImpactResponseData | null>(null)
const jobs = ref<PublishJobItem[]>([])
const filters = ref({ siteId: undefined as number | undefined, unitType: 'content', mode: 'incremental', unitId: undefined as number | undefined, operatorComment: '' })
const jobFilters = ref({ siteId: undefined as number | undefined, status: '', mode: '' })

const contentStatus = computed(() => filters.value.mode === 'offline' ? 'published' : 'approved')
const canExecute = computed(() => Boolean(filters.value.siteId && (filters.value.unitType === 'site' || filters.value.unitId)))

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
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

const loadJobs = async () => {
  jobsLoading.value = true
  try {
    const response = await fetchPublishJobs({
      siteId: jobFilters.value.siteId,
      status: jobFilters.value.status || undefined,
      mode: jobFilters.value.mode || undefined
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
  } catch (error: any) {
    message.error(error.response?.data?.message || '执行发布失败')
  } finally {
    executing.value = false
  }
}

const openJobDetail = async (job: PublishJobItem) => {
  selectedJob.value = job
  detailOpen.value = true
  try {
    const [impactResponse, artifactResponse, logResponse] = await Promise.all([
      fetchPublishImpacts(job.id),
      fetchPublishArtifacts(job.id),
      fetchPublishLogs(job.id)
    ])
    impacts.value = impactResponse.data || []
    artifacts.value = artifactResponse.data || []
    logs.value = logResponse.data || []
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
  } catch (error: any) {
    message.error(error.response?.data?.message || '回滚失败')
  }
}

watch(() => [filters.value.siteId, filters.value.unitType, filters.value.mode], async () => {
  filters.value.unitId = undefined
  checkResult.value = null
  impactResult.value = null
  await loadUnitOptions()
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
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2>发布中心</h2>
        <p>统一执行正式发布、影响范围预计算、任务追踪、重试与回滚。</p>
      </div>
    </div>

    <div class="workspace-grid">
      <div class="panel-card">
        <div class="panel-title">发布入口</div>
        <div class="form-grid">
          <label>
            <span>站点</span>
            <select v-model="filters.siteId" class="input">
              <option :value="undefined">请选择站点</option>
              <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
            </select>
          </label>
          <label>
            <span>发布单位</span>
            <select v-model="filters.unitType" class="input">
              <option value="content">内容</option>
              <option value="category">栏目</option>
              <option value="template">模板</option>
              <option value="site">站点</option>
            </select>
          </label>
          <label v-if="filters.unitType !== 'site'">
            <span>发布模式</span>
            <select v-model="filters.mode" class="input">
              <option value="incremental">增量发布</option>
              <option v-if="filters.unitType === 'content'" value="offline">下线发布</option>
            </select>
          </label>
          <label v-if="filters.unitType !== 'site'" class="full-row">
            <span>发布对象</span>
            <select v-model="filters.unitId" class="input">
              <option :value="undefined">请选择对象</option>
              <option v-for="item in unitOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="full-row">
            <span>备注</span>
            <input v-model="filters.operatorComment" class="input" placeholder="可选备注" />
          </label>
        </div>
        <div class="actions top-actions">
          <button class="secondary-btn" :disabled="checking" @click="doCheck">{{ checking ? '校验中...' : '发布前校验' }}</button>
          <button class="secondary-btn" :disabled="impactLoading" @click="doImpact">{{ impactLoading ? '计算中...' : '影响范围计算' }}</button>
          <button class="primary-btn" :disabled="executing || !canExecute" @click="doExecute">{{ executing ? '执行中...' : '创建并执行发布' }}</button>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-title">校验结果</div>
        <div v-if="checkResult" class="result-box">
          <div :class="['status-chip', checkResult.publishable ? 'published' : 'rejected']">{{ checkResult.publishable ? '可发布' : '不可发布' }}</div>
          <div class="sub-text">影响项：{{ checkResult.impactCount }}</div>
          <ul>
            <li v-for="item in checkResult.reasons" :key="`r-${item}`">{{ item }}</li>
            <li v-for="item in checkResult.warnings" :key="`w-${item}`">{{ item }}</li>
          </ul>
        </div>
        <div v-else class="empty-box">点击“发布前校验”查看结果。</div>
      </div>
    </div>

    <div class="panel-card">
      <div class="panel-title">影响范围</div>
      <div v-if="impactResult" class="impact-list">
        <div class="sub-text">共 {{ impactResult.totalItems }} 项</div>
        <div v-for="item in impactResult.items" :key="`${item.action}-${item.path}`" class="impact-item">
          <div>{{ item.pageType }} · {{ item.action }}</div>
          <div class="sub-text">{{ item.path }}</div>
          <div class="sub-text">{{ item.summary || '-' }}</div>
        </div>
      </div>
      <div v-else class="empty-box">点击“影响范围计算”查看受影响页面集合。</div>
    </div>

    <div class="panel-card">
      <div class="panel-title">历史任务</div>
      <div class="toolbar">
        <select v-model="jobFilters.siteId" class="input small-select">
          <option :value="undefined">全部站点</option>
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <select v-model="jobFilters.status" class="input small-select">
          <option value="">全部状态</option>
          <option value="success">success</option>
          <option value="failed">failed</option>
          <option value="rollback_success">rollback_success</option>
          <option value="rollback_failed">rollback_failed</option>
        </select>
        <select v-model="jobFilters.mode" class="input small-select">
          <option value="">全部模式</option>
          <option value="incremental">incremental</option>
          <option value="full">full</option>
          <option value="offline">offline</option>
          <option value="rollback">rollback</option>
        </select>
        <button class="secondary-btn" @click="loadJobs">刷新</button>
      </div>

      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>单位</th>
            <th>模式</th>
            <th>状态</th>
            <th>操作者</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="jobsLoading">
            <td colspan="7" class="empty-box">加载中...</td>
          </tr>
          <tr v-else-if="!jobs.length">
            <td colspan="7" class="empty-box">暂无任务</td>
          </tr>
          <tr v-for="job in jobs" :key="job.id">
            <td>#{{ job.id }}</td>
            <td>{{ job.unitType }} / {{ job.unitIds }}</td>
            <td>{{ job.mode }}</td>
            <td><span :class="['status-chip', job.status.includes('success') ? 'published' : (job.status.includes('failed') ? 'rejected' : 'approved')]">{{ job.status }}</span></td>
            <td>{{ job.operatorName }}</td>
            <td>{{ job.createdAt ? job.createdAt.replace('T', ' ').slice(0, 16) : '-' }}</td>
            <td>
              <div class="actions">
                <button class="link-btn" @click="openJobDetail(job)">详情</button>
                <button v-if="job.status === 'failed'" class="link-btn warning" @click="handleRetry(job)">重试</button>
                <button v-if="job.status === 'success' || job.status === 'rollback_success'" class="link-btn danger" @click="handleRollback(job)">回滚</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <a-modal v-model:open="detailOpen" title="发布任务详情" width="1100px" :footer="null" destroy-on-close>
      <div v-if="selectedJob" class="detail-grid">
        <div class="panel-card">
          <div class="panel-title">任务概览</div>
          <div class="sub-text">#{{ selectedJob.id }} · {{ selectedJob.unitType }} · {{ selectedJob.mode }} · {{ selectedJob.status }}</div>
          <div class="sub-text">{{ selectedJob.resultSummary || selectedJob.failureReason || '暂无摘要' }}</div>
          <div class="footer-actions">
            <button v-if="selectedJob.status === 'failed'" class="secondary-btn" @click="handleRetry(selectedJob)">重试任务</button>
            <button v-if="selectedJob.status === 'success' || selectedJob.status === 'rollback_success'" class="danger-btn" @click="handleRollback(selectedJob)">回滚任务</button>
          </div>
        </div>
        <div class="panel-card">
          <div class="panel-title">影响项</div>
          <div v-if="impacts.length" class="impact-list small">
            <div v-for="item in impacts" :key="`${item.action}-${item.path}`" class="impact-item">
              <div>{{ item.pageType }} · {{ item.action }}</div>
              <div class="sub-text">{{ item.path }}</div>
            </div>
          </div>
          <div v-else class="empty-box">暂无影响项</div>
        </div>
        <div class="panel-card">
          <div class="panel-title">产物</div>
          <div v-if="artifacts.length" class="impact-list small">
            <div v-for="item in artifacts" :key="item.id" class="impact-item">
              <div>{{ item.artifactType }} · {{ item.outputPath }}</div>
              <div class="sub-text">{{ item.version || '-' }}</div>
            </div>
          </div>
          <div v-else class="empty-box">暂无产物</div>
        </div>
        <div class="panel-card full-row">
          <div class="panel-title">执行日志</div>
          <pre class="log-box">{{ logs.join('\n') || '暂无日志' }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 16px; }
.page-header h2 { margin: 0; }
.page-header p { margin: 6px 0 0; color: #64748b; }
.workspace-grid, .detail-grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: 16px; }
.panel-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 16px; }
.panel-title { font-weight: 600; margin-bottom: 12px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-grid label { display: flex; flex-direction: column; gap: 8px; }
.full-row { grid-column: 1 / -1; }
.toolbar, .actions, .top-actions, .footer-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.input { width: 100%; border: 1px solid #cbd5e1; border-radius: 10px; padding: 10px 12px; box-sizing: border-box; }
.small-select { min-width: 160px; }
.primary-btn, .secondary-btn, .danger-btn, .link-btn { border: none; border-radius: 10px; padding: 10px 14px; cursor: pointer; }
.primary-btn { background: #2563eb; color: #fff; }
.secondary-btn { background: #e2e8f0; color: #0f172a; }
.danger-btn { background: #dc2626; color: #fff; }
.link-btn { background: transparent; padding: 0; color: #2563eb; }
.link-btn.warning { color: #b45309; }
.link-btn.danger { color: #dc2626; }
.status-chip { display: inline-flex; padding: 4px 10px; border-radius: 999px; font-size: 12px; }
.status-chip.approved { background: #dbeafe; color: #1d4ed8; }
.status-chip.published { background: #dcfce7; color: #166534; }
.status-chip.rejected { background: #fee2e2; color: #991b1b; }
.result-box, .impact-item, .empty-box { background: #f8fafc; border-radius: 12px; padding: 12px; }
.impact-list { display: flex; flex-direction: column; gap: 10px; }
.data-table { width: 100%; border-collapse: collapse; margin-top: 12px; }
.data-table th, .data-table td { padding: 12px; border-bottom: 1px solid #e2e8f0; text-align: left; }
.log-box { background: #0f172a; color: #e2e8f0; border-radius: 12px; padding: 12px; min-height: 220px; white-space: pre-wrap; }
.sub-text { color: #64748b; font-size: 12px; }
@media (max-width: 1100px) { .workspace-grid, .detail-grid, .form-grid { grid-template-columns: 1fr; } }
</style>