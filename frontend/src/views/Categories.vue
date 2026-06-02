<script setup lang="ts">
import '../styles/admin-refresh.css'
import { computed, onMounted, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined, SwapOutlined, PoweroffOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'
import {
  createCategory,
  deleteCategory,
  fetchCategories,
  fetchCategoryImpact,
  fetchCategoryTree,
  moveCategory,
  updateCategory,
  updateCategorySort,
  updateCategoryStatus,
  type CategoryPayload
} from '../api/categories'
import { fetchTemplates, type TemplateItem } from '../api/templates'

interface Site {
  id: number
  name: string
  code: string
  status: string
}

interface Category {
  id: number
  siteId: number
  parentId?: number | null
  name: string
  code: string
  type: string
  slug: string
  fullPath: string
  level: number
  sortOrder: number
  status: string
  navVisible: boolean
  breadcrumbVisible: boolean
  publicVisible: boolean
  listTemplateId?: number | null
  detailTemplateId?: number | null
  aggregationMode: string
  description?: string | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
  children?: Category[]
}

interface CategoryForm {
  id?: number
  siteId?: number
  parentId?: number | null
  name?: string
  code?: string
  type?: string
  slug?: string
  sortOrder?: number
  status?: string
  navVisible?: boolean
  breadcrumbVisible?: boolean
  publicVisible?: boolean
  listTemplateId?: number | null
  detailTemplateId?: number | null
  aggregationMode?: string
  description?: string | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
}

interface ImpactResponse {
  categoryId: number
  categoryName: string
  fullPath: string
  subtreeCount: number
  relatedArticleCount: number
  canDelete: boolean
  canMove: boolean
  impactedPaths: string[]
  warnings: string[]
}

interface CategoryOption {
  id: number
  name: string
  fullPath: string
  level: number
}

interface CategoryRow extends Category {
  displayLevel: number
}

interface TemplateOption {
  id: number
  name: string
  code: string
  type: string
  status: string
}

const { hasPermission } = usePermission()
const canCreateCategory = hasPermission('content:category:create')
const canUpdateCategory = hasPermission('content:category:update')
const canDeleteCategory = hasPermission('content:category:delete')

const loading = ref(false)
const sites = ref<Site[]>([])
const selectedSiteId = ref<number | null>(null)
const searchKeyword = ref('')
const filterStatus = ref('')
const treeData = ref<Category[]>([])
const allCategories = ref<Category[]>([])
const selectedCategory = ref<Category | null>(null)
const modalVisible = ref(false)
const moveModalVisible = ref(false)
const impactModalVisible = ref(false)
const isEdit = ref(false)
const editingCategory = ref<CategoryForm>({})
const movingCategory = ref<Category | null>(null)
const moveTargetParentId = ref<number | null>(null)
const impactData = ref<ImpactResponse | null>(null)
const templateOptions = ref<TemplateOption[]>([])

const categoryTypeOptions = [
  { value: 'channel', label: '常规栏目' },
  { value: 'single_page', label: '单页栏目' },
  { value: 'external_link', label: '外链栏目' }
]
void categoryTypeOptions

const statusOptions = [
  { value: 'enabled', label: '启用' },
  { value: 'disabled', label: '禁用' }
]

const aggregationOptions = [
  { value: 'manual', label: '仅本栏目内容' },
  { value: 'inherit_children', label: '包含子栏目内容' }
]

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`暂无${actionName}权限`)
  return false
}

const flattenTree = (nodes: Category[], level = 0): CategoryRow[] => {
  const rows: CategoryRow[] = []
  nodes.forEach(node => {
    rows.push({ ...node, displayLevel: level })
    if (node.children?.length) {
      rows.push(...flattenTree(node.children, level + 1))
    }
  })
  return rows
}

const treeRows = computed(() => flattenTree(treeData.value))
const listTemplateOptions = computed(() => templateOptions.value.filter(item => item.type === 'column_list'))
const detailTemplateOptions = computed(() => templateOptions.value.filter(item => item.type === 'content_detail'))

const parentOptions = computed<CategoryOption[]>(() => {
  const excludedIds = movingCategory.value ? getDescendantIds(movingCategory.value.id) : new Set<number>()
  if (movingCategory.value) {
    excludedIds.add(movingCategory.value.id)
  }
  return allCategories.value
    .filter(item => !excludedIds.has(item.id))
    .map(item => ({ id: item.id, name: item.name, fullPath: item.fullPath, level: item.level }))
})

const getDescendantIds = (categoryId: number) => {
  const ids = new Set<number>()
  const stack = allCategories.value.filter(item => item.parentId === categoryId)
  while (stack.length) {
    const current = stack.pop()
    if (!current) {
      continue
    }
    ids.add(current.id)
    stack.push(...allCategories.value.filter(item => item.parentId === current.id))
  }
  return ids
}

const fetchSites = async () => {
  try {
    const res = await api.get('/sites', {
      params: {
        page: 0,
        size: 100,
        status: 'enabled'
      }
    })
    sites.value = res.data.content || []
    if (!selectedSiteId.value && sites.value.length > 0) {
      selectedSiteId.value = sites.value[0].id
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取站点失败')
  }
}

const fetchTemplateOptions = async (siteId?: number | null) => {
  if (!siteId) {
    templateOptions.value = []
    return
  }
  try {
    const res = await fetchTemplates({ siteId, status: 'active' })
    templateOptions.value = (res.data || []).map((item: TemplateItem) => ({
      id: item.id,
      name: item.name,
      code: item.code,
      type: item.type,
      status: item.status
    }))
  } catch (error: any) {
    templateOptions.value = []
    message.error(error.response?.data?.message || '加载模板列表失败')
  }
}

const fetchTree = async () => {
  if (!selectedSiteId.value) {
    treeData.value = []
    allCategories.value = []
    selectedCategory.value = null
    return
  }
  loading.value = true
  try {
    const [treeRes, listRes] = await Promise.all([
      fetchCategoryTree({
        siteId: selectedSiteId.value,
        keyword: searchKeyword.value.trim() || undefined,
        status: filterStatus.value || undefined
      }),
      fetchCategories({ siteId: selectedSiteId.value })
    ])
    treeData.value = treeRes.data || []
    allCategories.value = listRes.data || []

    if (selectedCategory.value) {
      const current = allCategories.value.find(item => item.id === selectedCategory.value?.id)
      selectedCategory.value = current || null
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取栏目列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  fetchTree()
}

const handleSelect = (record: Category) => {
  selectedCategory.value = record
}

const openAddModal = async (parent?: Category | null) => {
  if (!ensurePermission('content:category:create', '新增栏目')) {
    return
  }
  if (!selectedSiteId.value) {
    message.warning('请先选择站点')
    return
  }
  editingCategory.value = {
    siteId: selectedSiteId.value,
    parentId: parent?.id ?? null,
    name: '',
    code: '',
    type: 'channel',
    slug: '',
    sortOrder: 0,
    status: 'enabled',
    navVisible: true,
    breadcrumbVisible: true,
    publicVisible: true,
    listTemplateId: null,
    detailTemplateId: null,
    aggregationMode: 'manual',
    description: '',
    seoTitle: '',
    seoKeywords: '',
    seoDescription: ''
  }
  await fetchTemplateOptions(editingCategory.value.siteId)
  isEdit.value = false
  modalVisible.value = true
}

const openEditModal = async (record: Category) => {
  if (!ensurePermission('content:category:update', '编辑栏目')) {
    return
  }
  editingCategory.value = {
    id: record.id,
    siteId: record.siteId,
    parentId: record.parentId ?? null,
    name: record.name,
    code: record.code,
    type: record.type,
    slug: record.slug,
    sortOrder: record.sortOrder,
    status: record.status,
    navVisible: record.navVisible,
    breadcrumbVisible: record.breadcrumbVisible,
    publicVisible: record.publicVisible,
    listTemplateId: record.listTemplateId ?? null,
    detailTemplateId: record.detailTemplateId ?? null,
    aggregationMode: record.aggregationMode,
    description: record.description ?? '',
    seoTitle: record.seoTitle ?? '',
    seoKeywords: record.seoKeywords ?? '',
    seoDescription: record.seoDescription ?? ''
  }
  await fetchTemplateOptions(editingCategory.value.siteId)
  isEdit.value = true
  modalVisible.value = true
}

const handleEditingSiteChange = async () => {
  editingCategory.value.parentId = null
  editingCategory.value.listTemplateId = null
  editingCategory.value.detailTemplateId = null
  await fetchTemplateOptions(editingCategory.value.siteId)
}
void handleEditingSiteChange

const buildPayload = (): CategoryPayload | null => {
  if (!editingCategory.value.siteId) {
    message.error('请选择站点')
    return null
  }
  const name = editingCategory.value.name?.trim()
  const code = editingCategory.value.code?.trim()
  const slug = editingCategory.value.slug?.trim()
  if (!name) {
    message.error('请输入栏目名称')
    return null
  }
  if (!code) {
    message.error('请输入栏目编码')
    return null
  }
  if (!slug) {
    message.error('请输入路径标识')
    return null
  }
  return {
    siteId: editingCategory.value.siteId,
    parentId: editingCategory.value.parentId ?? null,
    name,
    code,
    type: editingCategory.value.type || 'channel',
    slug,
    sortOrder: Number(editingCategory.value.sortOrder ?? 0),
    status: editingCategory.value.status || 'enabled',
    navVisible: editingCategory.value.navVisible ?? true,
    breadcrumbVisible: editingCategory.value.breadcrumbVisible ?? true,
    publicVisible: editingCategory.value.publicVisible ?? true,
    listTemplateId: editingCategory.value.listTemplateId ?? null,
    detailTemplateId: editingCategory.value.detailTemplateId ?? null,
    aggregationMode: editingCategory.value.aggregationMode || 'manual',
    description: editingCategory.value.description?.trim() || null,
    seoTitle: editingCategory.value.seoTitle?.trim() || null,
    seoKeywords: editingCategory.value.seoKeywords?.trim() || null,
    seoDescription: editingCategory.value.seoDescription?.trim() || null
  }
}

const handleSave = async () => {
  const permission = isEdit.value ? 'content:category:update' : 'content:category:create'
  const actionName = isEdit.value ? '编辑栏目' : '新增栏目'
  if (!ensurePermission(permission, actionName)) {
    return
  }
  const payload = buildPayload()
  if (!payload) {
    return
  }
  try {
    if (isEdit.value && editingCategory.value.id) {
      await updateCategory(editingCategory.value.id, payload)
      message.success('栏目更新成功')
    } else {
      await createCategory(payload)
      message.success('栏目创建成功')
    }
    modalVisible.value = false
    await fetchTree()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存栏目失败')
  }
}

const openMoveModal = (record: Category) => {
  if (!ensurePermission('content:category:update', '移动栏目')) {
    return
  }
  movingCategory.value = record
  moveTargetParentId.value = record.parentId ?? null
  moveModalVisible.value = true
}

const handleMove = async () => {
  if (!movingCategory.value || !selectedSiteId.value) {
    return
  }
  try {
    await moveCategory(movingCategory.value.id, {
      siteId: selectedSiteId.value,
      targetParentId: moveTargetParentId.value ?? null
    })
    message.success('栏目移动成功')
    moveModalVisible.value = false
    movingCategory.value = null
    await fetchTree()
  } catch (error: any) {
    message.error(error.response?.data?.message || '移动栏目失败')
  }
}

const handleUpdateSort = async (record: Category) => {
  if (!ensurePermission('content:category:update', '修改排序')) {
    return
  }
  if (!selectedSiteId.value) {
    return
  }
  try {
    await updateCategorySort(record.id, {
      siteId: selectedSiteId.value,
      sortOrder: Number(record.sortOrder || 0)
    })
    message.success('排序更新成功')
    await fetchTree()
  } catch (error: any) {
    message.error(error.response?.data?.message || '排序更新失败')
  }
}

const handleToggleStatus = async (record: Category) => {
  if (!ensurePermission('content:category:update', '修改状态')) {
    return
  }
  if (!selectedSiteId.value) {
    return
  }
  const nextStatus = record.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await updateCategoryStatus(record.id, {
      siteId: selectedSiteId.value,
      status: nextStatus
    })
    message.success(`栏目已${nextStatus === 'enabled' ? '启用' : '禁用'}`)
    await fetchTree()
  } catch (error: any) {
    message.error(error.response?.data?.message || '更新栏目状态失败')
  }
}

const openImpactModal = async (record: Category) => {
  if (!selectedSiteId.value) {
    return
  }
  try {
    const res = await fetchCategoryImpact(record.id, { siteId: selectedSiteId.value })
    impactData.value = res.data
    impactModalVisible.value = true
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取影响范围失败')
  }
}

const handleDelete = (record: Category) => {
  if (!ensurePermission('content:category:delete', '删除栏目')) {
    return
  }
  if (!selectedSiteId.value) {
    return
  }
  Modal.confirm({
    title: '删除栏目',
    content: `确认删除栏目“${record.name}”吗？`,
    okText: '确认删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteCategory(record.id, { siteId: selectedSiteId.value })
        message.success('栏目删除成功')
        if (selectedCategory.value?.id === record.id) {
          selectedCategory.value = null
        }
        await fetchTree()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除栏目失败')
      }
    }
  })
}

watch(selectedSiteId, async () => {
  selectedCategory.value = null
  await fetchTemplateOptions(selectedSiteId.value)
  await fetchTree()
})

onMounted(async () => {
  await fetchSites()
  await fetchTemplateOptions(selectedSiteId.value)
  await fetchTree()
})
</script>

<template>
  <div class="admin-page categories-page">
    <div class="admin-page-header">
      <div class="header-left">
        <h1 class="admin-page-title">栏目管理</h1>
        <p class="admin-page-desc">维护站点栏目树、路径、排序、模板绑定和基础发布影响信息。</p>
      </div>
      <button v-if="canCreateCategory" class="admin-primary-btn" @click="openAddModal()">
        <PlusOutlined />
        <span>新建栏目</span>
      </button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <select v-model="selectedSiteId" class="admin-filter-select">
          <option :value="null">请选择站点</option>
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <div class="admin-search-box">
          <SearchOutlined class="admin-search-icon" />
          <input v-model="searchKeyword" type="text" class="admin-search-input" placeholder="搜索栏目名称或编码" @keyup.enter="handleSearch" />
        </div>
        <select v-model="filterStatus" class="admin-filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <button class="admin-secondary-btn" @click="handleSearch">查询</button>
      </div>
    </div>

    <div class="categories-grid">
      <div class="admin-card tree-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">栏目树</h3>
          <span class="admin-card-meta">{{ loading ? '加载中...' : `${treeRows.length} 个节点` }}</span>
        </div>
        <div v-if="!selectedSiteId" class="admin-empty-state">请先选择站点</div>
        <div v-else-if="treeRows.length === 0" class="admin-empty-state">当前站点暂无栏目</div>
        <div v-else class="tree-list">
          <div
            v-for="row in treeRows"
            :key="row.id"
            class="tree-row"
            :class="{ active: selectedCategory?.id === row.id }"
            @click="handleSelect(row)"
          >
            <div class="tree-main" :style="{ paddingLeft: `${16 + row.displayLevel * 20}px` }">
              <span class="tree-name">{{ row.name }}</span>
              <span class="tree-code">{{ row.code }}</span>
            </div>
            <div class="tree-meta">
              <span :class="['admin-status-badge', row.status === 'enabled' ? 'admin-status-badge--success' : 'admin-status-badge--default']">{{ row.status === 'enabled' ? '启用' : '禁用' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="admin-card detail-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">栏目详情</h3>
        </div>
        <div v-if="!selectedCategory" class="admin-empty-state">请选择左侧栏目节点</div>
        <div v-else class="detail-body">
          <div class="detail-grid">
            <div class="detail-item"><label>栏目名称</label><span>{{ selectedCategory.name }}</span></div>
            <div class="detail-item"><label>栏目编码</label><span>{{ selectedCategory.code }}</span></div>
            <div class="detail-item"><label>栏目类型</label><span>{{ selectedCategory.type }}</span></div>
            <div class="detail-item"><label>路径</label><span>{{ selectedCategory.fullPath }}</span></div>
            <div class="detail-item"><label>排序</label><span>{{ selectedCategory.sortOrder }}</span></div>
            <div class="detail-item"><label>状态</label><span>{{ selectedCategory.status === 'enabled' ? '启用' : '禁用' }}</span></div>
            <div class="detail-item"><label>聚合方式</label><span>{{ selectedCategory.aggregationMode }}</span></div>
            <div class="detail-item"><label>对外可见</label><span>{{ selectedCategory.publicVisible ? '是' : '否' }}</span></div>
          </div>
          <div class="detail-description">
            <label>栏目描述</label>
            <p>{{ selectedCategory.description || '暂无描述' }}</p>
          </div>
          <div class="detail-actions">
            <button v-if="canCreateCategory" class="admin-secondary-btn" @click="openAddModal(selectedCategory)">
              <PlusOutlined />
              <span>新增子栏目</span>
            </button>
            <button v-if="canUpdateCategory" class="admin-secondary-btn" @click="openEditModal(selectedCategory)">
              <EditOutlined />
              <span>编辑</span>
            </button>
            <button v-if="canUpdateCategory" class="admin-secondary-btn" @click="openMoveModal(selectedCategory)">
              <SwapOutlined />
              <span>移动</span>
            </button>
            <button class="admin-secondary-btn" @click="openImpactModal(selectedCategory)">
              <EyeOutlined />
              <span>影响范围</span>
            </button>
            <button v-if="canUpdateCategory" class="admin-secondary-btn" @click="handleToggleStatus(selectedCategory)">
              <PoweroffOutlined />
              <span>{{ selectedCategory.status === 'enabled' ? '禁用' : '启用' }}</span>
            </button>
            <button v-if="canDeleteCategory" class="admin-danger-btn" @click="handleDelete(selectedCategory)">
              <DeleteOutlined />
              <span>删除</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="admin-table-card" v-if="treeRows.length > 0">
      <table class="admin-data-table">
        <thead>
          <tr>
            <th>栏目</th>
            <th>路径</th>
            <th>类型</th>
            <th>排序</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in treeRows" :key="`table-${row.id}`">
            <td>
              <span :style="{ paddingLeft: `${row.displayLevel * 16}px` }">{{ row.name }}</span>
            </td>
            <td>{{ row.fullPath }}</td>
            <td>{{ row.type }}</td>
            <td>
              <input v-model.number="row.sortOrder" type="number" class="admin-sort-input" @blur="handleUpdateSort(row)" />
            </td>
            <td>
              <span :class="['admin-status-badge', row.status === 'enabled' ? 'admin-status-badge--success' : 'admin-status-badge--default']">{{ row.status === 'enabled' ? '启用' : '禁用' }}</span>
            </td>
            <td>
              <div class="table-action-btns">
                <button class="admin-icon-btn" v-if="canUpdateCategory" @click="openEditModal(row)"><EditOutlined /></button>
                <button class="admin-icon-btn" v-if="canUpdateCategory" @click="openMoveModal(row)"><SwapOutlined /></button>
                <button class="admin-icon-btn" @click="openImpactModal(row)"><EyeOutlined /></button>
                <button class="admin-icon-btn admin-icon-btn--danger" v-if="canDeleteCategory" @click="handleDelete(row)"><DeleteOutlined /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-modal-overlay" v-if="modalVisible" @click.self="modalVisible = false">
      <div class="admin-modal-content admin-modal-content--large">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑栏目' : '新建栏目' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">站点</label>
              <select v-model="editingCategory.siteId" class="admin-form-select">
                <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
              </select>
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">父栏目</label>
              <select v-model="editingCategory.parentId" class="admin-form-select">
                <option :value="null">作为顶级栏目</option>
                <option v-for="item in parentOptions" :key="item.id" :value="item.id">
                  {{ '—'.repeat(Math.max(item.level - 1, 0)) }} {{ item.fullPath }}
                </option>
              </select>
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">栏目名称</label>
              <input v-model="editingCategory.name" type="text" class="admin-form-input" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">栏目编码</label>
              <input v-model="editingCategory.code" type="text" class="admin-form-input" />
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">栏目类型</label>
              <select v-model="editingCategory.type" class="admin-form-select">
                <option v-for="item in categoryTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">路径标识</label>
              <input v-model="editingCategory.slug" type="text" class="admin-form-input" />
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">排序</label>
              <input v-model.number="editingCategory.sortOrder" type="number" class="admin-form-input" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">状态</label>
              <select v-model="editingCategory.status" class="admin-form-select">
                <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header">
              <h4 class="admin-card-title">显示规则</h4>
            </div>
            <div class="checkbox-grid">
              <label class="check-item"><input v-model="editingCategory.navVisible" type="checkbox" /> <span>在导航中显示</span></label>
              <label class="check-item"><input v-model="editingCategory.breadcrumbVisible" type="checkbox" /> <span>启用面包屑</span></label>
              <label class="check-item"><input v-model="editingCategory.publicVisible" type="checkbox" /> <span>前台公开可见</span></label>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header">
              <h4 class="admin-card-title">模板配置</h4>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">聚合方式</label>
                <select v-model="editingCategory.aggregationMode" class="admin-form-select">
                  <option v-for="item in aggregationOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">列表模板</label>
                <select v-model="editingCategory.listTemplateId" class="admin-form-select">
                  <option :value="null">请选择</option>
                  <option v-for="item in listTemplateOptions" :key="item.id" :value="item.id">{{ item.name }} ({{ item.code }})</option>
                </select>
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">详情模板</label>
                <select v-model="editingCategory.detailTemplateId" class="admin-form-select">
                  <option :value="null">请选择</option>
                  <option v-for="item in detailTemplateOptions" :key="item.id" :value="item.id">{{ item.name }} ({{ item.code }})</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">模板说明</label>
                <div class="admin-field-tip template-hint">列表模板建议使用 `column_list`，详情模板建议使用 `content_detail`。</div>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header">
              <h4 class="admin-card-title">SEO 设置</h4>
            </div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">栏目描述</label>
                <textarea v-model="editingCategory.description" class="admin-form-textarea" rows="3" placeholder="请输入栏目描述"></textarea>
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">SEO 标题</label>
                <input v-model="editingCategory.seoTitle" type="text" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">SEO 关键词</label>
                <input v-model="editingCategory.seoKeywords" type="text" class="admin-form-input" />
              </div>
            </div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">SEO 描述</label>
                <textarea v-model="editingCategory.seoDescription" class="admin-form-textarea" rows="2"></textarea>
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

    <div class="admin-modal-overlay" v-if="moveModalVisible" @click.self="moveModalVisible = false">
      <div class="admin-modal-content">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">移动栏目</h3>
          <button class="admin-close-btn" @click="moveModalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">目标父栏目</label>
              <select v-model="moveTargetParentId" class="admin-form-select">
                <option :value="null">作为顶级栏目</option>
                <option v-for="item in parentOptions" :key="item.id" :value="item.id">
                  {{ '—'.repeat(Math.max(item.level - 1, 0)) }} {{ item.fullPath }}
                </option>
              </select>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="moveModalVisible = false">取消</button>
          <button class="admin-primary-btn" @click="handleMove">确认移动</button>
        </div>
      </div>
    </div>

    <div class="admin-modal-overlay" v-if="impactModalVisible" @click.self="impactModalVisible = false">
      <div class="admin-modal-content admin-modal-content--large">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">影响范围</h3>
          <button class="admin-close-btn" @click="impactModalVisible = false">×</button>
        </div>
        <div class="admin-modal-body" v-if="impactData">
          <div class="detail-grid detail-grid--single">
            <div class="detail-item"><label>栏目</label><span>{{ impactData.categoryName }}</span></div>
            <div class="detail-item"><label>路径</label><span>{{ impactData.fullPath }}</span></div>
            <div class="detail-item"><label>子树节点数</label><span>{{ impactData.subtreeCount }}</span></div>
            <div class="detail-item"><label>关联内容数</label><span>{{ impactData.relatedArticleCount }}</span></div>
          </div>
          <div class="detail-description">
            <label>路径样例</label>
            <ul class="impact-list">
              <li v-for="item in impactData.impactedPaths" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="detail-description">
            <label>告警提示</label>
            <ul class="impact-list">
              <li v-for="item in impactData.warnings" :key="item">{{ item }}</li>
              <li v-if="impactData.warnings.length === 0">当前无额外告警</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.categories-page .categories-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}

.categories-page .tree-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 560px;
  overflow-y: auto;
}

.categories-page .tree-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.categories-page .tree-row:hover,
.categories-page .tree-row.active {
  background: #eff6ff;
}

.categories-page .tree-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.categories-page .tree-name {
  font-weight: 600;
  color: #0f172a;
}

.categories-page .tree-code,
.categories-page .template-hint {
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.categories-page .detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.categories-page .detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.categories-page .detail-grid--single {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.categories-page .detail-item,
.categories-page .detail-description {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.categories-page .detail-item label,
.categories-page .detail-description label,
.categories-page .check-item {
  font-size: 13px;
  line-height: 20px;
  color: #475569;
}

.categories-page .detail-actions,
.categories-page .table-action-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.categories-page .checkbox-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.categories-page .check-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.categories-page .modal-section {
  padding: 16px;
}

.categories-page .impact-list {
  margin: 0;
  padding-left: 18px;
  color: #334155;
}

@media (max-width: 1080px) {
  .categories-page .categories-grid,
  .categories-page .detail-grid,
  .categories-page .detail-grid--single,
  .categories-page .checkbox-grid {
    grid-template-columns: 1fr;
  }
}
</style>
