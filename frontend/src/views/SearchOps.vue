<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import { fetchArticles, type ArticleItem } from '../api/articles'
import { fetchCategories } from '../api/categories'
import { fetchTopics } from '../api/topics'
import {
  fetchSearchIndexStatus,
  rebuildSearchIndexCategory,
  rebuildSearchIndexContent,
  rebuildSearchIndexSite,
  rebuildSearchIndexTopic,
  type SearchIndexStatusData
} from '../api/searchIndex'

type ObjectType = 'content' | 'topic' | 'category'
interface OptionItem { value: number; label: string }

const sites = ref<SiteOptionItem[]>([])
const loading = ref(false)
const rebuilding = ref(false)
const status = ref<SearchIndexStatusData | null>(null)
const filters = ref({ siteId: undefined as number | undefined, days: 7, objectType: 'content' as ObjectType, objectId: undefined as number | undefined })
const objectOptions = ref<OptionItem[]>([])

const isSingleSite = computed(() => sites.value.length <= 1)
const selectedSiteLabel = computed(() => sites.value.find(item => item.id === filters.value.siteId)?.name || '-')
const buildQuickLinks = (keyword: string) => ({
  content: `/content?keyword=${encodeURIComponent(keyword)}`,
  topic: `/topics?keyword=${encodeURIComponent(keyword)}`,
  category: `/content/categories?keyword=${encodeURIComponent(keyword)}`
})

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
  if (!filters.value.siteId && sites.value.length) {
    filters.value.siteId = sites.value[0].id
  }
}

const loadObjectOptions = async () => {
  objectOptions.value = []
  filters.value.objectId = undefined
  if (!filters.value.siteId) return
  if (filters.value.objectType === 'content') {
    const response = await fetchArticles({ page: 0, size: 100, siteId: filters.value.siteId, status: 'published' })
    objectOptions.value = (response.data.content || []).map((item: ArticleItem) => ({ value: item.id, label: item.title }))
    return
  }
  if (filters.value.objectType === 'topic') {
    const response = await fetchTopics({ siteId: filters.value.siteId, status: 'active' })
    objectOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
    return
  }
  const response = await fetchCategories({ siteId: filters.value.siteId })
  objectOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
}

const loadStatus = async () => {
  if (!filters.value.siteId) {
    status.value = null
    return
  }
  loading.value = true
  try {
    const response = await fetchSearchIndexStatus(filters.value.siteId, 10, filters.value.days)
    status.value = response.data
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载搜索运营状态失败')
  } finally {
    loading.value = false
  }
}

const rebuildSite = async () => {
  if (!filters.value.siteId) return
  rebuilding.value = true
  try {
    const response = await rebuildSearchIndexSite(filters.value.siteId, 10, filters.value.days)
    status.value = response.data
    message.success('站点搜索索引已重建')
  } catch (error: any) {
    message.error(error.response?.data?.message || '站点搜索索引重建失败')
  } finally {
    rebuilding.value = false
  }
}

const rebuildObject = async () => {
  if (!filters.value.objectId) {
    message.warning('请选择要重建索引的对象')
    return
  }
  rebuilding.value = true
  try {
    let response
    if (filters.value.objectType === 'content') {
      response = await rebuildSearchIndexContent(filters.value.objectId, 10, filters.value.days)
    } else if (filters.value.objectType === 'topic') {
      response = await rebuildSearchIndexTopic(filters.value.objectId, 10, filters.value.days)
    } else {
      response = await rebuildSearchIndexCategory(filters.value.objectId, 10, filters.value.days)
    }
    status.value = response.data
    message.success('对象搜索索引已重建')
  } catch (error: any) {
    message.error(error.response?.data?.message || '对象搜索索引重建失败')
  } finally {
    rebuilding.value = false
  }
}

watch(() => filters.value.siteId, async () => {
  await loadObjectOptions()
  await loadStatus()
})

watch(() => filters.value.objectType, async () => {
  await loadObjectOptions()
})

watch(() => filters.value.days, async () => {
  await loadStatus()
})

onMounted(async () => {
  await loadSites()
  await loadObjectOptions()
  await loadStatus()
})
</script>

<template>
  <div class="admin-page search-ops-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">搜索运营</h1>
        <p class="admin-page-desc">查看站点搜索索引状态、热门词、零结果词与低结果词，并支持最小化手动重建。</p>
      </div>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <select v-model="filters.siteId" class="admin-filter-select" :disabled="isSingleSite">
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <select v-model="filters.days" class="admin-filter-select">
          <option :value="7">最近 7 天</option>
          <option :value="14">最近 14 天</option>
          <option :value="30">最近 30 天</option>
        </select>
      </div>
    </div>

    <div class="searchops-stats-grid">
      <div class="searchops-metric-card">
        <div class="admin-sub-text">当前站点</div>
        <div class="searchops-metric-label">{{ selectedSiteLabel }}</div>
      </div>
      <div class="searchops-metric-card">
        <div class="admin-sub-text">索引总量</div>
        <div class="searchops-metric-value">{{ status?.totalEntries ?? 0 }}</div>
      </div>
      <div class="searchops-metric-card">
        <div class="admin-sub-text">最近重建</div>
        <div class="searchops-metric-label">{{ status?.lastRebuildAt ? status.lastRebuildAt.replace('T', ' ').slice(0, 16) : '暂无' }}</div>
        <div class="admin-sub-text">{{ status?.lastRebuildSummary || '等待首次重建' }}</div>
      </div>
      <div class="searchops-metric-card">
        <div class="admin-sub-text">最近失败</div>
        <div class="searchops-warning-text">{{ status?.lastFailureReason || '暂无失败记录' }}</div>
      </div>
    </div>

    <div class="searchops-workspace-grid">
      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">站点索引</h3>
        </div>
        <div class="admin-sub-text">重建当前站点的内容、专题和栏目索引。</div>
        <div class="admin-toolbar-row searchops-actions">
          <button class="admin-primary-btn" :disabled="rebuilding || !filters.siteId" @click="rebuildSite">{{ rebuilding ? '处理中...' : '重建站点索引' }}</button>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">对象索引</h3>
        </div>
        <div class="admin-form-row">
          <div class="admin-form-group">
            <label class="admin-form-label">对象类型</label>
            <select v-model="filters.objectType" class="admin-form-select">
              <option value="content">内容</option>
              <option value="topic">专题</option>
              <option value="category">栏目</option>
            </select>
          </div>
          <div class="admin-form-group">
            <label class="admin-form-label">对象</label>
            <select v-model="filters.objectId" class="admin-form-select">
              <option :value="undefined">请选择对象</option>
              <option v-for="item in objectOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
        </div>
        <div class="admin-toolbar-row searchops-actions">
          <button class="admin-secondary-btn" :disabled="rebuilding || !filters.objectId" @click="rebuildObject">{{ rebuilding ? '处理中...' : '重建对象索引' }}</button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="admin-empty-state">加载中...</div>

    <div v-else class="searchops-diagnostics-grid">
      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">热门关键词 Top 10</h3>
        </div>
        <div v-if="status?.hotKeywords?.length" class="diagnostic-list">
          <div v-for="item in status.hotKeywords" :key="`hot-${item.keyword}`" class="diagnostic-item">
            <span>{{ item.keyword }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
        <div v-else class="admin-empty-state">暂无热门关键词</div>
      </div>

      <div class="admin-card">
        <div class="admin-card-header">
          <h3 class="admin-card-title">零结果关键词 Top 10</h3>
        </div>
        <div v-if="status?.zeroResultKeywords?.length" class="diagnostic-list">
          <div v-for="item in status.zeroResultKeywords" :key="`zero-${item.keyword}`" class="diagnostic-card warning-card">
            <div class="diagnostic-head"><span>{{ item.keyword }}</span><strong>{{ item.count }}</strong></div>
            <div class="diagnostic-links">
              <a class="diagnostic-link" :href="buildQuickLinks(item.keyword).content">补内容</a>
              <a class="diagnostic-link" :href="buildQuickLinks(item.keyword).topic">补专题</a>
              <a class="diagnostic-link" :href="buildQuickLinks(item.keyword).category">补栏目</a>
            </div>
          </div>
        </div>
        <div v-else class="admin-empty-state">暂无零结果关键词</div>
      </div>

      <div class="admin-card searchops-full-span">
        <div class="admin-card-header">
          <h3 class="admin-card-title">低结果关键词 Top 10</h3>
        </div>
        <div v-if="status?.lowResultKeywords?.length" class="diagnostic-list">
          <div v-for="item in status.lowResultKeywords" :key="`low-${item.keyword}`" class="diagnostic-card low-card">
            <div class="diagnostic-head"><span>{{ item.keyword }}</span><strong>{{ item.count }}</strong></div>
            <div class="diagnostic-links">
              <a class="diagnostic-link" :href="buildQuickLinks(item.keyword).content">补内容</a>
              <a class="diagnostic-link" :href="buildQuickLinks(item.keyword).topic">补专题</a>
              <a class="diagnostic-link" :href="buildQuickLinks(item.keyword).category">补栏目</a>
            </div>
          </div>
        </div>
        <div v-else class="admin-empty-state">暂无低结果关键词</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-ops-page .searchops-stats-grid,
.search-ops-page .searchops-workspace-grid,
.search-ops-page .searchops-diagnostics-grid {
  display: grid;
  gap: 16px;
}

.search-ops-page .searchops-stats-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.search-ops-page .searchops-workspace-grid,
.search-ops-page .searchops-diagnostics-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.search-ops-page .searchops-full-span {
  grid-column: 1 / -1;
}

.search-ops-page .searchops-metric-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 16px;
}

.search-ops-page .searchops-metric-value {
  font-size: 30px;
  font-weight: 700;
  margin-top: 8px;
}

.search-ops-page .searchops-metric-label {
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
}

.search-ops-page .searchops-warning-text {
  color: #b45309;
  font-size: 14px;
  line-height: 22px;
  margin-top: 8px;
}

.search-ops-page .searchops-actions {
  margin-top: 12px;
}

.search-ops-page .diagnostic-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-ops-page .diagnostic-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
}

.search-ops-page .diagnostic-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
}

.search-ops-page .warning-card {
  border: 1px solid #fbbf24;
  background: #fffbeb;
}

.search-ops-page .low-card {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
}

.search-ops-page .diagnostic-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.search-ops-page .diagnostic-links {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.search-ops-page .diagnostic-link {
  color: #2563eb;
  text-decoration: none;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #cbd5e1;
}

.search-ops-page .diagnostic-link:hover {
  background: #f8fafc;
}

@media (max-width: 1080px) {
  .search-ops-page .searchops-stats-grid,
  .search-ops-page .searchops-workspace-grid,
  .search-ops-page .searchops-diagnostics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
