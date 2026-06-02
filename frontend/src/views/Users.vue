<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import api from '../utils/api'

interface RoleItem {
  id: number
  name: string
  code: string
}

interface UserItem {
  id: number
  username: string
  email: string
  fullName?: string | null
  phone?: string | null
  managedSiteId?: number | null
  enabled: boolean
  roles?: RoleItem[]
  createdAt?: string | null
  updatedAt?: string | null
}

interface UserFormState {
  id?: number
  username: string
  email: string
  fullName: string
  phone: string
  managedSiteId?: number | null
  enabled: boolean
}

const { hasPermission } = usePermission()
const canCreateUser = hasPermission('sys:user:create')
const canUpdateUser = hasPermission('sys:user:update')
const canDeleteUser = hasPermission('sys:user:delete')
const canResetPassword = hasPermission('sys:user:reset-password')

const loading = ref(false)
const batchLoading = ref(false)
const users = ref<UserItem[]>([])
const roleOptions = ref<RoleItem[]>([])
const siteOptions = ref<SiteOptionItem[]>([])
const selectedRoleIds = ref<number[]>([])
const selectedUserIds = ref<number[]>([])
const searchKeyword = ref('')
const modalVisible = ref(false)
const isEdit = ref(false)
const editingPassword = ref('')
const formState = ref<UserFormState>({
  username: '',
  email: '',
  fullName: '',
  phone: '',
  managedSiteId: null,
  enabled: true
})
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const selectedRoleCodes = computed(() => roleOptions.value
  .filter(role => selectedRoleIds.value.includes(role.id))
  .map(role => role.code))
const requiresManagedSite = computed(() => selectedRoleCodes.value.includes('site_admin'))
const isAllSelected = computed(() => users.value.length > 0 && users.value.every(user => selectedUserIds.value.includes(user.id)))
const hasSelection = computed(() => selectedUserIds.value.length > 0)

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`您没有${actionName}权限`)
  return false
}

const resolveSiteName = (siteId?: number | null) => siteOptions.value.find(site => site.id === siteId)?.name || '-'
const resolveRoleNames = (roles?: RoleItem[]) => roles?.map(role => role.name).join(' / ') || '-'
const formatDate = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 16) : '-'

const fetchUsers = async () => {
  loading.value = true
  try {
    const response = await api.get('/users', {
      params: {
        page: pagination.value.current - 1,
        size: pagination.value.pageSize,
        keyword: searchKeyword.value.trim() || undefined
      }
    })
    users.value = response.data.content || []
    pagination.value.total = response.data.totalElements || 0
    selectedUserIds.value = selectedUserIds.value.filter(id => users.value.some(user => user.id === id))
  } catch (error: any) {
    console.error('获取用户列表失败:', error)
    message.error(error.response?.data?.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const fetchRoles = async () => {
  try {
    const response = await api.get('/roles')
    roleOptions.value = response.data || []
  } catch (error) {
    console.error('获取角色列表失败:', error)
  }
}

const fetchSites = async () => {
  try {
    const response = await fetchSiteOptions()
    siteOptions.value = response.data || []
  } catch (error) {
    console.error('获取站点列表失败:', error)
  }
}

const resetForm = () => {
  formState.value = {
    username: '',
    email: '',
    fullName: '',
    phone: '',
    managedSiteId: null,
    enabled: true
  }
  selectedRoleIds.value = []
  editingPassword.value = ''
}

const handleSearch = () => {
  pagination.value.current = 1
  fetchUsers()
}

const handlePageChange = (page: number) => {
  pagination.value.current = page
  fetchUsers()
}

const handleToggleSelectAll = (checked: boolean) => {
  selectedUserIds.value = checked ? users.value.map(user => user.id) : []
}

const handleToggleSelect = (id: number, checked: boolean) => {
  if (checked) {
    selectedUserIds.value = Array.from(new Set([...selectedUserIds.value, id]))
    return
  }
  selectedUserIds.value = selectedUserIds.value.filter(item => item !== id)
}

const handleAdd = () => {
  if (!ensurePermission('sys:user:create', '创建用户')) {
    return
  }
  resetForm()
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = async (record: UserItem) => {
  if (!ensurePermission('sys:user:update', '编辑用户')) {
    return
  }
  formState.value = {
    id: record.id,
    username: record.username,
    email: record.email,
    fullName: record.fullName || '',
    phone: record.phone || '',
    managedSiteId: record.managedSiteId ?? null,
    enabled: record.enabled
  }
  editingPassword.value = ''
  try {
    const response = await api.get(`/users/${record.id}/roles`)
    selectedRoleIds.value = response.data || []
  } catch (error) {
    console.error('获取用户角色失败:', error)
    selectedRoleIds.value = record.roles?.map(role => role.id) || []
  }
  isEdit.value = true
  modalVisible.value = true
}

const handleResetPassword = (record: UserItem) => {
  if (!ensurePermission('sys:user:reset-password', '重置密码')) {
    return
  }
  Modal.confirm({
    title: '确认重置密码',
    content: `确定将用户“${record.username}”的密码重置为默认密码吗？`,
    okText: '确认重置',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.post(`/users/${record.id}/reset-password`)
        message.success('密码已重置为：GovCMS@2026')
      } catch (error: any) {
        message.error(error.response?.data?.message || '重置密码失败')
      }
    }
  })
}

const handleDelete = (record: UserItem) => {
  if (!ensurePermission('sys:user:delete', '删除用户')) {
    return
  }
  Modal.confirm({
    title: '确认删除用户',
    content: `删除后无法恢复，确定删除用户“${record.username}”吗？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.delete(`/users/${record.id}`)
        message.success('用户删除成功')
        await fetchUsers()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除用户失败')
      }
    }
  })
}

const validateForm = () => {
  if (!formState.value.username.trim()) {
    message.warning('请输入用户名')
    return false
  }
  if (!formState.value.email.trim()) {
    message.warning('请输入邮箱')
    return false
  }
  if (!isEdit.value && !editingPassword.value.trim()) {
    message.warning('请输入初始密码')
    return false
  }
  if (requiresManagedSite.value && !formState.value.managedSiteId) {
    message.warning('站点管理员必须绑定一个管理站点')
    return false
  }
  return true
}

const handleSubmit = async () => {
  if (!validateForm()) {
    return
  }

  const payload = {
    username: formState.value.username.trim(),
    email: formState.value.email.trim(),
    fullName: formState.value.fullName.trim() || null,
    phone: formState.value.phone.trim() || null,
    managedSiteId: requiresManagedSite.value ? formState.value.managedSiteId ?? null : null,
    enabled: formState.value.enabled
  }

  try {
    const response = isEdit.value
      ? await api.put(`/users/${formState.value.id}`, payload)
      : await api.post('/users', { ...payload, password: editingPassword.value })

    await api.put(`/users/${response.data.id}/roles`, selectedRoleIds.value)
    message.success(isEdit.value ? '用户更新成功' : '用户创建成功')
    modalVisible.value = false
    await fetchUsers()
  } catch (error: any) {
    message.error(error.response?.data?.message || (isEdit.value ? '更新用户失败' : '创建用户失败'))
  }
}

const handleBatchDisable = () => {
  if (!ensurePermission('sys:user:update', '批量禁用用户')) {
    return
  }
  if (!selectedUserIds.value.length) {
    message.warning('请先选择用户')
    return
  }
  Modal.confirm({
    title: '确认批量禁用',
    content: `确定禁用已选中的 ${selectedUserIds.value.length} 个用户吗？`,
    okText: '确认禁用',
    cancelText: '取消',
    onOk: async () => {
      batchLoading.value = true
      try {
        await Promise.all(selectedUserIds.value.map(id => api.put(`/users/${id}`, { enabled: false })))
        message.success(`已禁用 ${selectedUserIds.value.length} 个用户`)
        selectedUserIds.value = []
        await fetchUsers()
      } catch (error: any) {
        message.error(error.response?.data?.message || '批量禁用失败')
      } finally {
        batchLoading.value = false
      }
    }
  })
}

const handleBatchDelete = () => {
  if (!ensurePermission('sys:user:delete', '批量删除用户')) {
    return
  }
  if (!selectedUserIds.value.length) {
    message.warning('请先选择用户')
    return
  }
  Modal.confirm({
    title: '确认批量删除',
    content: `删除后无法恢复，确定删除已选中的 ${selectedUserIds.value.length} 个用户吗？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      batchLoading.value = true
      try {
        const results = await Promise.allSettled(selectedUserIds.value.map(id => api.delete(`/users/${id}`)))
        const successCount = results.filter(result => result.status === 'fulfilled').length
        const failureCount = results.length - successCount
        if (successCount) {
          message.success(`成功删除 ${successCount} 个用户`)
        }
        if (failureCount) {
          message.warning(`${failureCount} 个用户删除失败，请检查是否仍被引用或权限不足`)
        }
        selectedUserIds.value = []
        await fetchUsers()
      } finally {
        batchLoading.value = false
      }
    }
  })
}

watch(requiresManagedSite, value => {
  if (!value) {
    formState.value.managedSiteId = null
  }
})

onMounted(async () => {
  await Promise.all([fetchUsers(), fetchRoles(), fetchSites()])
})
</script>

<template>
  <div class="admin-page users-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">用户管理</h1>
        <p class="admin-page-desc">管理系统用户、角色分配、站点管理员绑定与基础账号操作。</p>
      </div>
      <button v-if="canCreateUser" class="admin-primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新建用户</span>
      </button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <div class="admin-search-box">
          <SearchOutlined class="admin-search-icon" />
          <input v-model="searchKeyword" class="admin-search-input" placeholder="按用户名、姓名或邮箱搜索" @keyup.enter="handleSearch" />
        </div>
        <button class="admin-secondary-btn" @click="handleSearch">查询</button>
        <button v-if="canUpdateUser" class="admin-secondary-btn" :disabled="!hasSelection || batchLoading" @click="handleBatchDisable">
          <StopOutlined />
          <span>批量禁用</span>
        </button>
        <button v-if="canDeleteUser" class="admin-danger-btn" :disabled="!hasSelection || batchLoading" @click="handleBatchDelete">
          <DeleteOutlined />
          <span>批量删除</span>
        </button>
      </div>
    </div>

    <div class="admin-table-card">
      <table class="admin-data-table users-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input type="checkbox" :checked="isAllSelected" @change="handleToggleSelectAll(($event.target as HTMLInputElement).checked)" />
            </th>
            <th>用户</th>
            <th>邮箱</th>
            <th>手机号</th>
            <th>角色</th>
            <th>管理站点</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="admin-empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="!users.length">
            <td colspan="9" class="admin-empty-cell">暂无用户数据</td>
          </tr>
          <tr v-for="user in users" :key="user.id">
            <td class="checkbox-col">
              <input type="checkbox" :checked="selectedUserIds.includes(user.id)" @change="handleToggleSelect(user.id, ($event.target as HTMLInputElement).checked)" />
            </td>
            <td>
              <div class="user-cell">
                <div class="user-avatar">{{ (user.fullName || user.username).slice(0, 1).toUpperCase() }}</div>
                <div>
                  <div class="username">{{ user.fullName || user.username }}</div>
                  <div class="admin-sub-text">@{{ user.username }}</div>
                </div>
              </div>
            </td>
            <td class="admin-muted-cell">{{ user.email || '-' }}</td>
            <td class="admin-muted-cell">{{ user.phone || '-' }}</td>
            <td>{{ resolveRoleNames(user.roles) }}</td>
            <td>{{ resolveSiteName(user.managedSiteId) }}</td>
            <td>
              <span :class="['admin-status-badge', user.enabled ? 'admin-status-badge--success' : 'admin-status-badge--default']">
                {{ user.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="admin-muted-cell">{{ formatDate(user.createdAt) }}</td>
            <td>
              <div class="action-btns">
                <button v-if="canUpdateUser" class="admin-icon-btn" title="编辑用户" @click="handleEdit(user)">
                  <EditOutlined />
                </button>
                <button v-if="canResetPassword" class="admin-icon-btn" title="重置密码" @click="handleResetPassword(user)">
                  <KeyOutlined />
                </button>
                <button v-if="canDeleteUser" class="admin-icon-btn admin-icon-btn--danger" title="删除用户" @click="handleDelete(user)">
                  <DeleteOutlined />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="admin-pagination">
        <div class="admin-pagination-total">共 {{ pagination.total }} 条</div>
        <div class="admin-pagination-controls">
          <button class="admin-page-btn" :disabled="pagination.current <= 1" @click="handlePageChange(pagination.current - 1)">上一页</button>
          <span class="admin-page-info">第 {{ pagination.current }} / {{ Math.max(1, Math.ceil(pagination.total / pagination.pageSize)) }} 页</span>
          <button class="admin-page-btn" :disabled="pagination.current >= Math.max(1, Math.ceil(pagination.total / pagination.pageSize))" @click="handlePageChange(pagination.current + 1)">下一页</button>
        </div>
      </div>
    </div>

    <div v-if="modalVisible" class="admin-modal-overlay" @click.self="modalVisible = false">
      <div class="admin-modal-content">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑用户' : '新建用户' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">用户名</label>
              <input v-model="formState.username" class="admin-form-input" placeholder="请输入用户名" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">邮箱</label>
              <input v-model="formState.email" class="admin-form-input" placeholder="请输入邮箱" />
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">姓名</label>
              <input v-model="formState.fullName" class="admin-form-input" placeholder="请输入姓名" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">手机号</label>
              <input v-model="formState.phone" class="admin-form-input" placeholder="请输入手机号" />
            </div>
          </div>

          <div v-if="!isEdit" class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">初始密码</label>
              <input v-model="editingPassword" type="password" class="admin-form-input" placeholder="请输入初始密码" />
            </div>
          </div>

          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">角色</label>
              <select v-model="selectedRoleIds" class="admin-form-select role-select" multiple>
                <option v-for="role in roleOptions" :key="role.id" :value="role.id">{{ role.name }}（{{ role.code }}）</option>
              </select>
              <div class="admin-field-tip">支持多角色；若勾选 `site_admin`，下方“管理站点”必须选择。</div>
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">管理站点</label>
              <select v-model="formState.managedSiteId" class="admin-form-select" :disabled="!requiresManagedSite">
                <option :value="null">请选择管理站点</option>
                <option v-for="site in siteOptions" :key="site.id" :value="site.id">{{ site.name }}</option>
              </select>
            </div>
            <div class="admin-form-group switch-group">
              <label class="admin-form-label">账号状态</label>
              <label class="switch-inline">
                <input v-model="formState.enabled" type="checkbox" />
                <span>{{ formState.enabled ? '启用' : '禁用' }}</span>
              </label>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalVisible = false">取消</button>
          <button class="admin-primary-btn" @click="handleSubmit">{{ isEdit ? '保存更新' : '创建用户' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.users-page .checkbox-col {
  width: 44px;
  text-align: center !important;
}

.users-page .checkbox-col input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.users-page .user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.users-page .user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
}

.users-page .username {
  font-weight: 600;
}

.users-page .action-btns {
  display: flex;
  gap: 8px;
}

.users-page .role-select {
  min-height: 120px;
  padding: 10px 14px;
}

.users-page .switch-group {
  justify-content: flex-end;
}

.users-page .switch-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
}

@media (max-width: 1080px) {
  .users-page .switch-group {
    justify-content: flex-start;
  }
}
</style>
