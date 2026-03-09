<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface User {
  id: number
  username: string
  email: string
  fullName?: string
  phone?: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

const { hasPermission } = usePermission()
const canCreateUser = hasPermission('sys:user:create')
const canUpdateUser = hasPermission('sys:user:update')
const canDeleteUser = hasPermission('sys:user:delete')
const canResetPassword = hasPermission('sys:user:reset-password')

const loading = ref(false)
const users = ref<User[]>([])
const searchKeyword = ref('')
const modalVisible = ref(false)
const isEdit = ref(false)
const editingUser = ref<Partial<User>>({})
const editingPassword = ref('')
const selectedUserIds = ref<number[]>([])
const batchLoading = ref(false)
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`??${actionName}??`)
  return false
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.value.current - 1,
      size: pagination.value.pageSize,
      keyword: searchKeyword.value || undefined
    }
    const res = await api.get('/users', { params })
    users.value = res.data.content || []
    selectedUserIds.value = selectedUserIds.value.filter(id => users.value.some(user => user.id === id))
    pagination.value.total = res.data.totalElements || 0
  } catch (error: any) {
    console.error('????????:', error)
    message.error(error.response?.data?.message || '????????')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.current = 1
  fetchUsers()
}

const handlePageChange = (page: number, pageSize: number) => {
  pagination.value.current = page
  pagination.value.pageSize = pageSize
  fetchUsers()
}

const handleAdd = () => {
  if (!ensurePermission('sys:user:create', '????')) {
    return
  }
  editingUser.value = { username: '', email: '', fullName: '', phone: '', enabled: true }
  editingPassword.value = ''
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = (record: User) => {
  if (!ensurePermission('sys:user:update', '????')) {
    return
  }
  editingUser.value = { ...record }
  editingPassword.value = ''
  isEdit.value = true
  modalVisible.value = true
}

const handleDelete = (id: number) => {
  if (!ensurePermission('sys:user:delete', '????')) {
    return
  }

  Modal.confirm({
    title: '????',
    content: '??????????????????',
    okText: '????',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/users/${id}`)
        message.success('????')
        fetchUsers()
      } catch (error: any) {
        message.error(error.response?.data?.message || '????')
      }
    }
  })
}

const handleSave = async () => {
  const requiredPermission = isEdit.value ? 'sys:user:update' : 'sys:user:create'
  const actionName = isEdit.value ? '????' : '????'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }

  if (!editingUser.value.username?.trim()) {
    message.error('??????')
    return
  }
  if (!editingUser.value.email?.trim()) {
    message.error('?????')
    return
  }

  try {
    if (isEdit.value) {
      await api.put(`/users/${editingUser.value.id}`, editingUser.value)
      message.success('????')
    } else {
      if (!editingPassword.value) {
        message.error('?????')
        return
      }
      await api.post('/users', { ...editingUser.value, password: editingPassword.value })
      message.success('????')
    }
    modalVisible.value = false
    fetchUsers()
  } catch (error: any) {
    message.error(error.response?.data?.message || '????')
  }
}

const handleResetPassword = (record: User) => {
  if (!ensurePermission('sys:user:reset-password', '????')) {
    return
  }

  Modal.confirm({
    title: '????',
    content: `????????${record.username}??????`,
    okText: '????',
    onOk: async () => {
      try {
        await api.post(`/users/${record.id}/reset-password`)
        message.success('??????: GovCMS@2026')
      } catch (error: any) {
        message.error(error.response?.data?.message || '??????')
      }
    }
  })
}

const allSelected = computed(() => users.value.length > 0 && selectedUserIds.value.length === users.value.length)

const indeterminate = computed(() => selectedUserIds.value.length > 0 && selectedUserIds.value.length < users.value.length)

const toggleSelectAll = (event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  selectedUserIds.value = checked ? users.value.map(user => user.id) : []
}

const toggleSelectUser = (id: number, event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    if (!selectedUserIds.value.includes(id)) {
      selectedUserIds.value.push(id)
    }
    return
  }
  selectedUserIds.value = selectedUserIds.value.filter(item => item !== id)
}

const handleBatchDisable = () => {
  if (!ensurePermission('sys:user:update', '????')) {
    return
  }

  const targetIds = users.value
    .filter(user => selectedUserIds.value.includes(user.id) && user.enabled)
    .map(user => user.id)

  if (!selectedUserIds.value.length) {
    message.warning('??????')
    return
  }

  if (!targetIds.length) {
    message.info('?????????????')
    return
  }

  Modal.confirm({
    title: '??????',
    content: `???????? ${targetIds.length} ?????`,
    okText: '????',
    okType: 'danger',
    onOk: async () => {
      batchLoading.value = true
      try {
        await Promise.all(targetIds.map(id => api.put(`/users/${id}`, { enabled: false })))
        message.success(`??? ${targetIds.length} ???`)
        selectedUserIds.value = []
        fetchUsers()
      } catch (error: any) {
        message.error(error.response?.data?.message || '??????')
      } finally {
        batchLoading.value = false
      }
    }
  })
}

const handleBatchDelete = () => {
  if (!ensurePermission('sys:user:delete', '????')) {
    return
  }

  if (!selectedUserIds.value.length) {
    message.warning('??????')
    return
  }

  Modal.confirm({
    title: '??????',
    content: `???????? ${selectedUserIds.value.length} ?????????????`,
    okText: '????',
    okType: 'danger',
    onOk: async () => {
      batchLoading.value = true
      try {
        const result = await Promise.allSettled(selectedUserIds.value.map(id => api.delete(`/users/${id}`)))
        const successCount = result.filter(item => item.status === 'fulfilled').length
        const failCount = result.length - successCount

        if (successCount > 0) {
          message.success(`??? ${successCount} ???`)
        }
        if (failCount > 0) {
          message.error(`${failCount} ???????????`)
        }

        selectedUserIds.value = []
        fetchUsers()
      } finally {
        batchLoading.value = false
      }
    }
  })
}

const getStatusClass = (enabled: boolean) => {
  return enabled ? 'success' : 'default'
}

const getStatusText = (enabled: boolean) => {
  return enabled ? '??' : '??'
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="users-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1>用户管理</h1>
        <p>管理系统用户账号和权限</p>
      </div>
      <button v-if="canCreateUser" class="primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>添加用户</span>
      </button>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input 
          v-model="searchKeyword"
          type="text" 
          placeholder="搜索用户名、邮箱..." 
          class="search-input"
          @keyup.enter="handleSearch"
        />
      </div>
      <button class="secondary-btn" @click="handleSearch">搜索</button>
      <button v-if="canUpdateUser" class="secondary-btn" :disabled="!selectedUserIds.length || batchLoading" @click="handleBatchDisable">
        <StopOutlined />
        批量禁用
      </button>
      <button v-if="canDeleteUser" class="secondary-btn danger-btn" :disabled="!selectedUserIds.length || batchLoading" @click="handleBatchDelete">
        <DeleteOutlined />
        批量删除
      </button>
      <span class="selected-count" v-if="selectedUserIds.length">
        已选择 {{ selectedUserIds.length }} 项
      </span>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input
                type="checkbox"
                :checked="allSelected"
                :indeterminate="indeterminate"
                @change="toggleSelectAll"
              />
            </th>
            <th>用户</th>
            <th>邮箱</th>
            <th>姓名</th>
            <th>手机</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td class="checkbox-col">
              <input
                type="checkbox"
                :checked="selectedUserIds.includes(user.id)"
                @change="toggleSelectUser(user.id, $event)"
              />
            </td>
            <td>
              <div class="user-cell">
                <div class="user-avatar">{{ user.username.charAt(0).toUpperCase() }}</div>
                <span class="username">{{ user.username }}</span>
              </div>
            </td>
            <td class="email-cell">{{ user.email }}</td>
            <td>{{ user.fullName || '-' }}</td>
            <td class="phone-cell">{{ user.phone || '-' }}</td>
            <td>
              <span class="status-badge" :class="getStatusClass(user.enabled)">
                {{ getStatusText(user.enabled) }}
              </span>
            </td>
            <td class="date-cell">{{ user.createdAt?.split('T')[0] || '-' }}</td>
            <td>
              <div class="action-btns">
                <button class="action-btn" v-if="canUpdateUser" @click="handleEdit(user)" title="编辑">
                  <EditOutlined />
                </button>
                <button class="action-btn" v-if="canResetPassword" @click="handleResetPassword(user)" title="重置密码">
                  <KeyOutlined />
                </button>
                <button class="action-btn danger" v-if="canDeleteUser" @click="handleDelete(user.id)" title="删除">
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
          <h3>{{ isEdit ? '编辑用户' : '添加用户' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>用户名</label>
            <input 
              v-model="editingUser.username"
              type="text" 
              placeholder="请输入用户名"
              class="form-input"
              :disabled="isEdit"
            />
          </div>
          <div class="form-group" v-if="!isEdit">
            <label>密码</label>
            <input 
              v-model="editingPassword"
              type="password" 
              placeholder="请输入密码"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input 
              v-model="editingUser.email"
              type="email" 
              placeholder="请输入邮箱"
              class="form-input"
            />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>姓名</label>
              <input 
                v-model="editingUser.fullName"
                type="text" 
                placeholder="请输入姓名"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label>手机</label>
              <input 
                v-model="editingUser.phone"
                type="tel" 
                placeholder="请输入手机号"
                class="form-input"
              />
            </div>
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="editingUser.enabled" class="form-select">
              <option :value="true">启用</option>
              <option :value="false">禁用</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary-btn" @click="modalVisible = false">取消</button>
          <button v-if="isEdit ? canUpdateUser : canCreateUser" class="primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.users-page {
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
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.selected-count {
  color: #64748b;
  font-size: 13px;
}

.danger-btn {
  color: #ef4444;
  border-color: #fecaca;
}

.danger-btn:hover:not(:disabled) {
  background: #fef2f2;
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

.checkbox-col {
  width: 44px;
  text-align: center !important;
}

.checkbox-col input {
  width: 16px;
  height: 16px;
  cursor: pointer;
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.username {
  font-weight: 500;
}

.email-cell,
.phone-cell {
  color: #64748b;
}

.date-cell {
  color: #64748b;
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

.status-badge.default {
  background: #f1f5f9;
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
  max-width: 480px;
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

.form-input:focus,
.form-select:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.form-input:disabled {
  background: #f8fafc;
  color: #94a3b8;
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
