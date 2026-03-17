<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { fetchCategories } from '../api/categories'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import { approveArticle, fetchArticleDetail, fetchArticleHistories, fetchArticles, rejectArticle, type ArticleItem, type ArticleLifecycleHistoryItem } from '../api/articles'

type SiteOption = SiteOptionItem
interface CategoryOption { id: number; name: string }

const loading = ref(false)
const detailOpen = ref(false)
const rejectOpen = ref(false)
const sites = ref<SiteOption[]>([])
const categories = ref<CategoryOption[]>([])
const articles = ref<ArticleItem[]>([])
const selectedArticle = ref<ArticleItem | null>(null)
const histories = ref<ArticleLifecycleHistoryItem[]>([])
const rejectReason = ref('')
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
  try {
    await approveArticle(record.id)
    message.success('审核通过成功')
    if (selectedArticle.value?.id === record.id) {
      detailOpen.value = false
    }
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '审核通过失败')
  }
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
  try {
    await rejectArticle(selectedArticle.value.id, rejectReason.value.trim())
    message.success('驳回成功')
    rejectOpen.value = false
    detailOpen.value = false
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '驳回失败')
  }
}

onMounted(async () => {
  await loadSites()
  await loadArticles()
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2>审核工作区</h2>
        <p>聚焦 `pending_review` 内容，完成查看、通过、驳回三类核心动作。</p>
      </div>
    </div>

    <div class="toolbar">
      <input v-model="filters.keyword" class="input" placeholder="关键词搜索" @keyup.enter="loadArticles" />
      <select v-model="filters.siteId" class="input small-select" @change="loadCategories(filters.siteId)">
        <option :value="undefined">全部站点</option>
        <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
      </select>
      <select v-model="filters.primaryCategoryId" class="input small-select">
        <option :value="undefined">全部栏目</option>
        <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
      </select>
      <button class="secondary-btn" @click="loadArticles">查询</button>
    </div>

    <div class="table-card">
      <table class="data-table">
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
          <tr v-if="loading">
            <td colspan="5" class="empty-row">加载中...</td>
          </tr>
          <tr v-else-if="!articles.length">
            <td colspan="5" class="empty-row">当前没有待审核内容</td>
          </tr>
          <tr v-for="item in articles" :key="item.id">
            <td>{{ item.title }}</td>
            <td>{{ item.category || '-' }}</td>
            <td>{{ item.author || '-' }}</td>
            <td>{{ item.updatedAt ? item.updatedAt.replace('T', ' ').slice(0, 16) : '-' }}</td>
            <td>
              <div class="actions">
                <button class="link-btn" @click="openDetail(item)">查看</button>
                <button class="link-btn success" @click="handleApprove(item)">通过</button>
                <button class="link-btn danger" @click="openReject(item)">驳回</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <a-modal v-model:open="detailOpen" title="审核详情" width="980px" :footer="null" destroy-on-close>
      <div v-if="selectedArticle" class="detail-grid">
        <div class="detail-card">
          <h3>{{ selectedArticle.title }}</h3>
          <div class="meta-row">栏目：{{ selectedArticle.category || '-' }} · 作者：{{ selectedArticle.author || '-' }}</div>
          <div class="content-box" v-html="selectedArticle.content || '<p>暂无正文</p>'"></div>
          <div class="footer-actions">
            <button class="secondary-btn" @click="detailOpen = false">关闭</button>
            <button class="primary-btn" @click="handleApprove(selectedArticle)">审核通过</button>
            <button class="danger-btn" @click="openReject(selectedArticle)">驳回</button>
          </div>
        </div>
        <div class="detail-card">
          <h4>流转历史</h4>
          <div v-if="histories.length" class="history-list">
            <div v-for="item in histories" :key="item.id" class="history-item">
              <div>{{ item.action }} · {{ item.operatorName }}</div>
              <div class="sub-text">{{ item.fromStatus || '-' }} → {{ item.toStatus || '-' }}</div>
              <div class="sub-text">{{ item.createdAt?.replace('T', ' ').slice(0, 16) }}</div>
              <div v-if="item.reason" class="sub-text">{{ item.reason }}</div>
            </div>
          </div>
          <div v-else class="empty-row">暂无历史</div>
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="rejectOpen" title="驳回内容" :footer="null" destroy-on-close>
      <div class="reject-box">
        <p>请输入驳回原因：</p>
        <textarea v-model="rejectReason" class="textarea" rows="5" placeholder="请填写明确的驳回意见"></textarea>
        <div class="footer-actions">
          <button class="secondary-btn" @click="rejectOpen = false">取消</button>
          <button class="danger-btn" @click="handleReject">确认驳回</button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 16px; }
.page-header h2 { margin: 0; }
.page-header p { margin: 6px 0 0; color: #64748b; }
.toolbar, .actions, .footer-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.table-card, .detail-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 16px; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px; border-bottom: 1px solid #e2e8f0; text-align: left; }
.empty-row { text-align: center; color: #94a3b8; padding: 32px 0; }
.input, .textarea { width: 100%; border: 1px solid #cbd5e1; border-radius: 10px; padding: 10px 12px; box-sizing: border-box; }
.small-select { min-width: 160px; }
.link-btn, .secondary-btn, .primary-btn, .danger-btn { border: none; border-radius: 10px; padding: 10px 14px; cursor: pointer; }
.link-btn { background: transparent; padding: 0; color: #2563eb; }
.link-btn.success { color: #15803d; }
.link-btn.danger, .danger-btn { color: #fff; background: #dc2626; }
.primary-btn { background: #2563eb; color: #fff; }
.secondary-btn { background: #e2e8f0; color: #0f172a; }
.detail-grid { display: grid; grid-template-columns: 1.3fr 0.9fr; gap: 16px; }
.meta-row, .sub-text { color: #64748b; font-size: 12px; }
.content-box { margin-top: 12px; padding: 16px; border-radius: 12px; background: #f8fafc; min-height: 260px; }
.history-list { display: flex; flex-direction: column; gap: 10px; }
.history-item, .reject-box { background: #f8fafc; border-radius: 12px; padding: 12px; }
@media (max-width: 1100px) { .detail-grid { grid-template-columns: 1fr; } }
</style>