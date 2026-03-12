<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, ExportOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'
import { fetchCategories } from '../api/categories'

interface Site {
  id: number
  name: string
  code: string
  status: string
}

interface CategoryOption {
  id: number
  name: string
  fullPath: string
  siteId: number
}

interface Article {
  id: number
  siteId?: number | null
  primaryCategoryId?: number | null
  title: string
  content?: string
  summary?: string
  category: string
  author: string
  status: string
  views: number
  createdAt: string
  updatedAt?: string
}

interface ArticleForm {
  id?: number
  siteId?: number | null
  primaryCategoryId?: number | null
  title?: string
  content?: string
  summary?: string
  category?: string
  author?: string
  status?: string
}

const { hasPermission } = usePermission()
const canCreateArticle = hasPermission('content:article:create')
const canUpdateArticle = hasPermission('content:article:update')
const canDeleteArticle = hasPermission('content:article:delete')
const canPublishArticle = hasPermission('content:article:publish')

const loading = ref(false)
const articles = ref<Article[]>([])
const sites = ref<Site[]>([])
const categoryOptions = ref<CategoryOption[]>([])
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const filterSiteId = ref<number | null>(null)
const modalVisible = ref(false)
const isEdit = ref(false)
const editingArticle = ref<ArticleForm>({})
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const statusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'published', label: '已发布' },
  { value: 'archived', label: '已归档' }
]

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`暂无${actionName}权限`)
  return false
}

const selectedCategoryName = (categoryId?: number | null) => {
  if (!categoryId) {
    return ''
  }
  return categoryOptions.value.find(item => item.id === categoryId)?.name || ''
}

const fetchSites = async () => {
  try {
    const res = await api.get('/sites', {
      params: {
        page: 0,
        size: 100,
        status: 'enabled'
      }
    })
    sites.value = res.data.content || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取站点列表失败')
  }
}

const fetchCategoryOptions = async (siteId?: number | null) => {
  if (!siteId) {
    categoryOptions.value = []
    return
  }
  try {
    const res = await fetchCategories({ siteId })
    categoryOptions.value = res.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取栏目列表失败')
  }
}

const fetchArticles = async () => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.value.current - 1,
      size: pagination.value.pageSize
    }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (filterCategory.value) params.category = filterCategory.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterSiteId.value) params.siteId = filterSiteId.value

    const res = await api.get('/articles', { params })
    articles.value = res.data.content || []
    pagination.value.total = res.data.totalElements || 0
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取内容列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.current = 1
  fetchArticles()
}

const handlePageChange = (page: number, pageSize: number) => {
  pagination.value.current = page
  pagination.value.pageSize = pageSize
  fetchArticles()
}

const openAddModal = async () => {
  if (!ensurePermission('content:article:create', '新增内容')) {
    return
  }
  editingArticle.value = {
    title: '',
    siteId: filterSiteId.value,
    primaryCategoryId: null,
    category: '',
    author: '',
    status: 'draft',
    content: '',
    summary: ''
  }
  isEdit.value = false
  if (editingArticle.value.siteId) {
    await fetchCategoryOptions(editingArticle.value.siteId)
  }
  modalVisible.value = true
}

const openEditModal = async (record: Article) => {
  if (!ensurePermission('content:article:update', '编辑内容')) {
    return
  }
  editingArticle.value = {
    ...record,
    siteId: record.siteId ?? null,
    primaryCategoryId: record.primaryCategoryId ?? null,
    category: record.category || ''
  }
  if (editingArticle.value.siteId) {
    await fetchCategoryOptions(editingArticle.value.siteId)
  }
  isEdit.value = true
  modalVisible.value = true
}

const handleEditingSiteChange = async () => {
  editingArticle.value.primaryCategoryId = null
  editingArticle.value.category = ''
  await fetchCategoryOptions(editingArticle.value.siteId)
}

const buildPayload = () => {
  const title = editingArticle.value.title?.trim()
  if (!title) {
    message.error('请输入标题')
    return null
  }
  if (!editingArticle.value.siteId) {
    message.error('请选择站点')
    return null
  }
  if (!editingArticle.value.primaryCategoryId) {
    message.error('请选择栏目')
    return null
  }
  return {
    title,
    siteId: editingArticle.value.siteId,
    primaryCategoryId: editingArticle.value.primaryCategoryId,
    category: selectedCategoryName(editingArticle.value.primaryCategoryId),
    author: editingArticle.value.author?.trim() || '',
    status: editingArticle.value.status || 'draft',
    content: editingArticle.value.content?.trim() || '',
    summary: editingArticle.value.summary?.trim() || ''
  }
}

const handleSave = async () => {
  const requiredPermission = isEdit.value ? 'content:article:update' : 'content:article:create'
  const actionName = isEdit.value ? '编辑内容' : '新增内容'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }
  const payload = buildPayload()
  if (!payload) {
    return
  }

  try {
    if (isEdit.value && editingArticle.value.id) {
      await api.put(`/articles/${editingArticle.value.id}`, payload)
      message.success('内容更新成功')
    } else {
      await api.post('/articles', payload)
      message.success('内容创建成功')
    }
    modalVisible.value = false
    fetchArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存内容失败')
  }
}

const handleDelete = (record: Article) => {
  if (!ensurePermission('content:article:delete', '删除内容')) {
    return
  }
  Modal.confirm({
    title: '删除内容',
    content: `确认删除“${record.title}”吗？`,
    okText: '确认删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/articles/${record.id}`)
        message.success('内容删除成功')
        fetchArticles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除内容失败')
      }
    }
  })
}

const handlePublish = async (record: Article) => {
  if (!ensurePermission('content:article:publish', '发布内容')) {
    return
  }
  try {
    await api.post(`/articles/${record.id}/publish`)
    message.success('内容发布成功')
    fetchArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '发布内容失败')
  }
}

const handleUnpublish = async (record: Article) => {
  if (!ensurePermission('content:article:publish', '下线内容')) {
    return
  }
  try {
    await api.post(`/articles/${record.id}/unpublish`)
    message.success('内容已下线')
    fetchArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '下线内容失败')
  }
}

const getStatusText = (status: string) => {
  const mapping: Record<string, string> = {
    draft: '草稿',
    published: '已发布',
    archived: '已归档'
  }
  return mapping[status] || status
}

watch(filterSiteId, async value => {
  filterCategory.value = ''
  await fetchCategoryOptions(value)
  fetchArticles()
})

watch(
  () => editingArticle.value.primaryCategoryId,
  value => {
    editingArticle.value.category = selectedCategoryName(value)
  }
)

onMounted(async () => {
  await fetchSites()
  await fetchArticles()
})
</script>

<template>
  <div class="content-page">
    <div class="page-header">
      <div class="header-left">
        <h1>内容管理</h1>
        <p>管理文章内容，并为其配置站点和栏目归属</p>
      </div>
      <button v-if="canCreateArticle" class="primary-btn" @click="openAddModal">
        <PlusOutlined />
        <span>新建内容</span>
      </button>
    </div>

    <div class="toolbar">
      <select v-model="filterSiteId" class="filter-select">
        <option :value="null">全部站点</option>
        <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
      </select>
      <select v-model="filterCategory" class="filter-select" @change="handleSearch">
        <option value="">全部栏目</option>
        <option v-for="item in categoryOptions" :key="item.id" :value="item.name">{{ item.fullPath }}</option>
      </select>
      <select v-model="filterStatus" class="filter-select" @change="handleSearch">
        <option value="">全部状态</option>
        <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input v-model="searchKeyword" type="text" class="search-input" placeholder="搜索标题或正文" @keyup.enter="handleSearch" />
      </div>
      <button class="secondary-btn" @click="handleSearch">查询</button>
    </div>

    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>站点</th>
            <th>栏目</th>
            <th>作者</th>
            <th>状态</th>
            <th>浏览量</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && articles.length === 0">
            <td colspan="8" class="empty-row">暂无内容</td>
          </tr>
          <tr v-for="article in articles" :key="article.id">
            <td>{{ article.title }}</td>
            <td>{{ sites.find(site => site.id === article.siteId)?.name || '-' }}</td>
            <td>{{ article.category || '-' }}</td>
            <td>{{ article.author || '-' }}</td>
            <td>
              <span :class="['status-tag', article.status]">{{ getStatusText(article.status) }}</span>
            </td>
            <td>{{ article.views }}</td>
            <td>{{ article.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
            <td>
              <div class="action-btns">
                <button v-if="canUpdateArticle" class="action-btn" @click="openEditModal(article)" title="编辑">
                  <EditOutlined />
                </button>
                <button v-if="canPublishArticle && article.status === 'draft'" class="action-btn success" @click="handlePublish(article)" title="发布">
                  <ExportOutlined />
                </button>
                <button v-else-if="canPublishArticle && article.status === 'published'" class="action-btn warning" @click="handleUnpublish(article)" title="下线">
                  <ExportOutlined />
                </button>
                <button v-if="canDeleteArticle" class="action-btn danger" @click="handleDelete(article)" title="删除">
                  <DeleteOutlined />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <span class="pagination-total">共 {{ pagination.total }} 条</span>
        <div class="pagination-controls">
          <button class="page-btn" :disabled="pagination.current === 1" @click="handlePageChange(pagination.current - 1, pagination.pageSize)">上一页</button>
          <span class="page-info">{{ pagination.current }} / {{ Math.ceil(pagination.total / pagination.pageSize) || 1 }}</span>
          <button class="page-btn" :disabled="pagination.current >= Math.ceil(pagination.total / pagination.pageSize)" @click="handlePageChange(pagination.current + 1, pagination.pageSize)">下一页</button>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="modalVisible" @click.self="modalVisible = false">
      <div class="modal-content large">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑内容' : '新建内容' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>标题</label>
            <input v-model="editingArticle.title" type="text" class="form-input" placeholder="请输入文章标题" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>站点</label>
              <select v-model="editingArticle.siteId" class="form-select" @change="handleEditingSiteChange">
                <option :value="null">请选择站点</option>
                <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>栏目</label>
              <select v-model="editingArticle.primaryCategoryId" class="form-select">
                <option :value="null">请选择栏目</option>
                <option v-for="item in categoryOptions" :key="item.id" :value="item.id">{{ item.fullPath }}</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>作者</label>
              <input v-model="editingArticle.author" type="text" class="form-input" placeholder="请输入作者" />
            </div>
            <div class="form-group">
              <label>状态</label>
              <select v-model="editingArticle.status" class="form-select">
                <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>摘要</label>
            <textarea v-model="editingArticle.summary" class="form-textarea" rows="3" placeholder="请输入摘要"></textarea>
          </div>
          <div class="form-group">
            <label>正文</label>
            <textarea v-model="editingArticle.content" class="form-textarea" rows="8" placeholder="请输入正文"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary-btn" @click="modalVisible = false">取消</button>
          <button class="primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.content-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header,
.toolbar,
.table-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px;
}

.page-header,
.toolbar,
.modal-header,
.modal-footer,
.form-row {
  display: flex;
  align-items: center;
}

.page-header,
.modal-header,
.modal-footer {
  justify-content: space-between;
}

.header-left h1 {
  margin: 0;
  color: #0f172a;
}

.header-left p {
  margin: 6px 0 0;
  color: #64748b;
}

.toolbar {
  gap: 12px;
  flex-wrap: wrap;
}

.filter-select,
.form-select,
.form-input,
.search-input,
.form-textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  box-sizing: border-box;
}

.filter-select {
  width: 220px;
}

.search-box {
  position: relative;
  min-width: 240px;
  flex: 1;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}

.search-input {
  padding-left: 38px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
}

.empty-row {
  text-align: center;
  color: #94a3b8;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.status-tag.published {
  background: #dcfce7;
  color: #166534;
}

.status-tag.draft {
  background: #fef3c7;
  color: #92400e;
}

.status-tag.archived {
  background: #e2e8f0;
  color: #334155;
}

.action-btns {
  display: flex;
  gap: 8px;
}

.action-btn,
.primary-btn,
.secondary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  font-size: 14px;
}

.action-btn {
  width: 34px;
  height: 34px;
  background: #f8fafc;
  color: #1e293b;
}

.action-btn.success {
  background: #dcfce7;
  color: #166534;
}

.action-btn.warning {
  background: #fef3c7;
  color: #92400e;
}

.action-btn.danger {
  background: #fee2e2;
  color: #b91c1c;
}

.primary-btn,
.secondary-btn {
  padding: 10px 16px;
}

.primary-btn {
  background: #2563eb;
  color: #ffffff;
}

.secondary-btn,
.page-btn {
  background: #f1f5f9;
  color: #0f172a;
}

.pagination {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-total,
.page-info {
  color: #64748b;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-btn {
  border: none;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.modal-content {
  width: min(760px, calc(100vw - 32px));
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
}

.modal-content.large {
  width: min(920px, calc(100vw - 32px));
}

.modal-header,
.modal-footer {
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-footer {
  border-top: 1px solid #e2e8f0;
  border-bottom: none;
  gap: 12px;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 70vh;
  overflow-y: auto;
}

.close-btn {
  border: none;
  background: transparent;
  font-size: 24px;
  cursor: pointer;
  color: #64748b;
}

.form-row {
  gap: 16px;
}

.form-row > .form-group {
  flex: 1;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (max-width: 960px) {
  .form-row,
  .toolbar,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .pagination-controls {
    justify-content: space-between;
  }
}
</style>
