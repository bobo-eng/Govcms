<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { usePermission } from '../composables/usePermission'
import { fetchCategories } from '../api/categories'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import {
  createArticle,
  deleteArticle,
  fetchArticleDetail,
  fetchArticleHistories,
  fetchArticlePublishCheck,
  fetchArticles,
  type ArticleItem,
  type ArticleLifecycleHistoryItem,
  type ArticlePayload,
  type ArticlePublishCheckResponseData,
  submitArticleReview,
  updateArticle
} from '../api/articles'

interface CategoryOption {
  id: number
  name: string
}

interface ArticleForm {
  id?: number
  siteId?: number | null
  primaryCategoryId?: number | null
  title: string
  summary: string
  author: string
  content: string
}

const router = useRouter()
const { hasPermission } = usePermission()

const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const isEdit = ref(false)
const articles = ref<ArticleItem[]>([])
const sites = ref<SiteOptionItem[]>([])
const categoryOptions = ref<CategoryOption[]>([])
const historyItems = ref<ArticleLifecycleHistoryItem[]>([])
const publishCheck = ref<ArticlePublishCheckResponseData | null>(null)
const filters = ref({ keyword: '', status: '', siteId: undefined as number | undefined, primaryCategoryId: undefined as number | undefined })
const pagination = ref({ current: 1, pageSize: 10, total: 0 })
const form = ref<ArticleForm>({ title: '', summary: '', author: '', content: '', siteId: undefined, primaryCategoryId: undefined })

const canCreate = computed(() => hasPermission('content:article:create'))
const canUpdate = computed(() => hasPermission('content:article:update'))
const canDelete = computed(() => hasPermission('content:article:delete'))
const canSubmit = computed(() => hasPermission('content:article:submit-review'))
const canHistory = computed(() => hasPermission('content:article:history:view'))

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'draft', label: '草稿' },
  { value: 'pending_review', label: '待审核' },
  { value: 'rejected', label: '已驳回' },
  { value: 'approved', label: '待发布' },
  { value: 'published', label: '已发布' },
  { value: 'offline', label: '已下线' }
]

const statusLabel = (status?: string) => {
  const map: Record<string, string> = {
    draft: '草稿',
    pending_review: '待审核',
    rejected: '已驳回',
    approved: '待发布',
    published: '已发布',
    offline: '已下线'
  }
  return status ? (map[status] || status) : '-'
}

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
}

const loadCategories = async (siteId?: number) => {
  if (!siteId) {
    categoryOptions.value = []
    return
  }
  const response = await fetchCategories({ siteId })
  categoryOptions.value = response.data || []
}

const loadArticles = async () => {
  loading.value = true
  try {
    const response = await fetchArticles({
      page: pagination.value.current - 1,
      size: pagination.value.pageSize,
      keyword: filters.value.keyword || undefined,
      status: filters.value.status || undefined,
      siteId: filters.value.siteId,
      primaryCategoryId: filters.value.primaryCategoryId
    })
    articles.value = response.data.content || []
    pagination.value.total = response.data.totalElements || 0
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取内容列表失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.value = { title: '', summary: '', author: '', content: '', siteId: filters.value.siteId, primaryCategoryId: undefined }
  historyItems.value = []
  publishCheck.value = null
}

const openCreate = async () => {
  if (!canCreate.value) {
    message.warning('没有新增内容权限')
    return
  }
  isEdit.value = false
  resetForm()
  if (form.value.siteId) {
    await loadCategories(form.value.siteId || undefined)
  }
  modalOpen.value = true
}

const openEdit = async (record: ArticleItem) => {
  if (!canUpdate.value && !canHistory.value) {
    message.warning('没有查看内容详情权限')
    return
  }
  try {
    const [detailResponse, historyResponse] = await Promise.all([
      fetchArticleDetail(record.id),
      hasPermission('content:article:history:view') ? fetchArticleHistories(record.id) : Promise.resolve({ data: [] })
    ])
    const detail = detailResponse.data
    isEdit.value = true
    form.value = {
      id: detail.id,
      siteId: detail.siteId ?? undefined,
      primaryCategoryId: detail.primaryCategoryId ?? undefined,
      title: detail.title || '',
      summary: detail.summary || '',
      author: detail.author || '',
      content: detail.content || ''
    }
    historyItems.value = historyResponse.data || []
    publishCheck.value = null
    if (detail.siteId) {
      await loadCategories(detail.siteId)
    }
    modalOpen.value = true
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取内容详情失败')
  }
}

const save = async () => {
  if (!form.value.title.trim()) {
    message.warning('请输入标题')
    return
  }
  if (!form.value.siteId) {
    message.warning('请选择站点')
    return
  }
  if (!form.value.primaryCategoryId) {
    message.warning('请选择栏目')
    return
  }
  saving.value = true
  const payload: ArticlePayload = {
    title: form.value.title.trim(),
    summary: form.value.summary?.trim() || null,
    author: form.value.author?.trim() || null,
    content: form.value.content || '',
    siteId: form.value.siteId,
    primaryCategoryId: form.value.primaryCategoryId
  }
  try {
    if (isEdit.value && form.value.id) {
      await updateArticle(form.value.id, payload)
      message.success('内容更新成功')
    } else {
      await createArticle(payload)
      message.success('内容创建成功')
    }
    modalOpen.value = false
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存内容失败')
  } finally {
    saving.value = false
  }
}

const removeArticle = async (record: ArticleItem) => {
  if (!canDelete.value) {
    message.warning('没有删除权限')
    return
  }
  if (!window.confirm(`确认删除《${record.title}》吗？`)) {
    return
  }
  try {
    await deleteArticle(record.id)
    message.success('删除成功')
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除失败')
  }
}

const submitReviewAction = async (record: ArticleItem) => {
  if (!canSubmit.value) {
    message.warning('没有提交审核权限')
    return
  }
  try {
    await submitArticleReview(record.id)
    message.success('提交审核成功')
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '提交审核失败')
  }
}

const viewPublishCheck = async (record: ArticleItem) => {
  try {
    const response = await fetchArticlePublishCheck(record.id)
    publishCheck.value = response.data
    if (!modalOpen.value) {
      await openEdit(record)
      publishCheck.value = response.data
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取发布检查失败')
  }
}

const gotoPublish = (record: ArticleItem, mode: 'incremental' | 'offline') => {
  router.push({
    path: '/content/publish',
    query: {
      siteId: String(record.siteId || ''),
      unitType: 'content',
      unitId: String(record.id),
      mode
    }
  })
}

watch(() => form.value.siteId, async siteId => {
  await loadCategories(siteId || undefined)
  if (!categoryOptions.value.some(item => item.id === form.value.primaryCategoryId)) {
    form.value.primaryCategoryId = undefined
  }
})

watch(() => filters.value.siteId, async siteId => {
  await loadCategories(siteId || undefined)
  if (!categoryOptions.value.some(item => item.id === filters.value.primaryCategoryId)) {
    filters.value.primaryCategoryId = undefined
  }
})

onMounted(async () => {
  await loadSites()
  await loadCategories(filters.value.siteId)
  await loadArticles()
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2>内容中心</h2>
        <p>按六态生命周期管理内容，提交审核后进入审核工作区，正式发布统一收口到发布中心。</p>
      </div>
      <button class="primary-btn" :disabled="!canCreate" @click="openCreate">新建内容</button>
    </div>

    <div class="toolbar">
      <input v-model="filters.keyword" class="input" placeholder="关键词搜索" @keyup.enter="loadArticles" />
      <select v-model="filters.siteId" class="input small-select">
        <option :value="undefined">全部站点</option>
        <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
      </select>
      <select v-model="filters.primaryCategoryId" class="input small-select">
        <option :value="undefined">全部栏目</option>
        <option v-for="item in categoryOptions" :key="item.id" :value="item.id">{{ item.name }}</option>
      </select>
      <select v-model="filters.status" class="input small-select">
        <option v-for="item in statusOptions" :key="item.value || 'all'" :value="item.value">{{ item.label }}</option>
      </select>
      <button class="secondary-btn" @click="loadArticles">查询</button>
    </div>

    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>栏目</th>
            <th>状态</th>
            <th>版本</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="empty-row">加载中...</td>
          </tr>
          <tr v-else-if="!articles.length">
            <td colspan="6" class="empty-row">暂无数据</td>
          </tr>
          <tr v-for="item in articles" :key="item.id">
            <td>
              <div class="title-cell">{{ item.title }}</div>
              <div class="sub-text">{{ item.author || '未填写作者' }}</div>
            </td>
            <td>{{ item.category || '-' }}</td>
            <td><span :class="['status-chip', item.status]">{{ statusLabel(item.status) }}</span></td>
            <td>r{{ item.currentRevision || 1 }}</td>
            <td>{{ item.updatedAt ? item.updatedAt.replace('T', ' ').slice(0, 16) : '-' }}</td>
            <td>
              <div class="actions">
                <button class="link-btn" @click="openEdit(item)">详情</button>
                <button v-if="canUpdate && (item.status === 'draft' || item.status === 'rejected')" class="link-btn" @click="openEdit(item)">编辑</button>
                <button v-if="canDelete && (item.status === 'draft' || item.status === 'rejected')" class="link-btn danger" @click="removeArticle(item)">删除</button>
                <button v-if="canSubmit && (item.status === 'draft' || item.status === 'rejected')" class="link-btn" @click="submitReviewAction(item)">提交审核</button>
                <button v-if="item.status === 'approved'" class="link-btn" @click="gotoPublish(item, 'incremental')">去发布中心</button>
                <button v-if="item.status === 'published'" class="link-btn warning" @click="gotoPublish(item, 'offline')">下线</button>
                <button class="link-btn" @click="viewPublishCheck(item)">发布检查</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pagination-row">
      <span>共 {{ pagination.total }} 条</span>
      <div class="actions">
        <button class="secondary-btn" :disabled="pagination.current <= 1" @click="pagination.current -= 1; loadArticles()">上一页</button>
        <span>第 {{ pagination.current }} 页</span>
        <button class="secondary-btn" :disabled="pagination.current * pagination.pageSize >= pagination.total" @click="pagination.current += 1; loadArticles()">下一页</button>
      </div>
    </div>

    <a-modal v-model:open="modalOpen" :title="isEdit ? '内容详情 / 编辑' : '新建内容'" width="980px" :footer="null" destroy-on-close>
      <div class="modal-grid">
        <div class="form-panel">
          <div class="form-grid">
            <label>
              <span>站点</span>
              <select v-model="form.siteId" class="input">
                <option :value="undefined">请选择站点</option>
                <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
              </select>
            </label>
            <label>
              <span>栏目</span>
              <select v-model="form.primaryCategoryId" class="input">
                <option :value="undefined">请选择栏目</option>
                <option v-for="item in categoryOptions" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </label>
            <label class="full-row">
              <span>标题</span>
              <input v-model="form.title" class="input" placeholder="请输入标题" />
            </label>
            <label>
              <span>作者</span>
              <input v-model="form.author" class="input" placeholder="作者" />
            </label>
            <label>
              <span>摘要</span>
              <input v-model="form.summary" class="input" placeholder="摘要" />
            </label>
            <label class="full-row">
              <span>正文</span>
              <textarea v-model="form.content" class="textarea" rows="10" placeholder="请输入正文"></textarea>
            </label>
          </div>
          <div class="footer-actions">
            <button class="secondary-btn" @click="modalOpen = false">关闭</button>
            <button v-if="isEdit && form.id" class="secondary-btn" @click="viewPublishCheck({ id: form.id, siteId: form.siteId, title: form.title, status: 'draft' } as ArticleItem)">发布检查</button>
            <button v-if="!isEdit || canUpdate" class="primary-btn" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</button>
          </div>
        </div>
        <div class="side-panel">
          <div class="side-card">
            <div class="side-title">发布检查</div>
            <div v-if="publishCheck" class="check-box">
              <div :class="['status-chip', publishCheck.publishable ? 'published' : 'rejected']">{{ publishCheck.publishable ? '可发布' : '不可发布' }}</div>
              <div class="sub-text">模板：{{ publishCheck.templateName || '未解析' }}</div>
              <ul>
                <li v-for="item in publishCheck.reasons" :key="`reason-${item}`">{{ item }}</li>
                <li v-for="item in publishCheck.warnings" :key="`warn-${item}`">{{ item }}</li>
              </ul>
            </div>
            <div v-else class="empty-side">点击“发布检查”查看当前内容是否满足正式发布条件。</div>
          </div>
          <div class="side-card">
            <div class="side-title">流转历史</div>
            <div v-if="historyItems.length" class="history-list">
              <div v-for="item in historyItems.slice(0, 6)" :key="item.id" class="history-item">
                <div>{{ item.action }} · {{ item.operatorName }}</div>
                <div class="sub-text">{{ item.fromStatus || '-' }} → {{ item.toStatus || '-' }}</div>
                <div class="sub-text">{{ item.createdAt?.replace('T', ' ').slice(0, 16) }}</div>
                <div v-if="item.reason" class="sub-text">{{ item.reason }}</div>
              </div>
            </div>
            <div v-else class="empty-side">暂无流转记录</div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.page-header h2 { margin: 0; }
.page-header p { margin: 6px 0 0; color: #64748b; }
.toolbar, .actions, .pagination-row, .footer-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.table-card, .side-card, .form-panel { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 16px; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px; border-bottom: 1px solid #e2e8f0; text-align: left; vertical-align: top; }
.empty-row { text-align: center; color: #94a3b8; padding: 40px 0; }
.title-cell { font-weight: 600; }
.sub-text { color: #64748b; font-size: 12px; }
.input, .textarea { width: 100%; border: 1px solid #cbd5e1; border-radius: 10px; padding: 10px 12px; box-sizing: border-box; }
.small-select { min-width: 160px; }
.primary-btn, .secondary-btn, .link-btn { border: none; border-radius: 10px; padding: 10px 14px; cursor: pointer; }
.primary-btn { background: #2563eb; color: #fff; }
.secondary-btn { background: #e2e8f0; color: #0f172a; }
.link-btn { background: transparent; color: #2563eb; padding: 0; }
.link-btn.danger { color: #dc2626; }
.link-btn.warning { color: #b45309; }
.status-chip { display: inline-flex; padding: 4px 10px; border-radius: 999px; font-size: 12px; }
.status-chip.draft { background: #e2e8f0; color: #334155; }
.status-chip.pending_review { background: #fef3c7; color: #92400e; }
.status-chip.rejected { background: #fee2e2; color: #991b1b; }
.status-chip.approved { background: #dbeafe; color: #1d4ed8; }
.status-chip.published { background: #dcfce7; color: #166534; }
.status-chip.offline { background: #f3e8ff; color: #6b21a8; }
.modal-grid { display: grid; grid-template-columns: 1.3fr 0.9fr; gap: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-grid label { display: flex; flex-direction: column; gap: 8px; }
.full-row { grid-column: 1 / -1; }
.side-panel { display: flex; flex-direction: column; gap: 16px; }
.side-title { font-weight: 600; margin-bottom: 10px; }
.history-list { display: flex; flex-direction: column; gap: 10px; }
.history-item, .check-box, .empty-side { background: #f8fafc; border-radius: 12px; padding: 12px; }
.pagination-row { justify-content: space-between; }
@media (max-width: 1100px) { .modal-grid { grid-template-columns: 1fr; } .form-grid { grid-template-columns: 1fr; } }
</style>