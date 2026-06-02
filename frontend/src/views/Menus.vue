<script setup lang="ts">
import '../styles/admin-refresh.css'

import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { FolderOpenOutlined, FolderOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface MenuItem {
  id: number
  name: string
  path?: string | null
  icon?: string | null
  permissionId?: string | null
  visible: boolean
  sortOrder: number
  parentId?: number | null
  children?: MenuItem[]
  _level?: number
}

interface MenuForm {
  id?: number
  name: string
  path?: string | null
  icon?: string | null
  permissionId?: string | null
  visible: boolean
  sortOrder: number
  parentId?: number | null
}

const { hasPermission } = usePermission()
const canCreateMenu = hasPermission('sys:menu:create')
const canUpdateMenu = hasPermission('sys:menu:update')
const canDeleteMenu = hasPermission('sys:menu:delete')

const loading = ref(false)
const modalVisible = ref(false)
const isEdit = ref(false)
const menus = ref<MenuItem[]>([])
const flatMenus = ref<MenuItem[]>([])
const permissionOptions = ref<any[]>([])
const form = ref<MenuForm>({ name: '', path: '', icon: '', permissionId: null, visible: true, sortOrder: 0, parentId: null })
const keyword = ref('')

const flattenMenus = (nodes: MenuItem[], level = 0): MenuItem[] => {
  const rows: MenuItem[] = []
  nodes.forEach(node => {
    rows.push({ ...node, _level: level })
    if (node.children?.length) {
      rows.push(...flattenMenus(node.children, level + 1))
    }
  })
  return rows
}

const fetchMenus = async () => {
  loading.value = true
  try {
    const response = await api.get('/menus')
    menus.value = response.data || []
    flatMenus.value = flattenMenus(menus.value).filter(item => !keyword.value.trim() || item.name.includes(keyword.value.trim()) || (item.path || '').includes(keyword.value.trim()))
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取菜单列表失败')
  } finally {
    loading.value = false
  }
}

const fetchPermissions = async () => {
  try {
    const response = await api.get('/permissions')
    permissionOptions.value = response.data || []
  } catch (error) {
    console.error('获取权限列表失败:', error)
  }
}

const openCreate = (parent?: MenuItem) => {
  form.value = {
    name: '',
    path: '',
    icon: '',
    permissionId: null,
    visible: true,
    sortOrder: 0,
    parentId: parent?.id ?? null
  }
  isEdit.value = false
  modalVisible.value = true
}

const openEdit = (record: MenuItem) => {
  form.value = {
    id: record.id,
    name: record.name,
    path: record.path || '',
    icon: record.icon || '',
    permissionId: record.permissionId || null,
    visible: record.visible,
    sortOrder: record.sortOrder,
    parentId: record.parentId ?? null
  }
  isEdit.value = true
  modalVisible.value = true
}

const handleSave = async () => {
  try {
    if (isEdit.value && form.value.id) {
      await api.put(`/menus/${form.value.id}`, form.value)
      message.success('菜单更新成功')
    } else {
      await api.post('/menus', form.value)
      message.success('菜单创建成功')
    }
    modalVisible.value = false
    await fetchMenus()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存菜单失败')
  }
}

const handleDelete = (record: MenuItem) => {
  if (!canDeleteMenu) {
    message.warning('暂无删除菜单权限')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定删除菜单“${record.name}”吗？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.delete(`/menus/${record.id}`)
        message.success('菜单删除成功')
        await fetchMenus()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除菜单失败')
      }
    }
  })
}

onMounted(async () => {
  await Promise.all([fetchMenus(), fetchPermissions()])
})
</script>

<template>
  <div class="admin-page menus-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">菜单管理</h1>
        <p class="admin-page-desc">管理菜单层级、图标、路径、显示状态与权限绑定。</p>
      </div>
      <button v-if="canCreateMenu" class="admin-primary-btn" @click="openCreate()">新建菜单</button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <div class="admin-search-box">
          <input v-model="keyword" class="admin-search-input" placeholder="搜索菜单名称或路径" @keyup.enter="fetchMenus" />
        </div>
        <button class="admin-secondary-btn" @click="fetchMenus">刷新</button>
      </div>
    </div>

    <div class="admin-table-card">
      <table class="admin-data-table">
        <thead>
          <tr>
            <th>菜单</th>
            <th>路径</th>
            <th>图标</th>
            <th>权限</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="6" class="admin-empty-cell">加载中...</td></tr>
          <tr v-else-if="!flatMenus.length"><td colspan="6" class="admin-empty-cell">暂无菜单数据</td></tr>
          <tr v-for="menu in flatMenus" :key="menu.id">
            <td>
              <div class="menu-tree-cell" :style="{ paddingLeft: `${(menu._level || 0) * 16}px` }">
                <span v-if="(menu._level || 0) > 0" class="admin-tree-prefix">├─</span>
                <component :is="menu.visible ? FolderOutlined : FolderOpenOutlined" class="menu-tree-icon" />
                <span class="menu-tree-name">{{ menu.name }}</span>
              </div>
            </td>
            <td>{{ menu.path || '-' }}</td>
            <td>{{ menu.icon || '-' }}</td>
            <td>{{ menu.permissionId || '-' }}</td>
            <td><span :class="['admin-status-badge', menu.visible ? 'admin-status-badge--success' : 'admin-status-badge--default']">{{ menu.visible ? '显示' : '隐藏' }}</span></td>
            <td>
              <div class="admin-tree-actions">
                <button v-if="canCreateMenu" class="admin-link-action" @click="openCreate(menu)">新增下级</button>
                <button v-if="canUpdateMenu" class="admin-link-action" @click="openEdit(menu)">编辑</button>
                <button v-if="canDeleteMenu" class="admin-link-action" @click="handleDelete(menu)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="modalVisible" class="admin-modal-overlay" @click.self="modalVisible = false">
      <div class="admin-modal-content">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑菜单' : '新增菜单' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">基础信息</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">菜单名称</label>
                <input v-model="form.name" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">路径</label>
                <input v-model="form.path" class="admin-form-input" />
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">图标</label>
                <input v-model="form.icon" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">排序</label>
                <input v-model.number="form.sortOrder" type="number" class="admin-form-input" />
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">层级信息</h4></div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">父级菜单</label>
                <select v-model="form.parentId" class="admin-form-select">
                  <option :value="null">作为顶级菜单</option>
                  <option v-for="item in flatMenus" :key="item.id" :value="item.id">{{ '—'.repeat(item._level || 0) }} {{ item.name }}</option>
                </select>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">权限关联</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">关联权限</label>
                <select v-model="form.permissionId" class="admin-form-select">
                  <option :value="null">不关联权限</option>
                  <option v-for="item in permissionOptions" :key="item.id" :value="item.id">{{ item.name }}（{{ item.code }}）</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">显示状态</label>
                <select v-model="form.visible" class="admin-form-select">
                  <option :value="true">显示</option>
                  <option :value="false">隐藏</option>
                </select>
              </div>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalVisible = false">取消</button>
          <button class="admin-primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.menus-page .menu-tree-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menus-page .menu-tree-icon {
  color: #64748b;
}

.menus-page .menu-tree-name {
  color: #0f172a;
  font-weight: 500;
}

.menus-page .modal-section {
  padding: 16px;
}
</style>
