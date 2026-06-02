<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DownOutlined, LockOutlined, PlusOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface Permission {
  id: string
  name: string
  code: string
  type: string
  parentId: string | null
  path?: string
  icon?: string
  sort: number
  children?: Permission[]
}

interface PermissionRow extends Permission {
  level: number
  hasChildren: boolean
}

const { hasPermission } = usePermission()
const canCreatePermission = hasPermission('sys:permission:create')
const canUpdatePermission = hasPermission('sys:permission:update')
const canDeletePermission = hasPermission('sys:permission:delete')

const loading = ref(false)
const permissions = ref<Permission[]>([])
const searchKeyword = ref('')
const expandedKeys = ref<string[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)
const editingPermission = ref<Partial<Permission>>({})
const permissionOptions = ref<any[]>([])

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`您没有${actionName}权限`)
  return false
}

const fetchPermissions = async () => {
  loading.value = true
  try {
    const res = await api.get('/permissions')
    permissions.value = res.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取权限列表失败')
  } finally {
    loading.value = false
  }
}

const fetchAllPermissions = async () => {
  try {
    const res = await api.get('/permissions/all')
    permissionOptions.value = convertToOptions(res.data || [])
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取全部权限失败')
  }
}

const convertToOptions = (items: Permission[], level = 0): any[] => {
  return items.map(item => ({
    value: item.id,
    label: '  '.repeat(level) + (level > 0 ? '└ ' : '') + item.name,
    children: item.children?.length ? convertToOptions(item.children, level + 1) : []
  }))
}

const filterTree = (items: Permission[]): Permission[] => {
  if (!searchKeyword.value.trim()) {
    return items
  }
  const keyword = searchKeyword.value.toLowerCase()
  return items.reduce<Permission[]>((result, item) => {
    const children = item.children ? filterTree(item.children) : []
    const matched = item.name.toLowerCase().includes(keyword) || item.code.toLowerCase().includes(keyword)
    if (matched || children.length) {
      result.push({ ...item, children })
    }
    return result
  }, [])
}

const flattenTree = (items: Permission[], level = 0): PermissionRow[] => {
  const rows: PermissionRow[] = []
  items.forEach(item => {
    const hasChildren = Boolean(item.children?.length)
    rows.push({ ...item, level, hasChildren })
    if (hasChildren && expandedKeys.value.includes(item.id)) {
      rows.push(...flattenTree(item.children || [], level + 1))
    }
  })
  return rows
}

const visibleRows = computed(() => flattenTree(filterTree(permissions.value)))
const totalPermissions = computed(() => {
  const stack = [...permissions.value]
  let total = 0
  while (stack.length) {
    const item = stack.shift()!
    total += 1
    if (item.children?.length) stack.push(...item.children)
  }
  return total
})
const menuCount = computed(() => permissions.value.flatMap(item => flattenTree([item])).filter(item => item.type === 'menu').length)
const buttonCount = computed(() => permissions.value.flatMap(item => flattenTree([item])).filter(item => item.type === 'button').length)
const apiCount = computed(() => permissions.value.flatMap(item => flattenTree([item])).filter(item => item.type === 'api').length)

const getTypeTag = (type: string) => {
  if (type === 'menu') return { text: '菜单', class: 'admin-chip' }
  if (type === 'button') return { text: '按钮', class: 'admin-chip admin-chip--success' }
  if (type === 'api') return { text: 'API', class: 'admin-chip admin-chip--warning' }
  return { text: type, class: 'admin-chip' }
}

const handleAdd = async () => {
  if (!ensurePermission('sys:permission:create', '新增权限')) {
    return
  }
  await fetchAllPermissions()
  editingPermission.value = { name: '', code: '', type: 'menu', parentId: null, path: '', icon: '', sort: 0 }
  isEdit.value = false
  modalVisible.value = true
}

const handleEdit = async (record: Permission) => {
  if (!ensurePermission('sys:permission:update', '编辑权限')) {
    return
  }
  await fetchAllPermissions()
  editingPermission.value = { ...record }
  isEdit.value = true
  modalVisible.value = true
}

const handleDelete = (id: string) => {
  if (!ensurePermission('sys:permission:delete', '删除权限')) {
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复该权限，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.delete(`/permissions/${id}`)
        message.success('删除成功')
        fetchPermissions()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除失败')
      }
    }
  })
}

const handleSave = async () => {
  const requiredPermission = isEdit.value ? 'sys:permission:update' : 'sys:permission:create'
  const actionName = isEdit.value ? '编辑权限' : '新增权限'
  if (!ensurePermission(requiredPermission, actionName)) {
    return
  }
  if (!editingPermission.value.name?.trim()) {
    message.error('请输入权限名称')
    return
  }
  if (!editingPermission.value.code?.trim()) {
    message.error('请输入权限编码')
    return
  }
  try {
    if (isEdit.value) {
      await api.put(`/permissions/${editingPermission.value.id}`, editingPermission.value)
      message.success('更新成功')
    } else {
      await api.post('/permissions', editingPermission.value)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchPermissions()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存失败')
  }
}

const toggleExpand = (key: string) => {
  const index = expandedKeys.value.indexOf(key)
  if (index > -1) {
    expandedKeys.value.splice(index, 1)
    return
  }
  expandedKeys.value.push(key)
}

const expandAll = () => {
  const keys: string[] = []
  const collectKeys = (items: Permission[]) => {
    items.forEach(item => {
      if (item.children?.length) {
        keys.push(item.id)
        collectKeys(item.children)
      }
    })
  }
  collectKeys(permissions.value)
  expandedKeys.value = keys
}

const collapseAll = () => {
  expandedKeys.value = []
}

onMounted(async () => {
  await fetchPermissions()
  expandAll()
})
</script>

<template>
  <div class="admin-page permissions-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">权限管理</h1>
        <p class="admin-page-desc">管理权限树、权限类型、父子关系和基础配置。</p>
      </div>
      <button v-if="canCreatePermission" class="admin-primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增权限</span>
      </button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <div class="admin-search-box">
          <SearchOutlined class="admin-search-icon" />
          <input v-model="searchKeyword" class="admin-search-input" placeholder="搜索权限名称或编码" />
        </div>
        <button class="admin-secondary-btn" @click="expandAll">
          <DownOutlined />
          <span>全部展开</span>
        </button>
        <button class="admin-secondary-btn" @click="collapseAll">
          <RightOutlined />
          <span>全部收起</span>
        </button>
      </div>
    </div>

    <div class="admin-grid-3 permissions-stats-grid">
      <div class="admin-metric-card"><div class="admin-sub-text">权限总数</div><div class="admin-metric-value">{{ totalPermissions }}</div></div>
      <div class="admin-metric-card"><div class="admin-sub-text">菜单权限</div><div class="admin-metric-value">{{ menuCount }}</div></div>
      <div class="admin-metric-card"><div class="admin-sub-text">按钮权限</div><div class="admin-metric-value">{{ buttonCount }}</div></div>
    </div>

    <div class="admin-card permissions-tree-card">
      <div class="admin-card-header">
        <h3 class="admin-card-title">权限树</h3>
        <span class="admin-card-meta">API 权限 {{ apiCount }} 项</span>
      </div>
      <table class="admin-data-table permissions-table">
        <thead>
          <tr>
            <th>权限名称</th>
            <th>权限编码</th>
            <th>类型</th>
            <th>路径</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="5" class="admin-empty-cell">加载中...</td></tr>
          <tr v-else-if="!visibleRows.length"><td colspan="5" class="admin-empty-cell">暂无权限数据</td></tr>
          <tr v-for="item in visibleRows" :key="item.id">
            <td>
              <div class="permission-tree-cell" :style="{ paddingLeft: `${item.level * 20}px` }">
                <button v-if="item.hasChildren" class="tree-toggle-btn" @click="toggleExpand(item.id)">
                  <DownOutlined v-if="expandedKeys.includes(item.id)" />
                  <RightOutlined v-else />
                </button>
                <span v-else class="tree-toggle-placeholder"></span>
                <LockOutlined class="permission-icon" />
                <span class="permission-name">{{ item.name }}</span>
              </div>
            </td>
            <td><code class="permission-code">{{ item.code }}</code></td>
            <td><span :class="getTypeTag(item.type).class">{{ getTypeTag(item.type).text }}</span></td>
            <td>{{ item.path || '-' }}</td>
            <td>
              <div class="admin-tree-actions">
                <button v-if="canUpdatePermission" class="admin-link-action" @click="handleEdit(item)">编辑</button>
                <button v-if="canDeletePermission" class="admin-link-action" @click="handleDelete(item.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-card permissions-info-card">
      <div class="admin-card-header">
        <h3 class="admin-card-title">权限说明</h3>
      </div>
      <ul class="info-list">
        <li><strong>菜单权限</strong>：控制页面访问，如用户管理、内容管理。</li>
        <li><strong>按钮权限</strong>：控制具体操作，如新增、编辑、删除。</li>
        <li><strong>API 权限</strong>：控制接口级访问能力。</li>
        <li>权限通过角色进行分配，请前往 <strong>角色管理</strong> 配置。</li>
      </ul>
    </div>

    <div class="admin-modal-overlay" v-if="modalVisible" @click.self="modalVisible = false">
      <div class="admin-modal-content">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑权限' : '新增权限' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">基础信息</h4></div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">权限名称 *</label>
                <input v-model="editingPermission.name" type="text" placeholder="请输入权限名称" class="admin-form-input" />
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">权限编码 *</label>
                <input v-model="editingPermission.code" type="text" placeholder="如：sys:user:create" class="admin-form-input" :disabled="isEdit" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">类型</label>
                <select v-model="editingPermission.type" class="admin-form-select">
                  <option value="menu">菜单</option>
                  <option value="button">按钮</option>
                  <option value="api">API</option>
                </select>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">层级信息</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">父级权限</label>
                <select v-model="editingPermission.parentId" class="admin-form-select">
                  <option :value="null">顶级权限</option>
                  <option v-for="opt in permissionOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">排序</label>
                <input v-model.number="editingPermission.sort" type="number" class="admin-form-input" />
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">扩展信息</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">路径</label>
                <input v-model="editingPermission.path" type="text" placeholder="/api/users" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">图标</label>
                <input v-model="editingPermission.icon" type="text" placeholder="UserOutlined" class="admin-form-input" />
              </div>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalVisible = false">取消</button>
          <button v-if="isEdit ? canUpdatePermission : canCreatePermission" class="admin-primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.permissions-page .permissions-tree-card,
.permissions-page .permissions-info-card {
  overflow: hidden;
}

.permissions-page .permission-tree-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permissions-page .tree-toggle-btn,
.permissions-page .tree-toggle-placeholder {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.permissions-page .tree-toggle-btn {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.permissions-page .permission-icon {
  color: #2563eb;
}

.permissions-page .permission-name {
  color: #0f172a;
  font-weight: 500;
}

.permissions-page .permission-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  color: #475569;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 6px;
}

.permissions-page .modal-section {
  padding: 16px;
}

.permissions-page .info-list {
  margin: 0;
  padding-left: 20px;
  color: #64748b;
  line-height: 22px;
}
</style>

