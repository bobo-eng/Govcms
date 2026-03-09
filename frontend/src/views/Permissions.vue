<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, FolderOutlined, FolderOpenOutlined, LockOutlined, EditOutlined, DeleteOutlined, DownOutlined, RightOutlined } from '@ant-design/icons-vue'
import axios from 'axios'

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

const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const loading = ref(false)
const permissions = ref<Permission[]>([])
const searchKeyword = ref('')
const expandedKeys = ref<string[]>([])  // 展开的节点

// CRUD 状态
const modalVisible = ref(false)
const isEdit = ref(false)
const editingPermission = ref<Partial<Permission>>({})

// 权限选项（用于父级选择）
const permissionOptions = ref<any[]>([])

const fetchPermissions = async () => {
  loading.value = true
  try {
    const res = await api.get('/permissions')
    permissions.value = res.data || []
  } catch (e: any) {
    message.error(e.response?.data?.message || '获取权限列表失败')
  } finally {
    loading.value = false
  }
}

// 获取所有权限用于父级选择
const fetchAllPermissions = async () => {
  try {
    const res = await api.get('/permissions/all')
    const allPerms = res.data || []
    // 转换为树形选择器格式
    permissionOptions.value = convertToOptions(allPerms)
  } catch (e: any) {
    message.error('获取权限列表失败')
  }
}

const convertToOptions = (perms: Permission[], level = 0): any[] => {
  return perms.map(p => ({
    value: p.id,
    label: '  '.repeat(level) + (level > 0 ? '├─ ' : '') + p.name,
    children: p.children?.length ? convertToOptions(p.children, level + 1) : []
  }))
}

// Filter permissions based on search
const filteredPermissions = () => {
  if (!searchKeyword.value) return permissions.value
  
  const keyword = searchKeyword.value.toLowerCase()
  return permissions.value.filter(p => 
    p.name.toLowerCase().includes(keyword) || 
    p.code.toLowerCase().includes(keyword)
  )
}

// Convert to tree data for display
const renderPermissionTree = (perms: Permission[], level = 0): any[] => {
  return perms.map(p => ({
    key: p.id,
    title: p.name,
    code: p.code,
    type: p.type,
    path: p.path,
    icon: p.icon,
    level,
    children: p.children?.length ? renderPermissionTree(p.children, level + 1) : []
  }))
}

const getTypeTag = (type: string) => {
  if (type === 'menu') return { text: '菜单', class: 'menu-tag' }
  if (type === 'button') return { text: '按钮', class: 'button-tag' }
  if (type === 'api') return { text: 'API', class: 'api-tag' }
  return { text: type, class: '' }
}

// 新增权限
const handleAdd = async () => {
  await fetchAllPermissions()
  editingPermission.value = { 
    name: '', 
    code: '', 
    type: 'menu', 
    parentId: null, 
    path: '', 
    icon: '', 
    sort: 0 
  }
  isEdit.value = false
  modalVisible.value = true
}

// 编辑权限
const handleEdit = async (record: Permission) => {
  await fetchAllPermissions()
  editingPermission.value = { ...record }
  isEdit.value = true
  modalVisible.value = true
}

// 删除权限
const handleDelete = (id: string) => {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除该权限吗？',
    okText: '确认删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await api.delete(`/permissions/${id}`)
        message.success('删除成功')
        fetchPermissions()
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

// 保存权限
const handleSave = async () => {
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
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

// 展开/折叠节点
const toggleExpand = (key: string) => {
  const idx = expandedKeys.value.indexOf(key)
  if (idx > -1) {
    expandedKeys.value.splice(idx, 1)
  } else {
    expandedKeys.value.push(key)
  }
}

// 递归展开所有节点
const expandAll = () => {
  const keys: string[] = []
  const collectKeys = (perms: Permission[]) => {
    perms.forEach(p => {
      if (p.children?.length) {
        keys.push(p.id)
        collectKeys(p.children)
      }
    })
  }
  collectKeys(permissions.value)
  expandedKeys.value = keys
}

// 折叠所有节点
const collapseAll = () => {
  expandedKeys.value = []
}

onMounted(() => { 
  fetchPermissions()
  expandAll()  // 默认展开所有
})
</script>

<template>
  <div class="permissions-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1>权限管理</h1>
        <p>系统权限配置和菜单结构</p>
      </div>
      <button class="primary-btn" @click="handleAdd">
        <PlusOutlined />
        <span>新增权限</span>
      </button>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input 
          v-model="searchKeyword"
          type="text" 
          placeholder="搜索权限名称或编码..." 
          class="search-input"
        />
      </div>
      <button class="secondary-btn" @click="expandAll">
        展开全部
      </button>
      <button class="secondary-btn" @click="collapseAll">
        折叠全部
      </button>
      <button class="secondary-btn" @click="fetchPermissions">
        刷新
      </button>
    </div>

    <!-- 权限统计 -->
    <div class="stats-row">
      <div class="stat-item">
        <span class="stat-value">{{ permissions.length }}</span>
        <span class="stat-label">权限总数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ permissions.filter(p => p.type === 'menu').length }}</span>
        <span class="stat-label">菜单权限</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ permissions.filter(p => p.type === 'button').length }}</span>
        <span class="stat-label">按钮权限</span>
      </div>
    </div>

    <!-- 权限树 -->
    <div class="tree-card">
      <div class="tree-header">
        <span class="col-name">权限名称</span>
        <span class="col-code">权限编码</span>
        <span class="col-type">类型</span>
        <span class="col-path">路径</span>
        <span class="col-actions">操作</span>
      </div>
      <div class="tree-body">
        <template v-for="perm in renderPermissionTree(filteredPermissions())" :key="perm.key">
          <div 
            class="tree-item"
            :style="{ paddingLeft: (perm.level * 24 + 20) + 'px' }"
          >
            <div class="tree-item-main">
              <!-- 展开/折叠按钮 -->
              <span 
                v-if="perm.children?.length" 
                class="expand-icon"
                @click="toggleExpand(perm.key)"
              >
                <DownOutlined v-if="expandedKeys.includes(perm.key)" />
                <RightOutlined v-else />
              </span>
              <span v-else class="expand-icon-placeholder"></span>
              
              <component 
                :is="perm.type === 'menu' ? FolderOutlined : LockOutlined" 
                class="item-icon"
                :class="{ 'is-menu': perm.type === 'menu' }"
              />
              <span class="item-name">{{ perm.title }}</span>
            </div>
            <span class="item-code">{{ perm.code }}</span>
            <span class="item-type">
              <span class="type-tag" :class="getTypeTag(perm.type).class">
                {{ getTypeTag(perm.type).text }}
              </span>
            </span>
            <span class="item-path">{{ perm.path || '-' }}</span>
            <div class="item-actions">
              <button class="action-btn" @click="handleEdit(perm)" title="编辑">
                <EditOutlined />
              </button>
              <button class="action-btn danger" @click="handleDelete(perm.key)" title="删除">
                <DeleteOutlined />
              </button>
            </div>
          </div>
          
          <!-- 递归显示子节点（根据展开状态） -->
          <template v-if="perm.children?.length && expandedKeys.includes(perm.key)">
            <div 
              v-for="child in perm.children" 
              :key="child.key"
              class="tree-item"
              :style="{ paddingLeft: ((child.level) * 24 + 20) + 'px' }"
            >
              <div class="tree-item-main">
                <span class="expand-icon-placeholder"></span>
                <component 
                  :is="child.type === 'menu' ? FolderOutlined : LockOutlined" 
                  class="item-icon"
                  :class="{ 'is-menu': child.type === 'menu' }"
                />
                <span class="item-name">{{ child.title }}</span>
              </div>
              <span class="item-code">{{ child.code }}</span>
              <span class="item-type">
                <span class="type-tag" :class="getTypeTag(child.type).class">
                  {{ getTypeTag(child.type).text }}
                </span>
              </span>
              <span class="item-path">{{ child.path || '-' }}</span>
              <div class="item-actions">
                <button class="action-btn" @click="handleEdit(child)" title="编辑">
                  <EditOutlined />
                </button>
                <button class="action-btn danger" @click="handleDelete(child.key)" title="删除">
                  <DeleteOutlined />
                </button>
              </div>
            </div>
          </template>
        </template>
        
        <div v-if="!permissions.length" class="empty-tip">
          <FolderOutlined class="empty-icon" />
          <p>暂无权限数据</p>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <div class="modal-overlay" v-if="modalVisible" @click.self="modalVisible = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑权限' : '新增权限' }}</h3>
          <button class="close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>权限名称 *</label>
            <input 
              v-model="editingPermission.name"
              type="text" 
              placeholder="请输入权限名称"
              class="form-input"
            />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>权限编码 *</label>
              <input 
                v-model="editingPermission.code"
                type="text" 
                placeholder="如: sys:user:add"
                class="form-input"
                :disabled="isEdit"
              />
            </div>
            <div class="form-group">
              <label>类型</label>
              <select v-model="editingPermission.type" class="form-select">
                <option value="menu">菜单</option>
                <option value="button">按钮</option>
                <option value="api">API</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>父级权限</label>
              <select v-model="editingPermission.parentId" class="form-select">
                <option :value="null">顶级权限</option>
                <option 
                  v-for="opt in permissionOptions" 
                  :key="opt.value" 
                  :value="opt.value"
                >
                  {{ opt.label }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>排序</label>
              <input 
                v-model.number="editingPermission.sort"
                type="number" 
                class="form-input"
              />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>路径</label>
              <input 
                v-model="editingPermission.path"
                type="text" 
                placeholder="/api/users"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label>图标</label>
              <input 
                v-model="editingPermission.icon"
                type="text" 
                placeholder="UserOutlined"
                class="form-input"
              />
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary-btn" @click="modalVisible = false">取消</button>
          <button class="primary-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>

    <!-- 说明 -->
    <div class="info-card">
      <h4>权限说明</h4>
      <ul>
        <li><strong>菜单权限</strong> - 控制页面访问，如用户管理、内容管理</li>
        <li><strong>按钮权限</strong> - 控制操作功能，如新增、编辑、删除</li>
        <li><strong>API权限</strong> - 控制接口访问</li>
        <li>权限通过角色进行分配，请前往 <strong>角色管理</strong> 配置</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.permissions-page {
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

/* 统计 */
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-item {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #2563eb;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}

/* 权限树 */
.tree-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}

.tree-header {
  display: grid;
  grid-template-columns: 2fr 1.5fr 100px 1.5fr 100px;
  gap: 16px;
  padding: 14px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
}

.tree-body {
  max-height: 500px;
  overflow-y: auto;
}

.tree-item {
  display: grid;
  grid-template-columns: 2fr 1.5fr 100px 1.5fr 100px;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid #f1f5f9;
  align-items: center;
  transition: background 0.15s;
}

.tree-item:hover {
  background: #f8fafc;
}

.tree-item:last-child {
  border-bottom: none;
}

.tree-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.expand-icon {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #64748b;
  font-size: 12px;
  transition: all 0.15s;
}

.expand-icon:hover {
  color: #2563eb;
}

.expand-icon-placeholder {
  width: 16px;
  height: 16px;
}

.item-icon {
  color: #94a3b8;
  font-size: 16px;
}

.item-icon.is-menu {
  color: #2563eb;
}

.item-name {
  font-weight: 500;
  color: #1e293b;
}

.item-code {
  font-family: monospace;
  font-size: 13px;
  color: #64748b;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 4px;
}

.item-type {
  display: flex;
}

.type-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.type-tag.menu-tag {
  background: #eff6ff;
  color: #2563eb;
}

.type-tag.button-tag {
  background: #f0fdf4;
  color: #16a34a;
}

.type-tag.api-tag {
  background: #fef3c7;
  color: #d97706;
}

.item-path {
  font-size: 13px;
  color: #94a3b8;
  font-family: monospace;
}

.item-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.action-btn.danger:hover {
  background: #fef2f2;
  color: #ef4444;
  border-color: #fecaca;
}

/* Empty */
.empty-tip {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

/* Modal */
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
  border-radius: 12px;
  width: 100%;
  max-width: 560px;
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
  color: #1e293b;
  margin: 0;
}

.close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  font-size: 24px;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 6px;
}

.close-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
}

.form-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.form-input:disabled {
  background: #f8fafc;
  color: #94a3b8;
}

.form-select {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  background: #fff;
  cursor: pointer;
}

.form-select:focus {
  border-color: #2563eb;
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

/* Info Card */
.info-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin-top: 24px;
}

.info-card h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 12px;
}

.info-card ul {
  margin: 0;
  padding-left: 20px;
}

.info-card li {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.info-card li:last-child {
  margin-bottom: 0;
}

.info-card strong {
  color: #2563eb;
}
</style>
