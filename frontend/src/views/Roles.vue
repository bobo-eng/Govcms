<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal, Tree } from 'ant-design-vue'
import { CopyOutlined, DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface Permission {
  id: string
  name: string
  code: string
  type: string
  parentId: string | null
  children?: Permission[]
}

interface Role {
  id: number
  name: string
  code: string
  description: string
  status: string
  sort: number
  permissions?: Permission[]
}

const { hasPermission, hasAllPermissions } = usePermission()
const canCreateRole = hasPermission('sys:role:create')
const canUpdateRole = hasPermission('sys:role:update')
const canDeleteRole = hasPermission('sys:role:delete')
const canViewPermissionTree = hasPermission('sys:permission:view')
const canOpenCreateRole = hasAllPermissions(['sys:role:create', 'sys:permission:view'])
const canOpenEditRole = hasAllPermissions(['sys:role:update', 'sys:permission:view'])

const loading = ref(false)
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)
const editingRole = ref<Partial<Role>>({})
const selectedPermissions = ref<string[]>([])
const selectedRoleIds = ref<number[]>([])
const batchLoading = ref(false)
const treeData = ref<any[]>([])

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`??${actionName}??`)
  return false
}

const ensureAllPermissions = (permissionCodes: string[], actionName: string) => {
  if (hasAllPermissions(permissionCodes)) {
    return true
  }
  message.warning(`??${actionName}??`)
  return false
}

const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await api.get('/roles')
    roles.value = res.data || []
    selectedRoleIds.value = selectedRoleIds.value.filter(id => roles.value.some(role => role.id === id))
  } catch (error: any) {
    message.error(error.response?.data?.message || '????????')
  } finally {
    loading.value = false
  }
}

const convertToTreeData = (items: Permission[]): any[] => {
  return items.map(item => ({
    key: item.code,
    title: item.name,
    children: item.children?.length ? convertToTreeData(item.children) : [],
    disableCheckbox: item.type === 'menu'
  }))
}

const fetchPermissions = async () => {
  if (!canViewPermissionTree) {
    permissions.value = []
    treeData.value = []
    return
  }

  try {
    const res = await api.get('/permissions')
    permissions.value = res.data || []
    treeData.value = convertToTreeData(permissions.value)
  } catch (error: any) {
    message.error(error.response?.data?.message || '????????')
  }
}

const handleAdd = async () => {
  if (!ensureAllPermissions(['sys:role:create', 'sys:permission:view'], '????')) {
    return
  }

  await fetchPermissions()
  editingRole.value = { name: '', code: '', description: '', status: 'enabled', sort: 0 }
  selectedPermissions.value = []
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = async (record: Role) => {
  if (!ensureAllPermissions(['sys:role:update', 'sys:permission:view'], '????')) {
    return
  }

  await fetchPermissions()
  editingRole.value = { ...record }
  selectedPermissions.value = record.permissions?.map(permission => permission.code) || []
  isEdit.value = true
  modalVisible.value = true
}

const handleDelete = (id: number) => {
  if (!ensurePermission('sys:role:delete', '????')) {
    return
  }

  Modal.confirm({
    title: '????',
    content: '??????????',
    okText: '????',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/roles/${id}`)
        message.success('????')
        fetchRoles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '????')
      }
    }
  })
}

const generateCopyCode = (code: string) => {
  const suffix = `_copy_${Date.now().toString().slice(-6)}`
  const maxPrefixLength = Math.max(1, 50 - suffix.length)
  return `${code.slice(0, maxPrefixLength)}${suffix}`
}

const copyRole = async (role: Role) => {
  const payload = {
    name: `${role.name} - ??`,
    code: generateCopyCode(role.code),
    description: role.description,
    status: role.status || 'enabled',
    sort: role.sort || 0
  }

  const permissionIds = role.permissions?.map(permission => permission.id || permission.code) || []
  const createdRole = await api.post('/roles', payload)

  if (permissionIds.length && canUpdateRole) {
    await api.put(`/roles/${createdRole.data.id}/permissions`, permissionIds)
  }
}

const handleCopy = (record: Role) => {
  if (!ensurePermission('sys:role:create', '????')) {
    return
  }

  Modal.confirm({
    title: '????',
    content: `????????${record.name}???`,
    okText: '????',
    onOk: async () => {
      try {
        await copyRole(record)
        message.success(canUpdateRole ? '??????' : '??????????????')
        fetchRoles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '??????')
      }
    }
  })
}

const handleBatchCopy = () => {
  if (!ensurePermission('sys:role:create', '????')) {
    return
  }

  if (selectedRoleIds.value.length !== 1) {
    message.warning('???? 1 ???????')
    return
  }

  const targetRole = roles.value.find(role => role.id === selectedRoleIds.value[0])
  if (!targetRole) {
    message.error('????????')
    return
  }

  handleCopy(targetRole)
}

const handleBatchDelete = () => {
  if (!ensurePermission('sys:role:delete', '????')) {
    return
  }

  if (!selectedRoleIds.value.length) {
    message.warning('??????')
    return
  }

  Modal.confirm({
    title: '??????',
    content: `???????? ${selectedRoleIds.value.length} ?????`,
    okText: '????',
    okType: 'danger',
    onOk: async () => {
      batchLoading.value = true
      try {
        const result = await Promise.allSettled(selectedRoleIds.value.map(id => api.delete(`/roles/${id}`)))
        const successCount = result.filter(item => item.status === 'fulfilled').length
        const failCount = result.length - successCount

        if (successCount > 0) {
          message.success(`??? ${successCount} ???`)
        }
        if (failCount > 0) {
          message.error(`${failCount} ???????`)
        }

        selectedRoleIds.value = []
        fetchRoles()
      } finally {
        batchLoading.value = false
      }
    }
  })
}

const allSelected = computed(() => roles.value.length > 0 && selectedRoleIds.value.length === roles.value.length)

const toggleSelectAll = (event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  selectedRoleIds.value = checked ? roles.value.map(role => role.id) : []
}

const toggleSelectRole = (id: number, event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    if (!selectedRoleIds.value.includes(id)) {
      selectedRoleIds.value.push(id)
    }
    return
  }
  selectedRoleIds.value = selectedRoleIds.value.filter(item => item !== id)
}

const handleSave = async () => {
  const requiredPermission = isEdit.value ? 'sys:role:update' : 'sys:role:create'
  const actionName = isEdit.value ? '????' : '????'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }

  if (!editingRole.value.name?.trim()) {
    message.error('???????')
    return
  }
  if (!editingRole.value.code?.trim()) {
    message.error('???????')
    return
  }

  try {
    const payload = {
      ...editingRole.value
    }

    if (isEdit.value) {
      await api.put(`/roles/${editingRole.value.id}`, payload)
      await api.put(`/roles/${editingRole.value.id}/permissions`, selectedPermissions.value)
      message.success('????')
    } else {
      const createdRole = await api.post('/roles', payload)
      if (selectedPermissions.value.length && canUpdateRole) {
        await api.put(`/roles/${createdRole.data.id}/permissions`, selectedPermissions.value)
      }
      message.success('????')
      if (selectedPermissions.value.length && !canUpdateRole) {
        message.warning('??????????????????????')
      }
    }

    modalVisible.value = false
    fetchRoles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '????')
  }
}

type TreeCheckedKeys = Array<string | number> | { checked: Array<string | number>; halfChecked?: Array<string | number> }

const onCheck = (checkedKeys: TreeCheckedKeys) => {
  const values = Array.isArray(checkedKeys) ? checkedKeys : checkedKeys.checked
  selectedPermissions.value = values.map(String)
}

const getStatusClass = (status: string) => {
  return status === 'enabled' ? 'success' : 'default'
}

const getStatusText = (status: string) => {
  return status === 'enabled' ? '??' : '??'
}

onMounted(() => {
  fetchRoles()
})
</script>

<template>
  <div class="roles-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1>角色管理</h1>
        <p>管理系统角色和权限分配</p>
      </div>
      <button v-if="canOpenCreateRole" class="primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增角色</span>
      </button>
    </div>

    <div class="toolbar">
      <button v-if="canCreateRole" class="secondary-btn" :disabled="selectedRoleIds.length !== 1 || batchLoading" @click="handleBatchCopy">
        <CopyOutlined />
        复制所选角色
      </button>
      <button v-if="canDeleteRole" class="secondary-btn danger-btn" :disabled="!selectedRoleIds.length || batchLoading" @click="handleBatchDelete">
        <DeleteOutlined />
        批量删除
      </button>
      <span class="selected-count" v-if="selectedRoleIds.length">已选择 {{ selectedRoleIds.length }} 项</span>
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
                @change="toggleSelectAll"
              />
            </th>
            <th>角色名称</th>
            <th>角色编码</th>
            <th>描述</th>
            <th>权限数量</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="role in roles" :key="role.id">
            <td class="checkbox-col">
              <input
                type="checkbox"
                :checked="selectedRoleIds.includes(role.id)"
                @change="toggleSelectRole(role.id, $event)"
              />
            </td>
            <td>
              <div class="role-cell">
                <div class="role-avatar">
                  <KeyOutlined />
                </div>
                <span class="role-name">{{ role.name }}</span>
              </div>
            </td>
            <td>
              <code class="role-code">{{ role.code }}</code>
            </td>
            <td class="desc-cell">{{ role.description || '-' }}</td>
            <td>{{ role.permissions?.length || 0 }} 个权限</td>
            <td>
              <span class="status-badge" :class="getStatusClass(role.status)">
                {{ getStatusText(role.status) }}
              </span>
            </td>
            <td>
              <div class="action-btns">
                <button class="action-btn" v-if="canCreateRole" @click="handleCopy(role)" title="复制">
                  <CopyOutlined />
                </button>
                <button class="action-btn" v-if="canOpenEditRole" @click="handleEdit(role)" title="编辑">
                  <EditOutlined />
                </button>
                <button class="action-btn danger" v-if="canDeleteRole" @click="handleDelete(role.id)" title="删除">
                  <DeleteOutlined />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗 -->
    <div class="modal-overlay" v-if="modalVisible" @click.self="modalVisible = false">
      <div class="modal-content modal-large">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑角色' : '新增角色' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <div class="form-group">
              <label>角色名称</label>
              <input 
                v-model="editingRole.name"
                type="text" 
                placeholder="请输入角色名称"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label>角色编码</label>
              <input 
                v-model="editingRole.code"
                type="text" 
                placeholder="如: admin, editor"
                class="form-input"
                :disabled="isEdit"
              />
            </div>
          </div>
          <div class="form-group">
            <label>描述</label>
            <input 
              v-model="editingRole.description"
              type="text" 
              placeholder="请输入角色描述"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="editingRole.status" class="form-select">
              <option value="enabled">启用</option>
              <option value="disabled">禁用</option>
            </select>
          </div>
          
          <!-- 权限选择 -->
          <div class="form-group">
            <label>分配权限</label>
            <div class="permission-tree">
              <Tree
                v-if="treeData.length"
                v-model:checkedKeys="selectedPermissions"
                :treeData="treeData"
                checkable
                :default-expand-all="true"
                @check="onCheck"
              />
              <p v-else class="tree-loading">加载中...</p>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary-btn" @click="modalVisible = false">取消</button>
          <button v-if="isEdit ? canUpdateRole : canCreateRole" class="primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.roles-page {
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

.secondary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar {
  display: flex;
  align-items: center;
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

.role-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
}

.role-name {
  font-weight: 500;
}

.role-code {
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
  color: #64748b;
}

.desc-cell {
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

.modal-large {
  max-width: 640px;
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

/* 权限树 */
.permission-tree {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  max-height: 280px;
  overflow-y: auto;
}

.tree-loading {
  text-align: center;
  color: #94a3b8;
  padding: 20px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
}
</style>
