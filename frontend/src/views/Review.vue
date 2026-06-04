<script setup lang="ts">
import '../styles/admin-refresh.css'

import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { fetchCategories } from '../api/categories'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import {
  approveArticle,
  fetchArticleDetail,
  fetchArticleHistories,
  fetchArticles,
  rejectArticle,
  type ArticleItem,
  type ArticleLifecycleHistoryItem
} from '../api/articles'

type SiteOption = SiteOptionItem
interface CategoryOption { id: number; name: string }

const loading = ref(false)
const detailOpen = ref(false)
const rejectOpen = ref(false)
const sites = ref<SiteOption[]>([])
const categories = ref<CategoryOption[]>([])
const articles = ref<ArticleItem[]>([])
const selectedArticle = ref<ArticleItem | null>(null)
const approvingId = ref<number | null>(null)
const histories = ref<ArticleLifecycleHistoryItem[]>([])
const rejectReason = ref('')
const rejecting = ref(false)
const filters = ref({ keyword: '', siteId: undefined as number | undefined, primaryCategoryId: undefined as number | undefined })

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
}

const loadCategories = async (siteId?: number) => {
  if (!siteId) {
    categories.value = []
    return
  }
  const response = await fetchCategories({ siteId })
  categories.value = response.data || []
}

const loadArticles = async () => {
  loading.value = true
  try {
    const response = await fetchArticles({
      page: 0,
      size: 100,
      status: 'pending_review',
      keyword: filters.value.keyword || undefined,
      siteId: filters.value.siteId,
      primaryCategoryId: filters.value.primaryCategoryId
    })
    articles.value = response.data.content || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取待审核内容失败')
  } finally {
    loading.value = false
  }
}

const openDetail = async (record: ArticleItem) => {
  try {
    const [detailResponse, historyResponse] = await Promise.all([
      fetchArticleDetail(record.id),
      fetchArticleHistories(record.id)
    ])
    selectedArticle.value = detailResponse.data
    histories.value = historyResponse.data || []
    detailOpen.value = true
  } catch (error: any) {
    message.error(error.response?.data?.message || '获取审核详情失败')
  }
}

const handleApprove = async (record: ArticleItem) => {
  if (approvingId.value) return
  Modal.confirm({
    title: '确认通过',
    content: `确认通过《${record.title}》？通过后内容将进入待发布状态。`,
    okText: '通过',
    cancelText: '取消',
    async onOk() {
      approvingId.value = record.id
      try {
        await approveArticle(record.id)
        message.success('审核通过成功')
        if (selectedArticle.value?.id === record.id) {
          detailOpen.value = false
        }
        await loadArticles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '审核通过失败')
      } finally {
        approvingId.value = null
      }
    }
  })
}

const openReject = (record: ArticleItem) => {
  selectedArticle.value = record
  rejectReason.value = ''
  rejectOpen.value = true
}

const handleReject = async () => {
  if (!selectedArticle.value) return
  if (!rejectReason.value.trim()) {
    message.warning('请输入驳回原因')
    return
  }
  rejecting.value = true
  try {
    await rejectArticle(selectedArticle.value.id, rejectReason.value.trim())
    message.success('驳回成功')
    rejectOpen.value = false
    detailOpen.value = false
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '驳回失败')
  } finally {
    rejecting.value = false
  }
}

onMounted(async () => {
  await loadSites()
  await loadArticles()
})
</script>

<template>
  <div class="admin-page review-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">审核工作区</h1>
        <p class="admin-page-desc">聚焦 `pending_review` 内容，完成查看、通过、驳回三类核心动作。</p>
      </div>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <div class="admin-search-box">
          <input v-model="filters.keyword" class="admin-search-input" placeholder="关键词搜索" @keyup.enter="loadArticles" />
        </div>
        <select v-model="filters.siteId" class="admin-filter-select" @change="loadCategories(filters.siteId)">
          <option :value="undefined">全部站点</option>
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <select v-model="filters.primaryCategoryId" class="admin-filter-select" @change="loadArticles">
          <option :value="undefined">全部栏目</option>
          <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
        <button class="admin-secondary-btn" @click="loadArticles">查询</button>
      </div>
    </div>

    <div class="admin-table-card">
      <table class="admin-data-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>栏目</th>
            <th>作者</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-if="loading">
            <tr v-for="n in 5" :key="`sk-${n}`">
              <td colspan="5">
                <div class="admin-skeleton-row" style="margin: 8px 16px;"></div>
              </td>
            </tr>
          </template>
          <tr v-else-if="!articles.length">
            <td colspan="5">
              <div class="admin-empty-box">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <rect x="3" y="3" width="18" height="18" rx="4"/>
                  <path d="M9 9h6H9z"/>
                </svg>
                <p>当前没有待审核内容</p>
              </div>
            </td>
          </tr>
          <tr v-for="item in articles" :key="item.id">
            <td>{{ item.title }}</td>
            <td>{{ item.category || '-' }}</td>
            <td>{{ item.author || '-' }}</td>
            <td class="admin-muted-cell">{{ item.updatedAt ? item.updatedAt.replace('T', ' ').slice(0, 16) : '-' }}</td>
            <td>
              <div class="review-actions">
                <button class="admin-link-action" @click="openDetail(item)">查看</button>
                <button
                  class="admin-link-action success-link"
                  :disabled="approvingId === item.id"
                  @click="handleApprove(item)"
                >
                  {{ approvingId === item.id ? '通过中...' : '通过' }}
                </button>
                <button class="admin-link-action danger-link" @click="openReject(item)">驳回</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <a-modal v-model:open="detailOpen" title="审核详情" width="1100px" :footer="null" destroy-on-close>
      <div v-if="selectedArticle" class="admin-detail-grid">
        <div class="admin-detail-card">
          <h3 class="admin-card-title">{{ selectedArticle.title }}</h3>
          <div class="admin-sub-text">栏目：{{ selectedArticle.category || '-' }} · 作者：{{ selectedArticle.author || '-' }}</div>
          <div class="admin-content-box" v-html="selectedArticle.content || '<p>暂无正文</p>'"></div>
          <div class="review-detail-actions">
            <button class="admin-secondary-btn" @click="detailOpen = false">关闭</button>
            <button
              class="admin-primary-btn"
              :disabled="approvingId === selectedArticle?.id"
              @click="handleApprove(selectedArticle)"
            >
              {{ approvingId === selectedArticle?.id ? '通过中...' : '审核通过' }}
            </button>
            <button class="admin-danger-btn" @click="openReject(selectedArticle)">驳回</button>
          </div>
        </div>
        <div class="admin-detail-card">
          <div class="admin-card-header"><h3 class="admin-card-title">流转历史</h3></div>
          <div v-if="histories.length" class="admin-history-list">
            <div v-for="item in histories" :key="item.id" class="admin-history-item">
              <div>{{ item.action }} · {{ item.operatorName }}</div>
              <div class="admin-sub-text">{{ item.fromStatus || '-' }} → {{ item.toStatus || '-' }}</div>
              <div class="admin-sub-text">{{ item.createdAt?.replace('T', ' ').slice(0, 16) }}</div>
              <div v-if="item.reason" class="admin-sub-text">{{ item.reason }}</div>
            </div>
          </div>
          <div v-else class="admin-empty-state">暂无历史</div>
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="rejectOpen" title="驳回内容" :footer="null" destroy-on-close>
      <div class="admin-reject-box">
        <p>请输入驳回原因：</p>
        <textarea v-model="rejectReason" class="admin-form-textarea" rows="5" placeholder="请填写明确的驳回意见"></textarea>
        <div class="review-detail-actions">
          <button class="admin-secondary-btn" @click="rejectOpen = false">取消</button>
          <button class="admin-danger-btn" :disabled="rejecting" @click="handleReject">
            {{ rejecting ? '驳回中...' : '确认驳回' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.review-page .review-actions,
.review-page .review-detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-page .success-link {
  color: #15803d;
}

.review-page .danger-link {
  color: #dc2626;
}
</style>
