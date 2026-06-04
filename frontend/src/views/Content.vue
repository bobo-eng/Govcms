<script setup lang="ts">
import '../styles/admin-refresh.css'

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

const statusClass = (status?: string) => {
  const map: Record<string, string> = {
    draft: 'admin-status-chip--draft',
    pending_review: 'admin-status-chip--pending-review',
    rejected: 'admin-status-chip--rejected',
    approved: 'admin-status-chip--approved',
    published: 'admin-status-chip--published',
    offline: 'admin-status-chip--offline'
  }
  return map[status || 'draft'] || 'admin-status-chip--draft'
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
    if (form.value.siteId) {
      await loadCategories(form.value.siteId || undefined)
    }
    modalOpen.value = true
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取内容详情失败')
  }
}

const save = async () => {
  if (!form.value.siteId || !form.value.primaryCategoryId || !form.value.title.trim()) {
    message.warning('请填写完整的内容基础信息')
    return
  }
  saving.value = true
  const payload: ArticlePayload = {
    siteId: form.value.siteId,
    primaryCategoryId: form.value.primaryCategoryId,
    title: form.value.title,
    summary: form.value.summary,
    author: form.value.author,
    content: form.value.content
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
    message.warning('没有删除内容权限')
    return
  }
  if (!window.confirm(`确认删除《${record.title}》吗？`)) return
  try {
    await deleteArticle(record.id)
    message.success('删除成功')
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除失败')
  }
}

const submitReviewAction = async (record: ArticleItem) => {
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
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取发布检查失败')
  }
}

const gotoPublish = (record: ArticleItem, mode: 'incremental' | 'offline') => {
  router.push({
    path: '/content/publish',
    query: {
      siteId: String(record.siteId || filters.value.siteId || ''),
      unitType: 'content',
      unitId: String(record.id),
      mode
    }
  })
}

watch(() => filters.value.siteId, async siteId => {
  filters.value.primaryCategoryId = undefined
  await loadCategories(siteId)
  await loadArticles()
})

onMounted(async () => {
  await loadSites()
  await loadArticles()
})
</script>

<template>
  <div class="admin-page content-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">内容管理</h1>
        <p class="admin-page-desc">管理内容稿件、提交审核、查看发布检查与生命周期流转记录。</p>
      </div>
      <button v-if="canCreate" class="admin-primary-btn" @click="openCreate">
        <span>新建内容</span>
      </button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <div class="admin-search-box">
          <input v-model="filters.keyword" class="admin-search-input" placeholder="搜索标题、摘要或作者" @keyup.enter="loadArticles" />
        </div>
        <select v-model="filters.siteId" class="admin-filter-select">
          <option :value="undefined">全部站点</option>
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <select v-model="filters.primaryCategoryId" class="admin-filter-select" @change="loadArticles">
          <option :value="undefined">全部栏目</option>
          <option v-for="item in categoryOptions" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
        <select v-model="filters.status" class="admin-filter-select" @change="loadArticles">
          <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <button class="admin-secondary-btn" @click="loadArticles">查询</button>
      </div>
    </div>

    <div class="admin-table-card">
      <table class="admin-data-table">
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
          <template v-if="loading">
            <tr v-for="n in 6" :key="`sk-${n}`">
              <td colspan="6">
                <div class="admin-skeleton-row" style="margin: 8px 16px;"></div>
              </td>
            </tr>
          </template>
          <tr v-else-if="!articles.length">
            <td colspan="6">
              <div class="admin-empty-box">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <rect x="3" y="3" width="18" height="18" rx="4"/>
                  <path d="M9 9h6H9z"/>
                </svg>
                <p>暂无内容稿件</p>
                <button v-if="canCreate" class="admin-primary-btn" @click="openCreate">新建内容</button>
              </div>
            </td>
          </tr>
          <tr v-for="item in articles" :key="item.id">
            <td>
              <div class="article-title-cell">
                <strong>{{ item.title }}</strong>
                <span class="admin-sub-text">{{ item.author || '未填写作者' }}</span>
              </div>
            </td>
            <td>{{ item.category || '-' }}</td>
            <td><span :class="['admin-status-chip', statusClass(item.status)]">{{ statusLabel(item.status) }}</span></td>
            <td>r{{ item.currentRevision || 1 }}</td>
            <td class="admin-muted-cell">{{ item.updatedAt ? item.updatedAt.replace('T', ' ').slice(0, 16) : '-' }}</td>
            <td>
              <div class="article-actions">
                <button class="admin-link-action" @click="openEdit(item)">详情</button>
                <button v-if="canUpdate && (item.status === 'draft' || item.status === 'rejected')" class="admin-link-action" @click="openEdit(item)">编辑</button>
                <button v-if="canDelete && (item.status === 'draft' || item.status === 'rejected')" class="admin-link-action" @click="removeArticle(item)">删除</button>
                <button v-if="canSubmit && (item.status === 'draft' || item.status === 'rejected')" class="admin-link-action" @click="submitReviewAction(item)">提交审核</button>
                <button v-if="item.status === 'approved'" class="admin-link-action" @click="gotoPublish(item, 'incremental')">去发布中心</button>
                <button v-if="item.status === 'published'" class="admin-link-action" @click="gotoPublish(item, 'offline')">下线</button>
                <button class="admin-link-action" @click="viewPublishCheck(item)">发布检查</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="admin-pagination">
        <span class="admin-pagination-total">共 {{ pagination.total }} 条</span>
        <div class="admin-pagination-controls">
          <button class="admin-page-btn" :disabled="pagination.current <= 1" @click="pagination.current -= 1; loadArticles()">上一页</button>
          <span class="admin-page-info">第 {{ pagination.current }} 页</span>
          <button class="admin-page-btn" :disabled="pagination.current * pagination.pageSize >= pagination.total" @click="pagination.current += 1; loadArticles()">下一页</button>
        </div>
      </div>
    </div>

    <a-modal v-model:open="modalOpen" :title="isEdit ? '内容详情 / 编辑' : '新建内容'" width="1100px" :footer="null" destroy-on-close>
      <div class="content-detail-shell">
        <div class="admin-card modal-section">
          <div class="admin-card-header"><h3 class="admin-card-title">基础信息</h3></div>
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">站点</label>
              <select v-model="form.siteId" class="admin-form-select">
                <option :value="undefined">请选择站点</option>
                <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
              </select>
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">栏目</label>
              <select v-model="form.primaryCategoryId" class="admin-form-select">
                <option :value="undefined">请选择栏目</option>
                <option v-for="item in categoryOptions" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </div>
          </div>
          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">标题</label>
              <input v-model="form.title" class="admin-form-input" placeholder="请输入标题" />
            </div>
          </div>
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">作者</label>
              <input v-model="form.author" class="admin-form-input" placeholder="作者" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">摘要</label>
              <input v-model="form.summary" class="admin-form-input" placeholder="摘要" />
            </div>
          </div>
        </div>

        <div class="admin-card modal-section">
          <div class="admin-card-header"><h3 class="admin-card-title">正文编辑</h3></div>
          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">正文</label>
              <textarea v-model="form.content" class="admin-form-textarea" rows="12" placeholder="请输入正文"></textarea>
            </div>
          </div>
        </div>

        <div class="admin-detail-grid">
          <div class="admin-detail-card">
            <div class="admin-card-header"><h3 class="admin-card-title">发布检查</h3></div>
            <div v-if="publishCheck" class="publish-check-box">
              <div class="admin-sub-text">模板：{{ publishCheck.templateName || '-' }}</div>
              <ul class="detail-list">
                <li v-for="item in publishCheck.reasons" :key="`reason-${item}`">{{ item }}</li>
                <li v-for="item in publishCheck.warnings" :key="`warn-${item}`">{{ item }}</li>
                <li v-if="!publishCheck.reasons.length && !publishCheck.warnings.length">当前无额外提示</li>
              </ul>
            </div>
            <div v-else class="admin-empty-state">点击“发布检查”后查看结果。</div>
          </div>

          <div class="admin-detail-card">
            <div class="admin-card-header"><h3 class="admin-card-title">生命周期历史</h3></div>
            <div v-if="historyItems.length" class="admin-history-list">
              <div v-for="item in historyItems" :key="item.id" class="admin-history-item">
                <div>{{ item.action }} · {{ item.operatorName }}</div>
                <div class="admin-sub-text">{{ item.fromStatus || '-' }} → {{ item.toStatus || '-' }}</div>
                <div class="admin-sub-text">{{ item.createdAt?.replace('T', ' ').slice(0, 16) }}</div>
                <div v-if="item.reason" class="admin-sub-text">{{ item.reason }}</div>
              </div>
            </div>
            <div v-else class="admin-empty-state">暂无历史记录</div>
          </div>
        </div>

        <div class="admin-toolbar-row content-footer-actions">
          <button class="admin-secondary-btn" @click="modalOpen = false">关闭</button>
          <button v-if="isEdit && form.id" class="admin-secondary-btn" @click="viewPublishCheck({ id: form.id, siteId: form.siteId, title: form.title, status: 'draft' } as ArticleItem)">发布检查</button>
          <button v-if="!isEdit || canUpdate" class="admin-primary-btn" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存内容' }}</button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.content-page .article-title-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.content-page .article-actions,
.content-page .content-footer-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.content-page .content-detail-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-page .modal-section {
  padding: 16px;
}

.content-page .publish-check-box {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
}

.content-page .detail-list {
  margin: 10px 0 0;
  padding-left: 18px;
  color: #334155;
}
</style>
