<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined, PoweroffOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import { getRoles } from '../utils/session'
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
  updatedAt?: string | null
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
const isSiteAdmin = computed(() => getRoles().includes('site_admin'))

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

const currentSite = computed(() => sites.value[0] || null)

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
    if (isSiteAdmin.value && !hasPermission('site:manage:view')) {
      const res = await api.get('/sites/current')
      sites.value = [res.data]
      pagination.value.total = 1
      return
    }

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
    cancelText: '取消',
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

const getStatusText = (status: string) => status === 'enabled' ? '启用' : '禁用'
const formatDate = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 16) : '-'

onMounted(() => {
  fetchSites()
})
</script>

<template>
  <div class="admin-page sites-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">站点管理</h1>
        <p class="admin-page-desc">平台管理员管理站点列表，站点管理员在同页查看并维护本站点配置。</p>
      </div>
      <button v-if="canCreateSite && !isSiteAdmin" class="admin-primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增站点</span>
      </button>
    </div>

    <div v-if="isSiteAdmin && currentSite">
      <div class="sites-admin-grid">
        <div class="admin-card">
          <div class="admin-card-header">
            <h3 class="admin-card-title">站点摘要</h3>
          </div>
          <div class="site-summary-grid">
            <div class="site-summary-item"><label>站点名称</label><strong>{{ currentSite.name }}</strong></div>
            <div class="site-summary-item"><label>站点编码</label><strong>{{ currentSite.code }}</strong></div>
            <div class="site-summary-item"><label>域名</label><span>{{ currentSite.domain || '—' }}</span></div>
            <div class="site-summary-item"><label>组织 ID</label><span>{{ currentSite.organizationId ?? '—' }}</span></div>
            <div class="site-summary-item"><label>状态</label><span :class="['admin-status-badge', currentSite.status === 'enabled' ? 'admin-status-badge--success' : 'admin-status-badge--default']">{{ getStatusText(currentSite.status) }}</span></div>
            <div class="site-summary-item"><label>最近更新</label><span>{{ formatDate(currentSite.updatedAt || currentSite.createdAt) }}</span></div>
          </div>
        </div>

        <div class="admin-card">
          <div class="admin-card-header">
            <h3 class="admin-card-title">站点配置</h3>
            <button v-if="canUpdateSite" class="admin-secondary-btn" @click="handleEdit(currentSite)">
              <EditOutlined />
              <span>编辑配置</span>
            </button>
          </div>
          <div class="site-config-body">
            <div class="site-config-item">
              <label>站点描述</label>
              <p>{{ currentSite.description || '暂无站点描述' }}</p>
            </div>
            <div class="site-config-actions">
              <button v-if="canUpdateSite" class="admin-secondary-btn" @click="handleToggleStatus(currentSite)">
                <PoweroffOutlined />
                <span>{{ currentSite.status === 'enabled' ? '禁用站点' : '启用站点' }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else>
      <div class="admin-toolbar-card">
        <div class="admin-toolbar-row">
          <div class="admin-search-box">
            <SearchOutlined class="admin-search-icon" />
            <input v-model="searchKeyword" type="text" placeholder="搜索名称、编码或域名" class="admin-search-input" @keyup.enter="handleSearch" />
          </div>
          <select v-model="filterStatus" class="admin-filter-select" @change="handleSearch">
            <option value="">全部状态</option>
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <input v-model="filterOrganizationId" type="number" min="1" placeholder="组织 ID" class="admin-form-input org-filter-input" @keyup.enter="handleSearch" />
          <button class="admin-secondary-btn" @click="handleSearch">查询</button>
        </div>
      </div>

      <div class="admin-table-card">
        <table class="admin-data-table">
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
            <tr v-if="loading">
              <td colspan="7" class="admin-empty-cell">加载中...</td>
            </tr>
            <tr v-else-if="!sites.length">
              <td colspan="7" class="admin-empty-cell">暂无站点数据</td>
            </tr>
            <tr v-for="site in sites" :key="site.id">
              <td>
                <div class="site-name-cell">
                  <strong>{{ site.name }}</strong>
                  <span class="admin-sub-text">{{ site.description || '暂无描述' }}</span>
                </div>
              </td>
              <td class="code-cell">{{ site.code }}</td>
              <td>{{ site.domain || '—' }}</td>
              <td>{{ site.organizationId ?? '—' }}</td>
              <td>
                <span :class="['admin-status-badge', site.status === 'enabled' ? 'admin-status-badge--success' : 'admin-status-badge--default']">
                  {{ getStatusText(site.status) }}
                </span>
              </td>
              <td class="admin-muted-cell">{{ formatDate(site.createdAt) }}</td>
              <td>
                <div class="table-action-btns">
                  <button v-if="canUpdateSite" class="admin-icon-btn" @click="handleEdit(site)" title="编辑站点">
                    <EditOutlined />
                  </button>
                  <button v-if="canUpdateSite" class="admin-icon-btn" @click="handleToggleStatus(site)" :title="site.status === 'enabled' ? '禁用站点' : '启用站点'">
                    <PoweroffOutlined />
                  </button>
                  <button v-if="canDeleteSite" class="admin-icon-btn admin-icon-btn--danger" @click="handleDelete(site)" title="删除站点">
                    <DeleteOutlined />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="admin-pagination">
          <span class="admin-pagination-total">共 {{ pagination.total }} 条</span>
          <div class="admin-pagination-controls">
            <button class="admin-page-btn" :disabled="pagination.current === 1" @click="handlePageChange(pagination.current - 1, pagination.pageSize)">上一页</button>
            <span class="admin-page-info">{{ pagination.current }} / {{ Math.ceil(pagination.total / pagination.pageSize) || 1 }}</span>
            <button class="admin-page-btn" :disabled="pagination.current >= Math.ceil(pagination.total / pagination.pageSize)" @click="handlePageChange(pagination.current + 1, pagination.pageSize)">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="modalVisible" class="admin-modal-overlay" @click.self="modalVisible = false">
      <div class="admin-modal-content">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑站点' : '新增站点' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">站点名称</label>
              <input v-model="editingSite.name" type="text" class="admin-form-input" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">站点编码</label>
              <input v-model="editingSite.code" type="text" class="admin-form-input" />
            </div>
          </div>
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">域名</label>
              <input v-model="editingSite.domain" type="text" class="admin-form-input" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">组织 ID</label>
              <input v-model="editingSite.organizationId" type="number" class="admin-form-input" />
            </div>
          </div>
          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">站点描述</label>
              <textarea v-model="editingSite.description" rows="4" class="admin-form-textarea"></textarea>
            </div>
          </div>
          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">状态</label>
              <select v-model="editingSite.status" class="admin-form-select">
                <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalVisible = false">取消</button>
          <button class="admin-primary-btn" @click="handleSave">{{ isEdit ? '保存更新' : '创建站点' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sites-admin-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.site-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.site-summary-item,
.site-config-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.site-summary-item label,
.site-config-item label {
  font-size: 13px;
  line-height: 20px;
  color: #475569;
}

.site-config-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.site-config-item p {
  margin: 0;
  color: #0f172a;
  line-height: 22px;
}

.site-config-actions,
.table-action-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.site-name-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.site-name-cell strong,
.code-cell {
  color: #0f172a;
}

.org-filter-input {
  width: 160px;
}

@media (max-width: 1080px) {
  .sites-admin-grid,
  .site-summary-grid {
    grid-template-columns: 1fr;
  }

  .org-filter-input {
    width: 100%;
  }
}
</style>
