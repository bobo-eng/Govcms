<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SearchOutlined, FilterOutlined, ExportOutlined } from '@ant-design/icons-vue'
import axios from 'axios'

interface Article {
  id: number
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

const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      message.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

const loading = ref(false)
const articles = ref<Article[]>([])
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const modalVisible = ref(false)
const isEdit = ref(false)
const editingArticle = ref<Partial<Article>>({})
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const categories = ['新闻动态', '政策法规', '通知公告', '办事指南', '常见问题']
const statusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'published', label: '已发布' },
  { value: 'archived', label: '已归档' }
]

const fetchArticles = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.value.current - 1,
      size: pagination.value.pageSize
    }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterCategory.value) params.category = filterCategory.value
    if (filterStatus.value) params.status = filterStatus.value
    
    const res = await api.get('/articles', { params })
    articles.value = res.data.content || []
    pagination.value.total = res.data.totalElements || 0
  } catch (e: any) {
    console.error('获取内容列表失败:', e)
    message.error(e.response?.data?.message || '获取内容列表失败')
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

const handleAdd = () => {
  editingArticle.value = { title: '', category: '', author: '', status: 'draft', content: '', summary: '' }
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = (record: Article) => {
  editingArticle.value = { ...record }
  isEdit.value = true
  modalVisible.value = true
}

const handleDelete = (id: number) => {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除该文章吗？此操作不可撤销。',
    okText: '确认删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/articles/${id}`)
        message.success('删除成功')
        fetchArticles()
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

const handleSave = async () => {
  if (!editingArticle.value.title?.trim()) {
    message.error('请输入标题')
    return
  }
  if (!editingArticle.value.category) {
    message.error('请选择分类')
    return
  }
  
  try {
    if (isEdit.value) {
      await api.put(`/articles/${editingArticle.value.id}`, editingArticle.value)
      message.success('更新成功')
    } else {
      await api.post('/articles', editingArticle.value)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchArticles()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

const handlePublish = async (record: Article) => {
  try {
    await api.post(`/articles/${record.id}/publish`)
    message.success('发布成功')
    fetchArticles()
  } catch (e: any) {
    message.error(e.response?.data?.message || '发布失败')
  }
}

const handleUnpublish = async (record: Article) => {
  try {
    await api.post(`/articles/${record.id}/unpublish`)
    message.success('已下架')
    fetchArticles()
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    published: 'success',
    draft: 'warning',
    archived: 'default'
  }
  return map[status] || 'default'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    published: '已发布',
    draft: '草稿',
    archived: '已归档'
  }
  return map[status] || status
}

onMounted(() => { fetchArticles() })
</script>

<template>
  <div class="content-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1>内容管理</h1>
        <p>管理网站文章内容</p>
      </div>
      <button class="primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新建内容</span>
      </button>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input 
          v-model="searchKeyword"
          type="text" 
          placeholder="搜索文章标题..." 
          class="search-input"
          @keyup.enter="handleSearch"
        />
      </div>
      <select v-model="filterCategory" class="filter-select" @change="handleSearch">
        <option value="">全部分类</option>
        <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
      </select>
      <select v-model="filterStatus" class="filter-select" @change="handleSearch">
        <option value="">全部状态</option>
        <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <button class="secondary-btn" @click="handleSearch">搜索</button>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>分类</th>
            <th>作者</th>
            <th>状态</th>
            <th>浏览</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="article in articles" :key="article.id">
            <td>
              <span class="article-title">{{ article.title }}</span>
            </td>
            <td>
              <span class="category-tag">{{ article.category }}</span>
            </td>
            <td>{{ article.author }}</td>
            <td>
              <span class="status-badge" :class="getStatusClass(article.status)">
                {{ getStatusText(article.status) }}
              </span>
            </td>
            <td class="views-cell">
              <EyeOutlined />
              {{ article.views }}
            </td>
            <td class="date-cell">{{ article.createdAt?.split('T')[0] || '-' }}</td>
            <td>
              <div class="action-btns">
                <button class="action-btn" @click="handleEdit(article)" title="编辑">
                  <EditOutlined />
                </button>
                <button v-if="article.status === 'draft'" class="action-btn success" @click="handlePublish(article)" title="发布">
                  <ExportOutlined />
                </button>
                <button v-else class="action-btn warning" @click="handleUnpublish(article)" title="下架">
                  <ExportOutlined />
                </button>
                <button class="action-btn danger" @click="handleDelete(article.id)" title="删除">
                  <DeleteOutlined />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      
      <!-- 分页 -->
      <div class="pagination">
        <span class="pagination-total">共 {{ pagination.total }} 条</span>
        <div class="pagination-controls">
          <button 
            class="page-btn" 
            :disabled="pagination.current === 1"
            @click="handlePageChange(pagination.current - 1, pagination.pageSize)"
          >上一页</button>
          <span class="page-info">{{ pagination.current }} / {{ Math.ceil(pagination.total / pagination.pageSize) || 1 }}</span>
          <button 
            class="page-btn" 
            :disabled="pagination.current >= Math.ceil(pagination.total / pagination.pageSize)"
            @click="handlePageChange(pagination.current + 1, pagination.pageSize)"
          >下一页</button>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div class="modal-overlay" v-if="modalVisible" @click.self="modalVisible = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑内容' : '新建内容' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>标题</label>
            <input 
              v-model="editingArticle.title"
              type="text" 
              placeholder="请输入文章标题"
              class="form-input"
            />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>分类</label>
              <select v-model="editingArticle.category" class="form-select">
                <option value="">请选择分类</option>
                <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>状态</label>
              <select v-model="editingArticle.status" class="form-select">
                <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>作者</label>
            <input 
              v-model="editingArticle.author"
              type="text" 
              placeholder="请输入作者"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label>摘要</label>
            <textarea 
              v-model="editingArticle.summary"
              placeholder="请输入文章摘要"
              class="form-textarea"
              rows="2"
            ></textarea>
          </div>
          <div class="form-group">
            <label>正文</label>
            <textarea 
              v-model="editingArticle.content"
              placeholder="请输入文章内容"
              class="form-textarea"
              rows="6"
            ></textarea>
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
  max-width: 1400px;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-left h1 {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 4px;
}

.header-left p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.primary-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: #2563eb;
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.primary-btn:hover {
  background: #1d4ed8;
}

.secondary-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #1e293b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.secondary-btn:hover {
  background: #f1f5f9;
}

/* 工具栏 */
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-box {
  flex: 1;
  max-width: 320px;
  position: relative;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}

.search-input {
  width: 100%;
  height: 42px;
  padding: 0 14px 0 40px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
}

.search-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.filter-select {
  height: 42px;
  padding: 0 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  background: #fff;
  cursor: pointer;
}

.filter-select:focus {
  border-color: #2563eb;
}

/* 表格 */
.table-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  text-align: left;
  padding: 14px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.data-table td {
  padding: 16px;
  font-size: 14px;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
}

.data-table tr:last-child td {
  border-bottom: none;
}

.data-table tr:hover td {
  background: #f8fafc;
}

.article-title {
  font-weight: 500;
  color: #1e293b;
}

.category-tag {
  background: #f1f5f9;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
}

.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
}

.status-badge.success {
  background: #ecfdf5;
  color: #059669;
}

.status-badge.warning {
  background: #fef3c7;
  color: #d97706;
}

.status-badge.default {
  background: #f1f5f9;
  color: #64748b;
}

.views-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a1a1aa;
}

.date-cell {
  color: #64748b;
}

.action-btns {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  border: none;
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.action-btn.success:hover {
  background: #ecfdf5;
  color: #059669;
}

.action-btn.warning:hover {
  background: #fef3c7;
  color: #d97706;
}

.action-btn.danger:hover {
  background: #fef2f2;
  color: #ef4444;
}

/* 分页 */
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f1f5f9;
}

.pagination-total {
  font-size: 14px;
  color: #64748b;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-btn {
  padding: 6px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 14px;
  color: #1e293b;
  cursor: pointer;
  transition: all 0.15s;
}

.page-btn:hover:not(:disabled) {
  background: #f1f5f9;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #64748b;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 16px;
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  font-size: 24px;
  color: #64748b;
  cursor: pointer;
  border-radius: 6px;
}

.close-btn:hover {
  background: #f1f5f9;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.form-input,
.form-select {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
}

.form-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  resize: vertical;
  font-family: inherit;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
}
</style>
