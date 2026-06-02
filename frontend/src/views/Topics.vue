<script setup lang="ts">
import '../styles/admin-refresh.css'

import { onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import { fetchCategories } from '../api/categories'
import { fetchTemplates, type TemplateItem } from '../api/templates'
import {
  createTopic,
  deleteTopic,
  fetchTopicContentItems,
  fetchTopics,
  replaceTopicContentItems,
  updateTopic,
  type TopicContentItemData,
  type TopicItem,
  type TopicPayload
} from '../api/topics'

interface OptionItem { value: number; label: string }
interface TopicFormState extends TopicPayload {}

const loading = ref(false)
const saving = ref(false)
const contentSaving = ref(false)
const modalVisible = ref(false)
const contentModalVisible = ref(false)
const isEdit = ref(false)
const sites = ref<SiteOptionItem[]>([])
const rows = ref<TopicItem[]>([])
const templateOptions = ref<TemplateItem[]>([])
const categoryOptions = ref<OptionItem[]>([])
const articleOptions = ref<OptionItem[]>([])
const selectedTopic = ref<TopicItem | null>(null)
const selectedArticleIds = ref<number[]>([])
const filters = ref({ siteId: undefined as number | undefined, status: '', keyword: '' })
const formState = ref<TopicFormState>({
  siteId: 0,
  name: '',
  code: '',
  slug: '',
  summary: '',
  status: 'active',
  templateId: null,
  aggregationMode: 'manual',
  ruleCategoryId: null,
  ruleLimit: 10,
  seoTitle: '',
  seoKeywords: '',
  seoDescription: '',
  navVisible: false
})

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
  if (!filters.value.siteId && sites.value.length) {
    filters.value.siteId = sites.value[0].id
    formState.value.siteId = sites.value[0].id
  }
}

const loadTopics = async () => {
  if (!filters.value.siteId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const response = await fetchTopics({
      siteId: filters.value.siteId,
      status: filters.value.status || undefined,
      keyword: filters.value.keyword || undefined
    })
    rows.value = response.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载专题列表失败')
  } finally {
    loading.value = false
  }
}

const loadTemplateOptions = async (siteId?: number) => {
  if (!siteId) {
    templateOptions.value = []
    return
  }
  const response = await fetchTemplates({ siteId, type: 'topic_page' })
  templateOptions.value = response.data || []
}

const loadCategoryOptions = async (siteId?: number) => {
  if (!siteId) {
    categoryOptions.value = []
    return
  }
  const response = await fetchCategories({ siteId })
  categoryOptions.value = (response.data || []).map((item: any) => ({ value: item.id, label: item.name }))
}

const loadArticleOptions = async (siteId?: number) => {
  if (!siteId) {
    articleOptions.value = []
    return
  }
  const response = await fetch('/api/articles?page=0&size=100&siteId=' + siteId + '&status=published')
  const data = await response.json()
  articleOptions.value = (data.content || []).map((item: any) => ({ value: item.id, label: item.title }))
}

const resetForm = () => {
  formState.value = {
    siteId: filters.value.siteId || 0,
    name: '',
    code: '',
    slug: '',
    summary: '',
    status: 'active',
    templateId: null,
    aggregationMode: 'manual',
    ruleCategoryId: null,
    ruleLimit: 10,
    seoTitle: '',
    seoKeywords: '',
    seoDescription: '',
    navVisible: false
  }
}

const openCreate = async () => {
  resetForm()
  isEdit.value = false
  await loadTemplateOptions(formState.value.siteId)
  await loadCategoryOptions(formState.value.siteId)
  modalVisible.value = true
}

const openEdit = async (record: TopicItem) => {
  formState.value = {
    siteId: record.siteId,
    name: record.name,
    code: record.code,
    slug: record.slug,
    summary: record.summary || '',
    status: record.status,
    templateId: record.templateId ?? null,
    aggregationMode: record.aggregationMode,
    ruleCategoryId: record.ruleCategoryId ?? null,
    ruleLimit: record.ruleLimit ?? 10,
    seoTitle: record.seoTitle || '',
    seoKeywords: record.seoKeywords || '',
    seoDescription: record.seoDescription || '',
    navVisible: record.navVisible
  }
  selectedTopic.value = record
  isEdit.value = true
  await loadTemplateOptions(record.siteId)
  await loadCategoryOptions(record.siteId)
  modalVisible.value = true
}

const openContentArrange = async (record: TopicItem) => {
  selectedTopic.value = record
  selectedArticleIds.value = []
  await loadArticleOptions(record.siteId)
  try {
    const response = await fetchTopicContentItems(record.id, { siteId: record.siteId })
    selectedArticleIds.value = (response.data || []).map((item: TopicContentItemData) => item.articleId)
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载专题编排内容失败')
  }
  contentModalVisible.value = true
}

const handleSave = async () => {
  if (!formState.value.siteId || !formState.value.name?.trim() || !formState.value.code?.trim() || !formState.value.slug?.trim()) {
    message.warning('请完整填写专题基础信息')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && selectedTopic.value) {
      await updateTopic(selectedTopic.value.id, formState.value)
      message.success('专题更新成功')
    } else {
      await createTopic(formState.value)
      message.success('专题创建成功')
    }
    modalVisible.value = false
    await loadTopics()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存专题失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = (record: TopicItem) => {
  if (!filters.value.siteId) return
  if (!window.confirm(`确认删除专题“${record.name}”吗？`)) {
    return
  }
  deleteTopic(record.id, { siteId: filters.value.siteId })
    .then(() => {
      message.success('专题删除成功')
      loadTopics()
    })
    .catch((error: any) => {
      message.error(error.response?.data?.message || '专题删除失败')
    })
}

const handleSaveContent = async () => {
  if (!selectedTopic.value) return
  contentSaving.value = true
  try {
    await replaceTopicContentItems(selectedTopic.value.id, { siteId: selectedTopic.value.siteId, articleIds: selectedArticleIds.value })
    message.success('内容编排保存成功')
    contentModalVisible.value = false
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存内容编排失败')
  } finally {
    contentSaving.value = false
  }
}

watch(() => filters.value.siteId, async siteId => {
  if (!siteId) return
  formState.value.siteId = siteId
  await loadTopics()
})

onMounted(async () => {
  await loadSites()
  await loadTopics()
})
</script>

<template>
  <div class="admin-page topics-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">专题管理</h1>
        <p class="admin-page-desc">管理专题对象、模板绑定和内容编排；当前版本支持专题页正式发布。</p>
      </div>
      <button class="admin-primary-btn" @click="openCreate">新建专题</button>
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <select v-model="filters.siteId" class="admin-filter-select">
          <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
        </select>
        <select v-model="filters.status" class="admin-filter-select" @change="loadTopics">
          <option value="">全部状态</option>
          <option value="active">active</option>
          <option value="inactive">inactive</option>
        </select>
        <div class="admin-search-box">
          <input v-model="filters.keyword" class="admin-search-input" placeholder="搜索专题名称、编码或路径" @keyup.enter="loadTopics" />
        </div>
        <button class="admin-secondary-btn" @click="loadTopics">查询</button>
      </div>
    </div>

    <div class="admin-table-card">
      <table class="admin-data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>编码</th>
            <th>路径</th>
            <th>模板</th>
            <th>聚合模式</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="admin-empty-cell">加载中...</td></tr>
          <tr v-else-if="!rows.length"><td colspan="7" class="admin-empty-cell">暂无专题数据</td></tr>
          <tr v-for="item in rows" :key="item.id">
            <td>
              <div class="topic-name-cell">
                <strong>{{ item.name }}</strong>
                <span class="admin-sub-text">{{ item.summary || '暂无摘要' }}</span>
              </div>
            </td>
            <td>{{ item.code }}</td>
            <td>/topics/{{ item.slug }}/index.html</td>
            <td>{{ templateOptions.find(template => template.id === item.templateId)?.name || '-' }}</td>
            <td>{{ item.aggregationMode }}</td>
            <td>
              <span :class="['admin-status-badge', item.status === 'active' ? 'admin-status-badge--success' : 'admin-status-badge--default']">{{ item.status }}</span>
            </td>
            <td>
              <div class="topic-actions">
                <button class="admin-icon-btn" @click="openEdit(item)">编辑</button>
                <button class="admin-icon-btn" @click="openContentArrange(item)">编排</button>
                <button class="admin-icon-btn admin-icon-btn--danger" @click="handleDelete(item)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="modalVisible" class="admin-modal-overlay" @click.self="modalVisible = false">
      <div class="admin-modal-content admin-modal-content--large">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">{{ isEdit ? '编辑专题' : '新建专题' }}</h3>
          <button class="admin-close-btn" @click="modalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">站点</label>
              <select v-model="formState.siteId" class="admin-form-select">
                <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
              </select>
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">状态</label>
              <select v-model="formState.status" class="admin-form-select">
                <option value="active">active</option>
                <option value="inactive">inactive</option>
              </select>
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">专题名称</label>
              <input v-model="formState.name" class="admin-form-input" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">专题编码</label>
              <input v-model="formState.code" class="admin-form-input" />
            </div>
          </div>

          <div class="admin-form-row">
            <div class="admin-form-group">
              <label class="admin-form-label">路径标识</label>
              <input v-model="formState.slug" class="admin-form-input" />
            </div>
            <div class="admin-form-group">
              <label class="admin-form-label">导航入口</label>
              <select v-model="formState.navVisible" class="admin-form-select">
                <option :value="true">显示</option>
                <option :value="false">隐藏</option>
              </select>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">基础信息</h4></div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">专题摘要</label>
                <textarea v-model="formState.summary" rows="3" class="admin-form-textarea"></textarea>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">模板绑定</h4></div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">专题模板</label>
                <select v-model="formState.templateId" class="admin-form-select">
                  <option :value="null">请选择模板</option>
                  <option v-for="item in templateOptions" :key="item.id" :value="item.id">{{ item.name }} ({{ item.code }})</option>
                </select>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">聚合规则</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">聚合模式</label>
                <select v-model="formState.aggregationMode" class="admin-form-select">
                  <option value="manual">manual</option>
                  <option value="rule_based">rule_based</option>
                </select>
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">规则栏目</label>
                <select v-model="formState.ruleCategoryId" class="admin-form-select" :disabled="formState.aggregationMode !== 'rule_based'">
                  <option :value="null">请选择栏目</option>
                  <option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </div>
            </div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">规则数量</label>
                <input v-model.number="formState.ruleLimit" type="number" min="1" class="admin-form-input" :disabled="formState.aggregationMode !== 'rule_based'" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">规则说明</label>
                <div class="admin-field-tip">`manual` 使用手工编排结果，`rule_based` 使用栏目 + 数量规则聚合。</div>
              </div>
            </div>
          </div>

          <div class="admin-card modal-section">
            <div class="admin-card-header"><h4 class="admin-card-title">SEO 设置</h4></div>
            <div class="admin-form-row">
              <div class="admin-form-group">
                <label class="admin-form-label">SEO 标题</label>
                <input v-model="formState.seoTitle" class="admin-form-input" />
              </div>
              <div class="admin-form-group">
                <label class="admin-form-label">SEO 关键词</label>
                <input v-model="formState.seoKeywords" class="admin-form-input" />
              </div>
            </div>
            <div class="admin-form-row admin-form-row--single">
              <div class="admin-form-group">
                <label class="admin-form-label">SEO 描述</label>
                <textarea v-model="formState.seoDescription" rows="2" class="admin-form-textarea"></textarea>
              </div>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="modalVisible = false">取消</button>
          <button class="admin-primary-btn" :disabled="saving" @click="handleSave">{{ saving ? '保存中...' : '保存专题' }}</button>
        </div>
      </div>
    </div>

    <div v-if="contentModalVisible" class="admin-modal-overlay" @click.self="contentModalVisible = false">
      <div class="admin-modal-content admin-modal-content--large">
        <div class="admin-modal-header">
          <h3 class="admin-modal-title">内容编排</h3>
          <button class="admin-close-btn" @click="contentModalVisible = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row admin-form-row--single">
            <div class="admin-form-group">
              <label class="admin-form-label">已发布内容</label>
              <select v-model="selectedArticleIds" multiple class="admin-form-select topics-article-select">
                <option v-for="item in articleOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <div class="admin-field-tip">仅展示当前站点已发布内容；保存后会替换现有手工编排结果。</div>
            </div>
          </div>
        </div>
        <div class="admin-modal-footer">
          <button class="admin-secondary-btn" @click="contentModalVisible = false">取消</button>
          <button class="admin-primary-btn" :disabled="contentSaving" @click="handleSaveContent">{{ contentSaving ? '保存中...' : '保存编排' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.topics-page .topic-name-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.topics-page .topic-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.topics-page .topic-actions .admin-icon-btn {
  width: auto;
  min-width: 72px;
  padding: 0 12px;
}

.topics-page .modal-section {
  padding: 16px;
}

.topics-page .topics-article-select {
  min-height: 180px;
  padding: 10px 12px;
}
</style>
