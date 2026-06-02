<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref } from 'vue'
import { message, Modal, Tree } from 'ant-design-vue'
import { CopyOutlined, DeleteOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons-vue'
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
  message.warning(`您没有${actionName}权限`)
  return false
}

const ensureAllPermissions = (permissionCodes: string[], actionName: string) => {
  if (hasAllPermissions(permissionCodes)) {
    return true
  }
  message.warning(`您没有${actionName}权限`)
  return false
}

const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await api.get('/roles')
    roles.value = res.data || []
    selectedRoleIds.value = selectedRoleIds.value.filter(id => roles.value.some(role => role.id === id))
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取角色列表失败')
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
    message.error(error.response?.data?.message || '获取权限列表失败')
  }
}

const handleAdd = async () => {
  if (!ensureAllPermissions(['sys:role:create', 'sys:permission:view'], '新增角色')) {
    return
  }

  await fetchPermissions()
  editingRole.value = { name: '', code: '', description: '', status: 'enabled', sort: 0 }
  selectedPermissions.value = []
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = async (record: Role) => {
  if (!ensureAllPermissions(['sys:role:update', 'sys:permission:view'], '编辑角色')) {
    return
  }

  await fetchPermissions()
  editingRole.value = { ...record }
  selectedPermissions.value = record.permissions?.map(permission => permission.code) || []
  isEdit.value = true
  modalVisible.value = true
}

const handleDelete = (id: number) => {
  if (!ensurePermission('sys:role:delete', '删除角色')) {
    return
  }

  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复该角色，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.delete(`/roles/${id}`)
        message.success('删除成功')
        fetchRoles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除失败')
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
    name: `${role.name} - 副本`,
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

const handleCopy = async (record: Role) => {
  if (!ensurePermission('sys:role:create', '复制角色')) {
    return
  }
  try {
    await copyRole(record)
    message.success('复制成功')
    fetchRoles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '复制失败')
  }
}

const handleBatchCopy = () => {
  if (!ensurePermission('sys:role:create', '批量复制')) {
    return
  }
  if (selectedRoleIds.value.length !== 1) {
    message.warning('请选择 1 个角色进行复制')
    return
  }
  const targetRole = roles.value.find(role => role.id === selectedRoleIds.value[0])
  if (!targetRole) {
    message.warning('未找到目标角色')
    return
  }
  handleCopy(targetRole)
}

const handleBatchDelete = () => {
  if (!ensurePermission('sys:role:delete', '批量删除角色')) {
    return
  }
  if (!selectedRoleIds.value.length) {
    message.warning('请选择角色')
    return
  }
  Modal.confirm({
    title: '批量删除角色',
    content: `确认删除选中的 ${selectedRoleIds.value.length} 个角色？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      batchLoading.value = true
      try {
        const result = await Promise.allSettled(selectedRoleIds.value.map(id => api.delete(`/roles/${id}`)))
        const successCount = result.filter(item => item.status === 'fulfilled').length
        const failCount = result.length - successCount
        if (successCount > 0) {
          message.success(`已删除 ${successCount} 个角色`)
        }
        if (failCount > 0) {
          message.error(`${failCount} 个角色删除失败`)
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
  const actionName = isEdit.value ? '编辑角色' : '新增角色'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }

  if (!editingRole.value.name?.trim()) {
    message.error('请输入角色名称')
    return
  }
  if (!editingRole.value.code?.trim()) {
    message.error('请输入角色编码')
    return
  }

  try {
    const payload = {
      ...editingRole.value
    }

    if (isEdit.value) {
      await api.put(`/roles/${editingRole.value.id}`, payload)
      await api.put(`/roles/${editingRole.value.id}/permissions`, selectedPermissions.value)
      message.success('更新成功')
    } else {
      const createdRole = await api.post('/roles', payload)
      if (selectedPermissions.value.length && canUpdateRole) {
        await api.put(`/roles/${createdRole.data.id}/permissions`, selectedPermissions.value)
      }
      message.success('创建成功')
      if (selectedPermissions.value.length && !canUpdateRole) {
        message.warning('角色已创建，但当前账号没有分配权限能力')
      }
    }

    modalVisible.value = false
    fetchRoles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存失败')
  }
}

type TreeCheckedKeys = Array<string | number> | { checked: Array<string | number>; halfChecked?: Array<string | number> }

const onCheck = (checkedKeys: TreeCheckedKeys) => {
  const values = Array.isArray(checkedKeys) ? checkedKeys : checkedKeys.checked
  selectedPermissions.value = values.map(String)
}

const getStatusClass = (status: string) => status === 'enabled' ? 'admin-status-badge--success' : 'admin-status-badge--default'
const getStatusText = (status: string) => status === 'enabled' ? '启用' : '禁用'

onMounted(() => {
  fetchRoles()
})
</script>

<template>
  <div class="admin-page roles-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">角色管理</h1>
        <p class="admin-page-desc">管理系统角色、权限分配和角色复制能力。</p>
      </div>
      <button v-if="canOpenCreateRole" class="admin-primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增角色</span>
      </button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <button v-if="canCreateRole" class="admin-secondary-btn" :disabled="selectedRoleIds.length !== 1 || batchLoading" @click="handleBatchCopy">
          <CopyOutlined />
          <span>复制所选角色</span>
        </button>
        <button v-if="canDeleteRole" class="admin-danger-btn" :disabled="!selectedRoleIds.length || batchLoading" @click="handleBatchDelete">
          <DeleteOutlined />
          <span>批量删除</span>
        </button>
        <span class="admin-sub-text" v-if="selectedRoleIds.length">已选择 {{ selectedRoleIds.length }} 项</span>
      </div>
    </div>

    <div class="admin-table-card">
      <table class="admin-data-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
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
          <tr v-if="loading">
            <td colspan="7" class="admin-empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="!roles.length">
            <td colspan="7" class="admin-empty-cell">暂无角色数据</td>
          </tr>
          <tr v-for="role in roles" :key="role.id">
            <td class="checkbox-col">
              <input type="checkbox" :checked="selectedRoleIds.includes(role.id)" @change="toggleSelectRole(role.id, $event)" />
            </td>
            <td>
              <div class="role-cell">
                <div class="role-avatar">
                  <KeyOutlined />
                </div>
                <span class="role-name">{{ role.name }}</span>
              </div>
            </td>
            <td><code class="role-code">{{ role.code }}</code></td>
            <td class="admin-muted-cell">{{ role.description || '-' }}</td>
            <td>{{ role.permissions?.length || 0 }} 个权限</td>
            <td><span :class="['admin-status-badge', getStatusClass(role.status)]">{{ getStatusText(role.status) }}</span></td>
            <td>
              <div class="admin-tree-actions">
                <button class="admin-link-action" v-if="canCreateRole" @click="handleCopy(role)">复制</button>
                <button class="admin-link-action" v-if="canOpenEditRole" @click="handleEdit(role)">编辑</button>
                <button class="admin-link-action" v-if="canDeleteRole" @click="handleDelete(role.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="modalVisible" class="admin-modal-overlay" @click.self="modalVisible = false">
      <div class="admin-modal-content admin-modal-content--large">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑角色' : '新增角色' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">基础信息</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">角色名称</label>
                <input v-model="editingRole.name" type="text" placeholder="请输入角色名称" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">角色编码</label>
                <input v-model="editingRole.code" type="text" placeholder="如：admin, editor" class="admin-form-input" :disabled="isEdit" />
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">角色描述</label>
                <input v-model="editingRole.description" type="text" placeholder="请输入角色描述" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">排序</label>
                <input v-model.number="editingRole.sort" type="number" class="admin-form-input" />
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">权限分配</h4></div>
            <div v-if="canViewPermissionTree" class="role-tree-box">
              <Tree
                checkable
                block-node
                :tree-data="treeData"
                :checked-keys="selectedPermissions"
                @check="onCheck"
              />
            </div>
            <div v-else class="admin-empty-state">当前账号无权限查看权限树。</div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalVisible = false">取消</button>
          <button v-if="isEdit ? canUpdateRole : canCreateRole" class="admin-primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.roles-page .checkbox-col {
  width: 44px;
  text-align: center !important;
}

.roles-page .checkbox-col input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.roles-page .role-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.roles-page .role-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.roles-page .role-name {
  font-weight: 600;
}

.roles-page .role-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  color: #475569;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 6px;
}

.roles-page .modal-section {
  padding: 16px;
}

.roles-page .role-tree-box {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
}
</style>

