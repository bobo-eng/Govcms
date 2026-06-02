<script setup lang="ts">
import '../styles/admin-refresh.css'

import { onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import { fetchCategories } from '../api/categories'
import { fetchTopics } from '../api/topics'
import {
  createNavigationItem,
  deleteNavigationItem,
  fetchNavigationItems,
  type NavigationItemData,
  type NavigationPayload,
  updateNavigationItem,
  updateNavigationSort
} from '../api/navigation'

interface OptionItem { value: number; label: string }

const loading = ref(false)
const modalOpen = ref(false)
const isEdit = ref(false)
const sites = ref<SiteOptionItem[]>([])
const items = ref<NavigationItemData[]>([])
const parentOptions = ref<OptionItem[]>([])
const targetOptions = ref<OptionItem[]>([])
const filters = ref({ siteId: undefined as number | undefined, keyword: '', status: '' })
const form = ref<NavigationPayload>({ siteId: 0, name: '', code: '', targetType: 'category', status: 'enabled', primaryNav: true, breadcrumbEnabled: true, sortOrder: 0 })

const loadSites = async () => {
  const res = await fetchSiteOptions()
  sites.value = res.data || []
  if (!filters.value.siteId && sites.value.length) filters.value.siteId = sites.value[0].id
}

const loadItems = async () => {
  if (!filters.value.siteId) return
  loading.value = true
  try {
    const res = await fetchNavigationItems({ siteId: filters.value.siteId, keyword: filters.value.keyword || undefined, status: filters.value.status || undefined })
    items.value = res.data || []
    parentOptions.value = items.value.map(item => ({ value: item.id, label: item.name }))
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取导航列表失败')
  } finally {
    loading.value = false
  }
}

const loadTargets = async () => {
  if (!form.value.siteId) return
  if (form.value.targetType === 'category') {
    const res = await fetchCategories({ siteId: form.value.siteId })
    targetOptions.value = (res.data || []).map((item: any) => ({ value: item.id, label: item.name }))
  } else if (form.value.targetType === 'topic') {
    const res = await fetchTopics({ siteId: form.value.siteId })
    targetOptions.value = (res.data || []).map((item: any) => ({ value: item.id, label: item.name }))
  } else {
    targetOptions.value = []
  }
}

const openCreate = async () => {
  form.value = { siteId: filters.value.siteId || 0, name: '', code: '', targetType: 'category', status: 'enabled', primaryNav: true, breadcrumbEnabled: true, sortOrder: 0 }
  isEdit.value = false
  await loadTargets()
  modalOpen.value = true
}

const openEdit = async (item: NavigationItemData) => {
  form.value = { ...item }
  isEdit.value = true
  await loadTargets()
  modalOpen.value = true
}

const save = async () => {
  try {
    if (isEdit.value && (form.value as any).id) {
      await updateNavigationItem((form.value as any).id, form.value)
      message.success('导航更新成功')
    } else {
      await createNavigationItem(form.value)
      message.success('导航创建成功')
    }
    modalOpen.value = false
    await loadItems()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存导航失败')
  }
}

const removeItem = async (item: NavigationItemData) => {
  if (!window.confirm(`确认删除导航《${item.name}》吗？`)) return
  try {
    await deleteNavigationItem(item.id, { siteId: filters.value.siteId })
    message.success('导航删除成功')
    await loadItems()
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除导航失败')
  }
}

const moveUp = async (item: NavigationItemData) => {
  try {
    await updateNavigationSort(item.id, { siteId: item.siteId, sortOrder: Math.max(0, item.sortOrder - 1) })
    await loadItems()
  } catch (error: any) {
    message.error(error.response?.data?.message || '更新排序失败')
  }
}

watch(() => filters.value.siteId, async () => { await loadItems() })
watch(() => [form.value.siteId, form.value.targetType], async () => { await loadTargets() })
onMounted(async () => { await loadSites(); await loadItems() })
</script>

<template>
  <div class="admin-page navigation-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">导航管理</h1>
        <p class="admin-page-desc">管理站点导航树、跳转目标和显示状态。</p>
      </div>
      <button class="admin-primary-btn" @click="openCreate">新建导航</button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <select v-model="filters.siteId" class="admin-filter-select">
          <option :value="undefined">请选择站点</option>
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <input v-model="filters.keyword" class="admin-form-input navigation-keyword" placeholder="关键词" @keyup.enter="loadItems" />
        <select v-model="filters.status" class="admin-filter-select" @change="loadItems">
          <option value="">全部状态</option>
          <option value="enabled">启用</option>
          <option value="disabled">禁用</option>
        </select>
        <button class="admin-secondary-btn" @click="loadItems">查询</button>
      </div>
    </div>

    <div class="admin-grid-2 navigation-grid">
      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">导航树</h3>
        </div>
        <div v-if="loading" class="admin-empty-state">加载中...</div>
        <div v-else-if="!items.length" class="admin-empty-state">暂无导航数据</div>
        <div v-else class="admin-tree-list">
          <div v-for="item in items" :key="item.id" class="admin-tree-row">
            <div class="admin-tree-main">
              <span class="admin-tree-title">{{ item.name }}</span>
              <span class="admin-tree-meta">{{ item.code }} · {{ item.targetType }} · 排序 {{ item.sortOrder }}</span>
            </div>
            <div class="admin-tree-actions">
              <span :class="['admin-status-badge', item.status === 'enabled' ? 'admin-status-badge--success' : 'admin-status-badge--default']">{{ item.status === 'enabled' ? '启用' : '禁用' }}</span>
              <button class="admin-link-action" @click="openEdit(item)">编辑</button>
              <button class="admin-link-action" @click="moveUp(item)">上移</button>
              <button class="admin-link-action" @click="removeItem(item)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">导航说明</h3>
        </div>
        <div class="admin-form-row admin-form-row--single">
          <div class="admin-form-group">
            <label class="admin-form-label">目标类型</label>
            <div class="admin-field-tip">支持 `category`、`topic`、`external_link`、`custom_page`。</div>
          </div>
          <div class="admin-form-group">
            <label class="admin-form-label">显示规则</label>
            <div class="admin-field-tip">主导航与面包屑开关应按导航使用场景进行配置。</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="modalOpen" class="admin-modal-overlay" @click.self="modalOpen = false">
      <div class="admin-modal-content admin-modal-content--large">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑导航' : '新建导航' }}</h3>
          <button class="admin-close-btn" @click="modalOpen = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">基础信息</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">站点</label>
                <select v-model="form.siteId" class="admin-form-select">
                  <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">父级导航</label>
                <select v-model="form.parentId" class="admin-form-select">
                  <option :value="null">作为顶级导航</option>
                  <option v-for="item in parentOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">导航名称</label>
                <input v-model="form.name" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">导航编码</label>
                <input v-model="form.code" class="admin-form-input" />
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">目标配置</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">目标类型</label>
                <select v-model="form.targetType" class="admin-form-select">
                  <option value="category">category</option>
                  <option value="topic">topic</option>
                  <option value="external_link">external_link</option>
                  <option value="custom_page">custom_page</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">目标对象</label>
                <select v-model="form.targetId" class="admin-form-select" :disabled="form.targetType === 'external_link' || form.targetType === 'custom_page'">
                  <option :value="null">请选择对象</option>
                  <option v-for="item in targetOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">显示规则</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">状态</label>
                <select v-model="form.status" class="admin-form-select">
                  <option value="enabled">启用</option>
                  <option value="disabled">禁用</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">排序</label>
                <input v-model.number="form.sortOrder" type="number" class="admin-form-input" />
              </div>
            </div>
            <div class="navigation-check-row">
              <label><input v-model="form.primaryNav" type="checkbox" /> <span>主导航</span></label>
              <label><input v-model="form.breadcrumbEnabled" type="checkbox" /> <span>启用面包屑</span></label>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalOpen = false">取消</button>
          <button class="admin-primary-btn" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.navigation-page .navigation-grid {
  align-items: start;
}

.navigation-page .navigation-keyword {
  width: 220px;
}

.navigation-page .modal-section {
  padding: 16px;
}

.navigation-page .navigation-check-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.navigation-page .navigation-check-row label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 1080px) {
  .navigation-page .navigation-keyword {
    width: 100%;
  }
}
</style>
