<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined, PoweroffOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface Site {
  id: number
  name: string
  code: string
  domain?: string | null
  organizationId?: number | null
  description?: string | null
  status: string
  createdAt: string
  updatedAt?: string
}

interface SiteForm {
  id?: number
  name?: string
  code?: string
  domain?: string | null
  organizationId?: number | string | null
  description?: string | null
  status?: string
}

const { hasPermission } = usePermission()
const canCreateSite = hasPermission('site:manage:create')
const canUpdateSite = hasPermission('site:manage:update')
const canDeleteSite = hasPermission('site:manage:delete')

const loading = ref(false)
const sites = ref<Site[]>([])
const searchKeyword = ref('')
const filterStatus = ref('')
const filterOrganizationId = ref('')
const modalVisible = ref(false)
const isEdit = ref(false)
const editingSite = ref<SiteForm>({})
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const statusOptions = [
  { value: 'enabled', label: '启用' },
  { value: 'disabled', label: '禁用' }
]

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`暂无${actionName}权限`)
  return false
}

const normalizeOrganizationId = (value: number | string | null | undefined) => {
  if (value === '' || value === null || value === undefined) {
    return null
  }

  const normalized = Number(value)
  if (Number.isNaN(normalized)) {
    return NaN
  }

  return normalized
}

const buildSitePayload = () => {
  const organizationId = normalizeOrganizationId(editingSite.value.organizationId)
  if (Number.isNaN(organizationId)) {
    message.error('组织 ID 必须为数字')
    return null
  }

  return {
    name: editingSite.value.name?.trim() || '',
    code: editingSite.value.code?.trim() || '',
    domain: editingSite.value.domain?.trim() || null,
    organizationId,
    description: editingSite.value.description?.trim() || null,
    status: editingSite.value.status || 'enabled'
  }
}

const fetchSites = async () => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.value.current - 1,
      size: pagination.value.pageSize
    }

    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (filterStatus.value) params.status = filterStatus.value
    if (filterOrganizationId.value.trim()) params.organizationId = Number(filterOrganizationId.value.trim())

    const res = await api.get('/sites', { params })
    sites.value = res.data.content || []
    pagination.value.total = res.data.totalElements || 0
  } catch (error: any) {
    console.error('Failed to fetch sites:', error)
    message.error(error.response?.data?.message || '获取站点列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.current = 1
  fetchSites()
}

const handlePageChange = (page: number, pageSize: number) => {
  pagination.value.current = page
  pagination.value.pageSize = pageSize
  fetchSites()
}

const handleAdd = () => {
  if (!ensurePermission('site:manage:create', '新增站点')) {
    return
  }

  editingSite.value = {
    name: '',
    code: '',
    domain: '',
    organizationId: null,
    description: '',
    status: 'enabled'
  }
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = (record: Site) => {
  if (!ensurePermission('site:manage:update', '编辑站点')) {
    return
  }

  editingSite.value = {
    ...record,
    organizationId: record.organizationId ?? null,
    domain: record.domain ?? '',
    description: record.description ?? ''
  }
  isEdit.value = true
  modalVisible.value = true
}

const handleSave = async () => {
  const requiredPermission = isEdit.value ? 'site:manage:update' : 'site:manage:create'
  const actionName = isEdit.value ? '编辑站点' : '新增站点'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }

  const payload = buildSitePayload()
  if (!payload) {
    return
  }
  if (!payload.name) {
    message.error('请输入站点名称')
    return
  }
  if (!payload.code) {
    message.error('请输入站点编码')
    return
  }

  try {
    if (isEdit.value && editingSite.value.id) {
      await api.put(`/sites/${editingSite.value.id}`, payload)
      message.success('站点更新成功')
    } else {
      await api.post('/sites', payload)
      message.success('站点创建成功')
    }
    modalVisible.value = false
    fetchSites()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存站点失败')
  }
}

const handleToggleStatus = async (record: Site) => {
  if (!ensurePermission('site:manage:update', '修改站点状态')) {
    return
  }

  const nextStatus = record.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await api.put(`/sites/${record.id}`, {
      name: record.name,
      code: record.code,
      domain: record.domain,
      organizationId: record.organizationId,
      description: record.description,
      status: nextStatus
    })
    message.success(`站点已${nextStatus === 'enabled' ? '启用' : '禁用'}`)
    fetchSites()
  } catch (error: any) {
    message.error(error.response?.data?.message || '更新站点状态失败')
  }
}

const handleDelete = (record: Site) => {
  if (!ensurePermission('site:manage:delete', '删除站点')) {
    return
  }

  Modal.confirm({
    title: '删除站点',
    content: `确认删除站点“${record.name}”吗？此操作不可恢复。`,
    okText: '确认删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/sites/${record.id}`)
        message.success('站点删除成功')
        if (sites.value.length === 1 && pagination.value.current > 1) {
          pagination.value.current -= 1
        }
        fetchSites()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除站点失败')
      }
    }
  })
}

const getStatusClass = (status: string) => {
  return status === 'enabled' ? 'success' : 'default'
}

const getStatusText = (status: string) => {
  return status === 'enabled' ? '启用' : '禁用'
}

onMounted(() => {
  fetchSites()
})
</script>

<template>
  <div class="sites-page">
    <div class="page-header">
      <div class="header-left">
        <h1>站点管理</h1>
        <p>管理站点基础信息、所属组织和启停状态</p>
      </div>
      <button v-if="canCreateSite" class="primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增站点</span>
      </button>
    </div>

    <div class="toolbar">
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="搜索名称、编码或域名"
          class="search-input"
          @keyup.enter="handleSearch"
        />
      </div>
      <select v-model="filterStatus" class="filter-select" @change="handleSearch">
        <option value="">全部状态</option>
        <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <input
        v-model="filterOrganizationId"
        type="number"
        min="1"
        placeholder="组织 ID"
        class="filter-input"
        @keyup.enter="handleSearch"
      />
      <button class="secondary-btn" @click="handleSearch">搜索</button>
    </div>

    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>站点名称</th>
            <th>站点编码</th>
            <th>域名</th>
            <th>组织 ID</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && !sites.length">
            <td colspan="7" class="empty-state">暂无站点数据</td>
          </tr>
          <tr v-for="site in sites" :key="site.id">
            <td>
              <div class="site-name-cell">
                <strong>{{ site.name }}</strong>
                <span class="site-desc">{{ site.description || '—' }}</span>
              </div>
            </td>
            <td class="code-cell">{{ site.code }}</td>
            <td>{{ site.domain || '—' }}</td>
            <td>{{ site.organizationId ?? '—' }}</td>
            <td>
              <span class="status-badge" :class="getStatusClass(site.status)">
                {{ getStatusText(site.status) }}
              </span>
            </td>
            <td class="date-cell">{{ site.createdAt?.split('T')[0] || '—' }}</td>
            <td>
              <div class="action-btns">
                <button v-if="canUpdateSite" class="action-btn" @click="handleEdit(site)" title="编辑站点">
                  <EditOutlined />
                </button>
                <button v-if="canUpdateSite" class="action-btn" @click="handleToggleStatus(site)" :title="site.status === 'enabled' ? '禁用站点' : '启用站点'">
                  <PoweroffOutlined />
                </button>
                <button v-if="canDeleteSite" class="action-btn danger" @click="handleDelete(site)" title="删除站点">
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

    <div v-if="modalVisible" class="modal-overlay" @click.self="modalVisible = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑站点' : '新增站点' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-row two-columns">
            <div class="form-group">
              <label>站点名称</label>
              <input v-model="editingSite.name" type="text" placeholder="请输入站点名称" class="form-input" />
            </div>
            <div class="form-group">
              <label>站点编码</label>
              <input v-model="editingSite.code" type="text" placeholder="例如 gov-main" class="form-input" />
            </div>
          </div>
          <div class="form-row two-columns">
            <div class="form-group">
              <label>域名</label>
              <input v-model="editingSite.domain" type="text" placeholder="例如 www.example.gov.cn" class="form-input" />
            </div>
            <div class="form-group">
              <label>组织 ID</label>
              <input v-model="editingSite.organizationId" type="number" min="1" placeholder="可选" class="form-input" />
            </div>
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="editingSite.status" class="form-select">
              <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="editingSite.description" rows="4" placeholder="请输入站点说明" class="form-textarea"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary-btn" @click="modalVisible = false">取消</button>
          <button v-if="isEdit ? canUpdateSite : canCreateSite" class="primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sites-page {
  max-width: 1400px;
}

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

.primary-btn,
.secondary-btn,
.page-btn,
.action-btn,
.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.primary-btn {
  padding: 10px 18px;
  background: #2563eb;
  border: none;
  color: #fff;
  font-weight: 500;
}

.primary-btn:hover {
  background: #1d4ed8;
}

.secondary-btn,
.page-btn {
  padding: 10px 16px;
  background: #fff;
  border: 1px solid #cbd5e1;
  color: #334155;
}

.secondary-btn:hover,
.page-btn:hover,
.action-btn:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.secondary-btn:disabled,
.page-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.search-box {
  position: relative;
  min-width: 320px;
  flex: 1;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}

.search-input,
.filter-input,
.filter-select,
.form-input,
.form-select,
.form-textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 14px;
  color: #1e293b;
  background: #fff;
}

.search-input {
  padding: 10px 14px 10px 40px;
}

.filter-input,
.filter-select,
.form-input,
.form-select {
  padding: 10px 14px;
}

.filter-input {
  width: 160px;
}

.filter-select {
  width: 140px;
}

.form-textarea {
  padding: 12px 14px;
  resize: vertical;
}

.table-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  font-size: 14px;
  color: #334155;
  vertical-align: top;
}

.data-table th {
  background: #f8fafc;
  font-weight: 600;
  color: #475569;
}

.empty-state {
  text-align: center !important;
  color: #94a3b8 !important;
  padding: 48px 16px !important;
}

.site-name-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.site-name-cell strong {
  color: #0f172a;
}

.site-desc {
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.code-cell {
  font-family: Consolas, Monaco, monospace;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.success {
  background: #dcfce7;
  color: #166534;
}

.status-badge.default {
  background: #e2e8f0;
  color: #475569;
}

.date-cell {
  white-space: nowrap;
}

.action-btns {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  background: #fff;
  border: 1px solid #cbd5e1;
  color: #475569;
}

.action-btn.danger:hover {
  border-color: #dc2626;
  color: #dc2626;
}

.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
}

.pagination-total,
.page-info {
  color: #64748b;
  font-size: 14px;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: min(760px, calc(100vw - 32px));
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.18);
  overflow: hidden;
}

.modal-header,
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
}

.modal-footer {
  border-bottom: none;
  border-top: 1px solid #f1f5f9;
  justify-content: flex-end;
  gap: 12px;
}

.modal-header h3 {
  margin: 0;
  color: #0f172a;
}

.close-btn {
  width: 32px;
  height: 32px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #475569;
}

.modal-body {
  padding: 24px;
}

.form-row {
  display: grid;
  gap: 16px;
}

.form-row.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.form-group label {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

@media (max-width: 960px) {
  .page-header,
  .pagination {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .search-box {
    min-width: 100%;
  }

  .form-row.two-columns {
    grid-template-columns: 1fr;
  }

  .table-card {
    overflow-x: auto;
  }
}
</style>
