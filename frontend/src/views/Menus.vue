<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, FolderOpenOutlined, FolderOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface Menu {
  id: number
  name: string
  path: string
  icon: string
  parentId: number | null
  sort: number
  permissionId: string
  visible: boolean
  status: string
  children?: Menu[]
  _level?: number
}

interface Permission {
  id: string
  name: string
  code: string
}

const { hasPermission, hasAllPermissions } = usePermission()
const canCreateMenu = hasPermission('sys:menu:create')
const canUpdateMenu = hasPermission('sys:menu:update')
const canDeleteMenu = hasPermission('sys:menu:delete')
const canViewPermissionCatalog = hasPermission('sys:permission:view')
const canOpenCreateMenu = hasAllPermissions(['sys:menu:create', 'sys:permission:view'])
const canOpenEditMenu = hasAllPermissions(['sys:menu:update', 'sys:permission:view'])

const loading = ref(false)
const menus = ref<Menu[]>([])
const permissions = ref<Permission[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)
const editingMenu = ref<Partial<Menu>>({})

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

const flattenedMenus = computed(() => {
  const result: Menu[] = []
  const flatten = (items: Menu[], level = 0) => {
    items.forEach(item => {
      result.push({ ...item, _level: level })
      if (item.children?.length) {
        flatten(item.children, level + 1)
      }
    })
  }
  flatten(menus.value)
  return result
})

const fetchMenus = async () => {
  loading.value = true
  try {
    const res = await api.get('/menus')
    menus.value = res.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '????????')
  } finally {
    loading.value = false
  }
}

const fetchPermissions = async () => {
  if (!canViewPermissionCatalog) {
    permissions.value = []
    return
  }

  try {
    const res = await api.get('/permissions/all')
    permissions.value = res.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '????????')
  }
}

const handleAdd = async () => {
  if (!ensureAllPermissions(['sys:menu:create', 'sys:permission:view'], '????')) {
    return
  }

  await fetchPermissions()
  editingMenu.value = { name: '', path: '', icon: '', sort: 0, visible: true, status: 'enabled', permissionId: '' }
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = async (record: Menu) => {
  if (!ensureAllPermissions(['sys:menu:update', 'sys:permission:view'], '????')) {
    return
  }

  await fetchPermissions()
  editingMenu.value = { ...record }
  isEdit.value = true
  modalVisible.value = true
}

const handleDelete = (id: number) => {
  if (!ensurePermission('sys:menu:delete', '????')) {
    return
  }

  Modal.confirm({
    title: '????',
    content: '??????????',
    okText: '????',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/menus/${id}`)
        message.success('????')
        fetchMenus()
      } catch (error: any) {
        message.error(error.response?.data?.message || '????')
      }
    }
  })
}

const handleSave = async () => {
  const requiredPermission = isEdit.value ? 'sys:menu:update' : 'sys:menu:create'
  const actionName = isEdit.value ? '????' : '????'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }

  if (!editingMenu.value.name?.trim()) {
    message.error('???????')
    return
  }

  try {
    if (isEdit.value) {
      await api.put(`/menus/${editingMenu.value.id}`, editingMenu.value)
      message.success('????')
    } else {
      await api.post('/menus', editingMenu.value)
      message.success('????')
    }
    modalVisible.value = false
    fetchMenus()
  } catch (error: any) {
    message.error(error.response?.data?.message || '????')
  }
}

const getStatusClass = (status: string) => {
  return status === 'enabled' ? 'success' : 'default'
}

const getStatusText = (status: string) => {
  return status === 'enabled' ? '??' : '??'
}

const getVisibleText = (visible: boolean) => {
  return visible ? '??' : '??'
}

onMounted(() => {
  fetchMenus()
})
</script>

<template>
  <div class="menus-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1>菜单管理</h1>
        <p>管理系统菜单和权限关联</p>
      </div>
      <button v-if="canOpenCreateMenu" class="primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增菜单</span>
      </button>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>菜单名称</th>
            <th>路径</th>
            <th>图标</th>
            <th>关联权限</th>
            <th>排序</th>
            <th>显示</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="menu in flattenedMenus" :key="menu.id">
            <td>
              <div class="menu-cell" :style="{ paddingLeft: ((menu._level ?? 0) * 20) + 'px' }">
                <span v-if="(menu._level ?? 0) > 0" class="tree-prefix">├─ </span>
                <component :is="menu.visible ? FolderOutlined : FolderOpenOutlined" class="menu-icon" />
                <span class="menu-name">{{ menu.name }}</span>
              </div>
            </td>
            <td>
              <code class="path-code">{{ menu.path }}</code>
            </td>
            <td>{{ menu.icon || '-' }}</td>
            <td>
              <span class="perm-tag">{{ menu.permissionId || '-' }}</span>
            </td>
            <td>{{ menu.sort }}</td>
            <td>
              <span class="visible-tag" :class="menu.visible ? 'show' : 'hide'">
                {{ getVisibleText(menu.visible) }}
              </span>
            </td>
            <td>
              <span class="status-badge" :class="getStatusClass(menu.status)">
                {{ getStatusText(menu.status) }}
              </span>
            </td>
            <td>
              <div class="action-btns">
                <button class="action-btn" v-if="canOpenEditMenu" @click="handleEdit(menu)" title="编辑">
                  <EditOutlined />
                </button>
                <button class="action-btn danger" v-if="canDeleteMenu" @click="handleDelete(menu.id)" title="删除">
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
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑菜单' : '新增菜单' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>菜单名称</label>
            <input 
              v-model="editingMenu.name"
              type="text" 
              placeholder="请输入菜单名称"
              class="form-input"
            />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>路径</label>
              <input 
                v-model="editingMenu.path"
                type="text" 
                placeholder="/users"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label>图标</label>
              <input 
                v-model="editingMenu.icon"
                type="text" 
                placeholder="UserOutlined"
                class="form-input"
              />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>父级菜单</label>
              <select v-model="editingMenu.parentId" class="form-select">
                <option :value="0">顶级菜单</option>
                <option v-for="menu in menus" :key="menu.id" :value="menu.id">
                  {{ menu.name }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>排序</label>
              <input 
                v-model.number="editingMenu.sort"
                type="number" 
                placeholder="0"
                class="form-input"
              />
            </div>
          </div>
          <div class="form-group">
            <label>关联权限</label>
            <select v-model="editingMenu.permissionId" class="form-select">
              <option value="">无关联</option>
              <option v-for="perm in permissions" :key="perm.id" :value="perm.id">
                {{ perm.name }} ({{ perm.code }})
              </option>
            </select>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>是否显示</label>
              <select v-model="editingMenu.visible" class="form-select">
                <option :value="true">显示</option>
                <option :value="false">隐藏</option>
              </select>
            </div>
            <div class="form-group">
              <label>状态</label>
              <select v-model="editingMenu.status" class="form-select">
                <option value="enabled">启用</option>
                <option value="disabled">禁用</option>
              </select>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary-btn" @click="modalVisible = false">取消</button>
          <button v-if="isEdit ? canUpdateMenu : canCreateMenu" class="primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.menus-page {
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

.tree-prefix {
  color: #94a3b8;
  margin-right: 4px;
  font-weight: bold;
}

.menu-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.menu-icon {
  color: #2563eb;
  font-size: 16px;
}

.menu-name {
  font-weight: 500;
}

.path-code {
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
  color: #64748b;
}

.perm-tag {
  background: #eff6ff;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: #2563eb;
}

.visible-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
}

.visible-tag.show {
  background: #ecfdf5;
  color: #059669;
}

.visible-tag.hide {
  background: #f1f5f9;
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
  max-width: 520px;
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
