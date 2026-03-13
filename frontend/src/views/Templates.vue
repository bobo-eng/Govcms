<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, SyncOutlined } from '@ant-design/icons-vue'
import api from '../utils/api'
import { usePermission } from '../composables/usePermission'
import {
  createTemplate,
  createTemplateBinding,
  createTemplateVersion,
  deleteTemplateBinding,
  fetchTemplateBindings,
  fetchTemplateDetail,
  fetchTemplateImpact,
  fetchTemplates,
  fetchTemplateVersions,
  previewTemplate,
  rollbackTemplateVersion,
  updateTemplate,
  type TemplateBindingItem,
  type TemplateImpactResponseData,
  type TemplateItem,
  type TemplatePreviewResponseData,
  type TemplateVersionItem
} from '../api/templates'

interface SiteItem {
  id: number
  name: string
  code: string
  status: string
}

interface CategoryItem {
  id: number
  name: string
  fullPath: string
}

interface ArticleItem {
  id: number
  title: string
  status: string
}

interface TemplateForm {
  id: number | null
  siteId: number | null
  name: string
  code: string
  type: string
  status: string
  description: string
  defaultPreviewSource: string
  layoutSchema: string
  blockSchema: string
  seoSchema: string
  styleSchema: string
  changeLog: string
}

type SchemaTab = 'layout' | 'block' | 'seo' | 'style'
type RightTab = 'preview' | 'impact' | 'versions'
type PreviewPanelTab = 'page' | 'contract' | 'schema' | 'html'

const templateTypeOptions = [
  { value: '', label: '全部类型' },
  { value: 'home', label: '首页' },
  { value: 'column_list', label: '栏目列表' },
  { value: 'content_detail', label: '内容详情' },
  { value: 'not_found', label: '404 页面' },
  { value: 'topic_page', label: '专题页' }
] as const

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'draft', label: '草稿' },
  { value: 'active', label: '启用' },
  { value: 'disabled', label: '停用' }
] as const

const previewSourceOptions = [
  { value: 'sample', label: '样例' },
  { value: 'column', label: '栏目' },
  { value: 'content', label: '内容' }
] as const

const previewContextSections = [
  { key: 'siteContext', title: '站点上下文' },
  { key: 'navigationContext', title: '导航上下文' },
  { key: 'columnContext', title: '栏目上下文' },
  { key: 'contentContext', title: '内容上下文' },
  { key: 'topicContext', title: '专题上下文' },
  { key: 'renderMeta', title: '渲染元信息' }
] as const

const templateTypeLabelMap: Record<string, string> = {
  home: '首页',
  column_list: '栏目列表',
  content_detail: '内容详情',
  not_found: '404 页面',
  topic_page: '专题页'
}

const statusLabelMap: Record<string, string> = {
  draft: '草稿',
  active: '启用',
  disabled: '停用',
  inactive: '停用'
}

const previewSourceLabelMap: Record<string, string> = {
  sample: '样例',
  column: '栏目',
  content: '内容'
}

const bindingSlotLabelMap: Record<string, string> = {
  site_home: '站点首页',
  site_detail_default: '站点默认详情',
  site_404: '站点 404',
  column_list: '栏目列表',
  column_detail_default: '栏目默认详情',
  topic_page: '专题页'
}

const { hasPermission } = usePermission()
const canView = hasPermission('template:manage:view')

const filters = reactive({ siteId: null as number | null, type: '', status: '', keyword: '' })
const sites = ref<SiteItem[]>([])
const categoryOptions = ref<CategoryItem[]>([])
const articleOptions = ref<ArticleItem[]>([])
const templates = ref<TemplateItem[]>([])
const versions = ref<TemplateVersionItem[]>([])
const bindings = ref<TemplateBindingItem[]>([])
const impact = ref<TemplateImpactResponseData | null>(null)
const preview = ref<TemplatePreviewResponseData | null>(null)

const listLoading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const savingVersion = ref(false)
const bindingLoading = ref(false)
const previewing = ref(false)
const previewModalOpen = ref(false)

const editorSchemaTab = ref<SchemaTab>('layout')
const previewSchemaTab = ref<SchemaTab>('layout')
const rightTab = ref<RightTab>('preview')
const previewPanelTab = ref<PreviewPanelTab>('page')

const loadedSnapshotFingerprint = ref('')
const previewSnapshotFingerprint = ref('')

const form = ref<TemplateForm>(emptyForm())
const bindingForm = reactive({
  targetType: 'site',
  bindingSlot: 'site_home',
  targetId: null as number | null,
  templateVersionId: null as number | null,
  replaceExisting: true
})
const previewForm = reactive({ sourceType: 'sample', sourceId: null as number | null })

function unwrapList<T>(payload: any): T[] {
  if (Array.isArray(payload)) {
    return payload as T[]
  }
  if (Array.isArray(payload?.content)) {
    return payload.content as T[]
  }
  return []
}

function emptyForm(siteId: number | null = filters.siteId): TemplateForm {
  return {
    id: null,
    siteId,
    name: '',
    code: '',
    type: 'home',
    status: 'draft',
    description: '',
    defaultPreviewSource: 'sample',
    layoutSchema: '{\n  "layout": []\n}',
    blockSchema: '{\n  "blocks": []\n}',
    seoSchema: '{\n  "title": ""\n}',
    styleSchema: '{\n  "theme": {}\n}',
    changeLog: ''
  }
}

function pretty(value: unknown) {
  return typeof value === 'string' ? value : JSON.stringify(value ?? {}, null, 2)
}

function ensure(permissionCode: string, actionName: string) {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`暂无${actionName}权限`)
  return false
}

function formatTemplateType(type?: string | null) {
  return type ? (templateTypeLabelMap[type] ?? type) : '--'
}

function formatStatus(status?: string | null) {
  return status ? (statusLabelMap[status] ?? status) : '--'
}

function formatPreviewSource(source?: string | null) {
  return source ? (previewSourceLabelMap[source] ?? source) : '--'
}

function formatBindingSlot(slot?: string | null) {
  return slot ? (bindingSlotLabelMap[slot] ?? slot) : '--'
}

function formatBindingTarget(item: TemplateBindingItem) {
  if (item.targetType === 'site') {
    return sites.value.find(site => site.id === item.targetId)?.name ?? `站点 #${item.targetId}`
  }
  if (item.targetType === 'column') {
    return categoryOptions.value.find(category => category.id === item.targetId)?.fullPath ?? `栏目 #${item.targetId}`
  }
  return `${item.targetType} #${item.targetId}`
}

function buildSnapshotFingerprint(source: Pick<TemplateForm, 'siteId' | 'type' | 'status' | 'layoutSchema' | 'blockSchema' | 'seoSchema' | 'styleSchema'>) {
  return JSON.stringify({
    siteId: source.siteId ?? null,
    type: source.type,
    status: source.status,
    layoutSchema: source.layoutSchema,
    blockSchema: source.blockSchema,
    seoSchema: source.seoSchema,
    styleSchema: source.styleSchema
  })
}

const formTypeOptions = computed(() => templateTypeOptions.filter(item => item.value))
const currentVersionId = computed(() => {
  if (!form.value.id) {
    return null
  }
  return templates.value.find(item => item.id === form.value.id)?.currentVersionId ?? versions.value[0]?.id ?? null
})
const currentVersion = computed(() => versions.value.find(item => item.id === currentVersionId.value) ?? versions.value[0] ?? null)
const editorSnapshotFingerprint = computed(() => buildSnapshotFingerprint(form.value))
const editorHasUnsavedPreviewChanges = computed(() => Boolean(form.value.id && loadedSnapshotFingerprint.value && loadedSnapshotFingerprint.value !== editorSnapshotFingerprint.value))
const previewIsStale = computed(() => Boolean(preview.value && previewSnapshotFingerprint.value && previewSnapshotFingerprint.value !== editorSnapshotFingerprint.value))
const previewSummary = computed<Record<string, any>>(() => preview.value?.summary ?? {})
const previewContext = computed<Record<string, any>>(() => preview.value?.context ?? {})
const previewWarnings = computed<string[]>(() => Array.isArray(preview.value?.warnings) ? preview.value!.warnings : [])
const previewBlockingReasons = computed<string[]>(() => {
  const reasons = previewSummary.value?.blockingReasons
  return Array.isArray(reasons) ? reasons.map(item => String(item)) : []
})
const previewHasHtml = computed(() => Boolean(preview.value?.renderedHtml?.trim()))
const previewPathHint = computed(() => String(previewSummary.value?.pathHint ?? '--'))
const previewPublishReady = computed(() => Boolean(previewSummary.value?.publishReady ?? previewContext.value?.renderMeta?.publishReady))
const previewInfoCards = computed(() => [
  { label: '页面类型', value: preview.value?.pageType ?? '--' },
  { label: '模板版本', value: preview.value ? `V${preview.value.versionNo}` : '--' },
  { label: '数据源', value: formatPreviewSource(preview.value?.sourceType) },
  { label: '渲染引擎', value: preview.value?.renderEngine ?? '--' },
  { label: '路径提示', value: previewPathHint.value },
  { label: '可发布', value: preview.value ? (previewPublishReady.value ? '是' : '否') : '--' }
])
const previewSummaryCards = computed(() => [
  { label: '页面类型', value: String(previewSummary.value?.pageType ?? '--') },
  { label: '路径提示', value: String(previewSummary.value?.pathHint ?? '--') },
  { label: '布局槽位数', value: String(previewSummary.value?.layoutSlotCount ?? '--') },
  { label: '渲染块数量', value: String(previewSummary.value?.renderBlockCount ?? '--') }
])
const previewContextCards = computed(() => previewContextSections.map(section => ({
  key: section.key,
  title: section.title,
  data: previewContext.value?.[section.key] ?? {}
})))
const previewNoticeText = computed(() => {
  if (editorHasUnsavedPreviewChanges.value || previewIsStale.value) {
    return '当前有未保存变更，预览展示的是已保存版本。'
  }
  return '预览基于当前已保存版本生成，不会自动包含未保存的编辑内容。'
})
const previewNoticeClass = computed(() => editorHasUnsavedPreviewChanges.value || previewIsStale.value ? 'preview-notice warning' : 'preview-notice')
const bindingTargetTypeOptions = computed(() => {
  if (form.value.type === 'content_detail') {
    return [
      { value: 'site', label: '站点默认详情' },
      { value: 'column', label: '栏目默认详情' }
    ]
  }
  if (form.value.type === 'column_list') {
    return [{ value: 'column', label: '栏目' }]
  }
  return [{ value: 'site', label: '站点' }]
})
const bindingTargetOptions = computed(() => {
  if (bindingForm.targetType === 'site') {
    return sites.value
      .filter(item => item.id === form.value.siteId)
      .map(item => ({ id: item.id, label: item.name }))
  }
  return categoryOptions.value.map(item => ({ id: item.id, label: item.fullPath }))
})

function syncLoadedSnapshot() {
  loadedSnapshotFingerprint.value = editorSnapshotFingerprint.value
}

function applyPreviewSelectionDefaults() {
  if (previewForm.sourceType === 'sample') {
    previewForm.sourceId = null
    return
  }

  if (previewForm.sourceType === 'column') {
    if (!categoryOptions.value.some(item => item.id === previewForm.sourceId)) {
      previewForm.sourceId = categoryOptions.value[0]?.id ?? null
    }
    return
  }

  if (!articleOptions.value.some(item => item.id === previewForm.sourceId)) {
    previewForm.sourceId = articleOptions.value[0]?.id ?? null
  }
}

function resetBindingForm() {
  bindingForm.templateVersionId = currentVersionId.value
  bindingForm.replaceExisting = true

  if (form.value.type === 'home') {
    bindingForm.targetType = 'site'
    bindingForm.bindingSlot = 'site_home'
    bindingForm.targetId = form.value.siteId
    return
  }

  if (form.value.type === 'not_found') {
    bindingForm.targetType = 'site'
    bindingForm.bindingSlot = 'site_404'
    bindingForm.targetId = form.value.siteId
    return
  }

  if (form.value.type === 'column_list') {
    bindingForm.targetType = 'column'
    bindingForm.bindingSlot = 'column_list'
    bindingForm.targetId = categoryOptions.value[0]?.id ?? null
    return
  }

  bindingForm.targetType = 'site'
  bindingForm.bindingSlot = 'site_detail_default'
  bindingForm.targetId = form.value.siteId
}

function resetPreviewState() {
  preview.value = null
  previewSnapshotFingerprint.value = ''
  previewPanelTab.value = 'page'
  previewSchemaTab.value = 'layout'
  previewModalOpen.value = false
}

function resetForm(siteId: number | null = filters.siteId) {
  form.value = emptyForm(siteId)
  versions.value = []
  bindings.value = []
  impact.value = null
  syncLoadedSnapshot()
  resetBindingForm()
  resetPreviewState()
}

function handleCreateTemplate() {
  resetForm(filters.siteId)
  rightTab.value = 'preview'
}

async function fetchSites() {
  const res = await api.get('/sites', { params: { page: 0, size: 100, status: 'enabled' } })
  sites.value = unwrapList<SiteItem>(res.data)
  if (!filters.siteId && sites.value.length) {
    filters.siteId = sites.value[0].id
  }
}

async function fetchSupport(siteId?: number | null) {
  if (!siteId) {
    categoryOptions.value = []
    articleOptions.value = []
    applyPreviewSelectionDefaults()
    return
  }

  const [categoryRes, articleRes] = await Promise.all([
    api.get('/categories', { params: { siteId } }),
    api.get('/articles', { params: { siteId, page: 0, size: 100 } })
  ])

  categoryOptions.value = unwrapList<any>(categoryRes.data).map(item => ({
    id: item.id,
    name: item.name,
    fullPath: item.fullPath
  }))

  articleOptions.value = unwrapList<any>(articleRes.data).map(item => ({
    id: item.id,
    title: item.title,
    status: item.status
  }))

  applyPreviewSelectionDefaults()
}

async function fetchList() {
  if (!filters.siteId || !canView) {
    templates.value = []
    return
  }
  listLoading.value = true
  try {
    const res = await fetchTemplates({
      siteId: filters.siteId,
      type: filters.type || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined
    })
    templates.value = unwrapList<TemplateItem>(res.data)
    if (form.value.id && !templates.value.some(item => item.id === form.value.id)) {
      resetForm(filters.siteId)
    }
  } finally {
    listLoading.value = false
  }
}

async function loadWorkspace(id: number) {
  detailLoading.value = true
  try {
    const detail = (await fetchTemplateDetail(id, { siteId: filters.siteId || undefined })).data
    form.value = {
      ...emptyForm(detail.siteId),
      id: detail.id,
      siteId: detail.siteId,
      name: detail.name,
      code: detail.code,
      type: detail.type,
      status: detail.status,
      description: detail.description || '',
      defaultPreviewSource: detail.defaultPreviewSource || 'sample',
      layoutSchema: form.value.layoutSchema,
      blockSchema: form.value.blockSchema,
      seoSchema: form.value.seoSchema,
      styleSchema: form.value.styleSchema,
      changeLog: ''
    }

    await fetchSupport(detail.siteId)

    const [versionRes, bindingRes, impactRes] = await Promise.all([
      fetchTemplateVersions(id),
      fetchTemplateBindings(id, { siteId: detail.siteId }),
      fetchTemplateImpact(id, { siteId: detail.siteId })
    ])

    versions.value = unwrapList<TemplateVersionItem>(versionRes.data)
    bindings.value = unwrapList<TemplateBindingItem>(bindingRes.data)
    impact.value = impactRes.data

    const activeVersion = versions.value.find(item => item.id === detail.currentVersionId) ?? versions.value[0] ?? null
    if (activeVersion) {
      form.value.layoutSchema = activeVersion.layoutSchema
      form.value.blockSchema = activeVersion.blockSchema
      form.value.seoSchema = activeVersion.seoSchema || ''
      form.value.styleSchema = activeVersion.styleSchema || ''
    }

    previewForm.sourceType = form.value.defaultPreviewSource || 'sample'
    previewForm.sourceId = null
    applyPreviewSelectionDefaults()
    syncLoadedSnapshot()
    resetBindingForm()
    resetPreviewState()
  } finally {
    detailLoading.value = false
  }
}

async function selectTemplate(item: TemplateItem) {
  await loadWorkspace(item.id)
}

async function saveMeta() {
  const creating = !form.value.id
  if (!ensure(creating ? 'template:manage:create' : 'template:manage:update', creating ? '新建模板' : '编辑模板')) {
    return
  }
  if (!form.value.siteId || !form.value.name.trim() || !form.value.code.trim()) {
    message.error('请补全站点、模板名称和模板编码')
    return
  }

  saving.value = true
  try {
    if (creating) {
      const res = await createTemplate({
        siteId: form.value.siteId,
        name: form.value.name.trim(),
        code: form.value.code.trim(),
        type: form.value.type,
        status: form.value.status,
        description: form.value.description.trim() || null,
        layoutSchema: form.value.layoutSchema,
        blockSchema: form.value.blockSchema,
        seoSchema: form.value.seoSchema || null,
        styleSchema: form.value.styleSchema || null,
        changeLog: form.value.changeLog || '初始化版本',
        defaultPreviewSource: form.value.defaultPreviewSource || null
      })
      message.success('模板创建成功')
      await fetchList()
      await loadWorkspace(res.data.id)
    } else {
      await updateTemplate(form.value.id!, {
        siteId: form.value.siteId,
        name: form.value.name.trim(),
        code: form.value.code.trim(),
        type: form.value.type,
        status: form.value.status,
        description: form.value.description.trim() || null,
        defaultPreviewSource: form.value.defaultPreviewSource || null
      })
      message.success('模板信息已保存')
      await fetchList()
      await loadWorkspace(form.value.id!)
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存模板失败')
  } finally {
    saving.value = false
  }
}

async function saveVersion() {
  if (!form.value.id) {
    message.warning('请先创建模板')
    return
  }
  if (!ensure('template:manage:update', '保存新版本')) {
    return
  }

  savingVersion.value = true
  try {
    await createTemplateVersion(form.value.id, {
      layoutSchema: form.value.layoutSchema,
      blockSchema: form.value.blockSchema,
      seoSchema: form.value.seoSchema || null,
      styleSchema: form.value.styleSchema || null,
      changeLog: form.value.changeLog || '保存新版本'
    })
    message.success('新版本已保存')
    form.value.changeLog = ''
    await fetchList()
    await loadWorkspace(form.value.id!)
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存版本失败')
  } finally {
    savingVersion.value = false
  }
}

async function rollbackVersion(version: TemplateVersionItem) {
  if (!form.value.id || !form.value.siteId || !ensure('template:manage:update', '回滚版本')) {
    return
  }

  Modal.confirm({
    title: '回滚版本',
    content: `确认回滚到 V${version.versionNo} 吗？系统会生成一个新的回滚版本记录。`,
    onOk: async () => {
      await rollbackTemplateVersion(form.value.id!, {
        siteId: form.value.siteId!,
        versionId: version.id
      })
      message.success('已生成回滚版本')
      await fetchList()
      await loadWorkspace(form.value.id!)
    }
  })
}

async function saveBinding() {
  if (!form.value.id || !form.value.siteId || !ensure('template:manage:bind', '模板绑定')) {
    return
  }
  if (!bindingForm.targetId) {
    message.warning('请选择绑定目标')
    return
  }

  bindingLoading.value = true
  try {
    await createTemplateBinding(form.value.id, {
      siteId: form.value.siteId,
      targetType: bindingForm.targetType,
      targetId: bindingForm.targetId,
      bindingSlot: bindingForm.bindingSlot,
      templateVersionId: bindingForm.templateVersionId,
      replaceExisting: bindingForm.replaceExisting
    })
    message.success('模板绑定成功')
    await loadWorkspace(form.value.id!)
  } catch (error: any) {
    message.error(error.response?.data?.message || '模板绑定失败')
  } finally {
    bindingLoading.value = false
  }
}

async function removeBinding(item: TemplateBindingItem) {
  if (!ensure('template:manage:bind', '解除绑定')) {
    return
  }

  await deleteTemplateBinding(item.id, { siteId: item.siteId })
  message.success('绑定已解除')
  if (form.value.id) {
    await loadWorkspace(form.value.id!)
  }
}

async function runPreview() {
  if (!form.value.id || !form.value.siteId || !ensure('template:manage:preview', '预览模板')) {
    return
  }
  if (previewForm.sourceType !== 'sample' && !previewForm.sourceId) {
    message.warning('请选择预览对象')
    return
  }

  previewing.value = true
  try {
    const res = await previewTemplate(form.value.id, {
      siteId: form.value.siteId,
      sourceType: previewForm.sourceType,
      sourceId: previewForm.sourceType === 'sample' ? null : previewForm.sourceId
    })
    preview.value = res.data
    previewSnapshotFingerprint.value = loadedSnapshotFingerprint.value
    previewPanelTab.value = 'page'
    previewSchemaTab.value = 'layout'
    rightTab.value = 'preview'
  } catch (error: any) {
    message.error(error.response?.data?.message || '预览失败')
  } finally {
    previewing.value = false
  }
}

watch(() => filters.siteId, async value => {
  resetForm(value)
  await fetchSupport(value)
  await fetchList()
})

watch(() => form.value.siteId, async value => {
  await fetchSupport(value)
  resetBindingForm()
})

watch(() => form.value.type, () => {
  resetBindingForm()
})

watch(() => bindingForm.targetType, value => {
  if (value === 'column') {
    bindingForm.bindingSlot = form.value.type === 'column_list' ? 'column_list' : 'column_detail_default'
    bindingForm.targetId = categoryOptions.value[0]?.id ?? null
    return
  }

  bindingForm.bindingSlot = form.value.type === 'home'
    ? 'site_home'
    : form.value.type === 'not_found'
      ? 'site_404'
      : 'site_detail_default'
  bindingForm.targetId = form.value.siteId
})

watch(() => previewForm.sourceType, () => {
  applyPreviewSelectionDefaults()
})

onMounted(async () => {
  if (!canView) {
    return
  }

  try {
    await fetchSites()
    await fetchSupport(filters.siteId)
    await fetchList()
    syncLoadedSnapshot()
  } catch (error: any) {
    message.error(error.response?.data?.message || '初始化模板页面失败')
  }
})
</script>

<template>
  <div v-if="!canView" class="templates-page">
    <div class="empty-box">暂无模板查看权限</div>
  </div>
  <div v-else class="templates-page">
    <div class="page-header">
      <div>
        <h1>模板管理</h1>
        <p>模板主流程、栏目联动、绑定管理与渲染准备态预览</p>
      </div>
      <button class="primary-btn" @click="handleCreateTemplate">
        <PlusOutlined />
        新建模板
      </button>
    </div>

    <div class="toolbar">
      <select v-model="filters.siteId" class="filter-select">
        <option :value="null">请选择站点</option>
        <option v-for="item in sites" :key="item.id" :value="item.id">{{ item.name }}</option>
      </select>
      <select v-model="filters.type" class="filter-select">
        <option v-for="item in templateTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <select v-model="filters.status" class="filter-select">
        <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input v-model="filters.keyword" class="search-input" placeholder="搜索模板名称或编码" @keyup.enter="fetchList" />
      </div>
      <button class="secondary-btn" @click="fetchList">查询</button>
    </div>

    <div class="workspace">
      <aside class="left-panel">
        <div class="panel-title">模板列表</div>
        <div v-if="listLoading" class="empty-box">加载模板中...</div>
        <div v-else-if="!templates.length" class="empty-box">当前没有模板数据</div>
        <button
          v-for="item in templates"
          :key="item.id"
          class="list-item"
          :class="{ active: form.id === item.id }"
          @click="selectTemplate(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.code }} · {{ formatTemplateType(item.type) }}</span>
          <span>{{ formatStatus(item.status) }} / 绑定 {{ item.bindingCount }}</span>
        </button>
      </aside>

      <section class="middle-panel">
        <div v-if="detailLoading" class="empty-box">加载模板详情中...</div>
        <template v-else>
          <div class="panel-title">基础信息</div>
          <div class="form-grid">
            <input v-model="form.name" class="form-input" placeholder="模板名称" />
            <input v-model="form.code" class="form-input" placeholder="模板编码" />
            <select v-model="form.type" class="form-select">
              <option v-for="item in formTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <select v-model="form.status" class="form-select">
              <option v-for="item in statusOptions.filter(item => item.value)" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <select v-model="form.defaultPreviewSource" class="form-select">
              <option v-for="item in previewSourceOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <div class="static-field">
              <label>当前版本</label>
              <span>{{ currentVersion ? `V${currentVersion.versionNo}` : '--' }}</span>
            </div>
            <textarea v-model="form.description" class="form-textarea" rows="3" placeholder="模板说明"></textarea>
          </div>

          <div class="action-row">
            <button class="primary-btn" :disabled="saving" @click="saveMeta">保存信息</button>
            <button class="secondary-btn" :disabled="savingVersion || !form.id" @click="saveVersion">保存新版本</button>
            <input v-model="form.changeLog" class="form-input compact" placeholder="本次版本说明" />
          </div>

          <div class="panel-title">Schema 编辑</div>
          <div class="tab-row">
            <button
              v-for="item in ['layout', 'block', 'seo', 'style']"
              :key="item"
              class="tab-btn"
              :class="{ active: editorSchemaTab === item }"
              @click="editorSchemaTab = item as SchemaTab"
            >
              {{ item }}
            </button>
          </div>
          <textarea v-if="editorSchemaTab === 'layout'" v-model="form.layoutSchema" class="editor"></textarea>
          <textarea v-else-if="editorSchemaTab === 'block'" v-model="form.blockSchema" class="editor"></textarea>
          <textarea v-else-if="editorSchemaTab === 'seo'" v-model="form.seoSchema" class="editor"></textarea>
          <textarea v-else v-model="form.styleSchema" class="editor"></textarea>

          <div class="panel-title">绑定管理</div>
          <div class="form-grid binding-grid">
            <select v-model="bindingForm.targetType" class="form-select" :disabled="form.type !== 'content_detail'">
              <option v-for="item in bindingTargetTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <div class="static-field">
              <label>绑定槽位</label>
              <span>{{ formatBindingSlot(bindingForm.bindingSlot) }}</span>
            </div>
            <select v-model="bindingForm.targetId" class="form-select">
              <option :value="null">请选择绑定目标</option>
              <option v-for="item in bindingTargetOptions" :key="item.id" :value="item.id">{{ item.label }}</option>
            </select>
            <select v-model="bindingForm.templateVersionId" class="form-select">
              <option :value="null">使用当前版本</option>
              <option v-for="item in versions" :key="item.id" :value="item.id">V{{ item.versionNo }}</option>
            </select>
          </div>
          <div class="action-row compact-row">
            <label class="check">
              <input v-model="bindingForm.replaceExisting" type="checkbox" />
              替换现有绑定
            </label>
            <button class="secondary-btn" :disabled="bindingLoading || !form.id" @click="saveBinding">创建 / 替换绑定</button>
          </div>
          <div v-if="bindings.length" class="mini-list">
            <div v-for="item in bindings" :key="item.id" class="mini-item vertical">
              <div class="binding-head">
                <strong>{{ formatBindingSlot(item.bindingSlot) }}</strong>
                <span class="status-text">{{ formatStatus(item.status) }}</span>
              </div>
              <span>{{ formatBindingTarget(item) }}</span>
              <span>模板版本：{{ item.templateVersionId ? `#${item.templateVersionId}` : '当前版本' }}</span>
              <button class="link-btn" @click="removeBinding(item)">解除绑定</button>
            </div>
          </div>
          <div v-else class="empty-box small">当前模板还没有绑定记录</div>
        </template>
      </section>

      <aside class="right-panel">
        <div class="tab-row top-tabs">
          <button class="tab-btn" :class="{ active: rightTab === 'preview' }" @click="rightTab = 'preview'">预览</button>
          <button class="tab-btn" :class="{ active: rightTab === 'impact' }" @click="rightTab = 'impact'">影响范围</button>
          <button class="tab-btn" :class="{ active: rightTab === 'versions' }" @click="rightTab = 'versions'">版本记录</button>
        </div>

        <template v-if="rightTab === 'preview'">
          <div class="preview-toolbar">
            <select v-model="previewForm.sourceType" class="form-select compact-control">
              <option v-for="item in previewSourceOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <select v-if="previewForm.sourceType === 'column'" v-model="previewForm.sourceId" class="form-select grow-control">
              <option :value="null">请选择栏目</option>
              <option v-for="item in categoryOptions" :key="item.id" :value="item.id">{{ item.fullPath }}</option>
            </select>
            <select v-if="previewForm.sourceType === 'content'" v-model="previewForm.sourceId" class="form-select grow-control">
              <option :value="null">请选择内容</option>
              <option v-for="item in articleOptions" :key="item.id" :value="item.id">{{ item.title }}（{{ item.status }}）</option>
            </select>
            <button class="primary-btn" :disabled="previewing || !form.id" @click="runPreview">
              <SyncOutlined v-if="previewing" spin />
              <span>{{ previewing ? '预览中...' : '发起预览' }}</span>
            </button>
            <button class="secondary-btn" :disabled="!previewHasHtml" @click="previewModalOpen = true">放大预览</button>
          </div>

          <div :class="previewNoticeClass">{{ previewNoticeText }}</div>

          <div v-if="preview" class="preview-shell">
            <div v-if="previewWarnings.length || previewBlockingReasons.length" class="alert-stack">
              <div v-if="previewWarnings.length" class="warn-box">
                <div class="warn-title">预览告警</div>
                <div v-for="item in previewWarnings" :key="item">{{ item }}</div>
              </div>
              <div v-if="previewBlockingReasons.length" class="warn-box blocking">
                <div class="warn-title">阻塞原因</div>
                <div v-for="item in previewBlockingReasons" :key="item">{{ item }}</div>
              </div>
            </div>

            <div class="tab-row sub-tabs">
              <button class="tab-btn" :class="{ active: previewPanelTab === 'page' }" @click="previewPanelTab = 'page'">页面预览</button>
              <button class="tab-btn" :class="{ active: previewPanelTab === 'contract' }" @click="previewPanelTab = 'contract'">渲染契约</button>
              <button class="tab-btn" :class="{ active: previewPanelTab === 'schema' }" @click="previewPanelTab = 'schema'">Schema</button>
              <button class="tab-btn" :class="{ active: previewPanelTab === 'html' }" @click="previewPanelTab = 'html'">HTML</button>
            </div>

            <template v-if="previewPanelTab === 'page'">
              <div class="info-grid info-grid-compact">
                <div v-for="item in previewInfoCards" :key="item.label" class="info-card">
                  <label>{{ item.label }}</label>
                  <span>{{ item.value }}</span>
                </div>
              </div>
              <div class="preview-stage">
                <div v-if="previewHasHtml" class="preview-frame-shell">
                  <iframe
                    class="preview-frame"
                    sandbox=""
                    :srcdoc="preview.renderedHtml"
                    title="模板真实预览"
                  ></iframe>
                  <div v-if="previewing" class="preview-loading-mask">正在生成新的预览...</div>
                </div>
                <div v-else class="empty-box preview-empty">
                  当前预览没有返回可渲染 HTML，请切换到“渲染契约”或“HTML”查看返回快照。
                </div>
              </div>
            </template>

            <template v-else-if="previewPanelTab === 'contract'">
              <div class="info-grid info-grid-compact">
                <div v-for="item in previewSummaryCards" :key="item.label" class="info-card">
                  <label>{{ item.label }}</label>
                  <span>{{ item.value }}</span>
                </div>
              </div>
              <div class="context-grid">
                <div v-for="item in previewContextCards" :key="item.key" class="context-card">
                  <div class="context-title">{{ item.title }}</div>
                  <pre class="contract-box">{{ pretty(item.data) }}</pre>
                </div>
              </div>
            </template>

            <template v-else-if="previewPanelTab === 'schema'">
              <div class="tab-row sub-tabs nested-tabs">
                <button
                  v-for="item in ['layout', 'block', 'seo', 'style']"
                  :key="item"
                  class="tab-btn"
                  :class="{ active: previewSchemaTab === item }"
                  @click="previewSchemaTab = item as SchemaTab"
                >
                  {{ item }}
                </button>
              </div>
              <pre class="code-box">{{
                previewSchemaTab === 'layout'
                  ? preview.layoutSchema
                  : previewSchemaTab === 'block'
                    ? preview.blockSchema
                    : previewSchemaTab === 'seo'
                      ? (preview.seoSchema || '{}')
                      : (preview.styleSchema || '{}')
              }}</pre>
            </template>

            <template v-else>
              <pre class="code-box html-box">{{ preview.renderedHtml || '<!-- 当前没有渲染 HTML -->' }}</pre>
            </template>
          </div>

          <div v-else class="empty-box preview-empty">
            选择数据源后发起预览，右侧会先展示真实页面，再提供渲染契约、Schema 和 HTML 调试视图。
          </div>
        </template>

        <template v-else-if="rightTab === 'impact'">
          <div class="action-row">
            <button class="secondary-btn" :disabled="!form.id" @click="form.id && loadWorkspace(form.id)">
              <SyncOutlined />
              刷新
            </button>
          </div>
          <div v-if="impact" class="snapshot">
            <div class="info-grid info-grid-compact">
              <div class="info-card">
                <label>有效绑定</label>
                <span>{{ impact.activeBindingCount }}</span>
              </div>
              <div class="info-card">
                <label>模板类型</label>
                <span>{{ formatTemplateType(impact.templateType) }}</span>
              </div>
            </div>
            <pre class="code-box">{{ pretty(impact.targetTypeCounts) }}</pre>
            <div class="mini-list">
              <div v-for="item in impact.sampleTargets" :key="item" class="mini-item">
                <span>{{ item }}</span>
              </div>
            </div>
            <div v-if="impact.warnings?.length" class="warn-box">
              <div class="warn-title">影响提示</div>
              <div v-for="item in impact.warnings" :key="item">{{ item }}</div>
            </div>
          </div>
          <div v-else class="empty-box small">选择模板后查看影响范围</div>
        </template>

        <template v-else>
          <div v-if="versions.length" class="mini-list">
            <div v-for="item in versions" :key="item.id" class="mini-item vertical">
              <div class="binding-head">
                <strong>V{{ item.versionNo }}</strong>
                <span>{{ item.createdAt || '--' }}</span>
              </div>
              <span>{{ item.changeLog || '无版本说明' }}</span>
              <button class="link-btn" @click="rollbackVersion(item)">回滚到此版本</button>
            </div>
          </div>
          <div v-else class="empty-box small">暂无版本记录</div>
        </template>
      </aside>
    </div>

    <Modal v-model:open="previewModalOpen" title="放大预览" width="1200px" :footer="null" destroy-on-close>
      <div class="preview-modal-body">
        <div class="info-grid info-grid-compact modal-meta">
          <div v-for="item in previewInfoCards" :key="`modal-${item.label}`" class="info-card">
            <label>{{ item.label }}</label>
            <span>{{ item.value }}</span>
          </div>
        </div>
        <div v-if="previewHasHtml" class="preview-frame-shell modal-frame-shell">
          <iframe class="preview-frame modal-frame" sandbox="" :srcdoc="preview?.renderedHtml" title="模板放大预览"></iframe>
        </div>
        <div v-else class="empty-box preview-empty">当前预览没有可显示的 HTML。</div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.templates-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workspace {
  display: grid;
  grid-template-columns: 280px 1fr 460px;
  gap: 16px;
}

.left-panel,
.middle-panel,
.right-panel {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px;
}

.left-panel,
.middle-panel,
.right-panel,
.preview-shell,
.snapshot,
.mini-list,
.alert-stack,
.preview-stage,
.preview-modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-header h1 {
  margin: 0;
  color: #0f172a;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.panel-title {
  font-weight: 600;
  color: #0f172a;
}

.list-item,
.primary-btn,
.secondary-btn,
.tab-btn,
.link-btn {
  border: none;
  cursor: pointer;
}

.list-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  color: #0f172a;
}

.list-item.active {
  background: #eff6ff;
  border: 1px solid #93c5fd;
}

.primary-btn,
.secondary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
}

.primary-btn {
  background: #2563eb;
  color: #ffffff;
}

.secondary-btn {
  background: #e2e8f0;
  color: #0f172a;
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.link-btn {
  padding: 0;
  background: transparent;
  color: #2563eb;
  text-align: left;
}

.filter-select,
.form-select,
.form-input,
.search-input,
.form-textarea,
.editor {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  box-sizing: border-box;
  font-size: 14px;
}

.search-box {
  position: relative;
  flex: 1;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}

.search-input {
  padding-left: 38px;
}

.form-grid,
.info-grid,
.context-grid {
  display: grid;
  gap: 12px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.binding-grid {
  align-items: stretch;
}

.form-textarea {
  grid-column: 1 / -1;
  resize: vertical;
}

.static-field {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  min-height: 44px;
}

.static-field label,
.info-card label {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.static-field span,
.info-card span {
  color: #0f172a;
  word-break: break-word;
}

.action-row,
.tab-row,
.preview-toolbar,
.compact-row,
.binding-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-row,
.preview-toolbar,
.compact-row {
  flex-wrap: wrap;
}

.compact {
  max-width: 240px;
}

.compact-control {
  max-width: 140px;
}

.grow-control {
  flex: 1;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #475569;
}

.editor,
.code-box,
.contract-box {
  min-height: 260px;
  font-family: Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  overflow: auto;
}

.editor {
  border: 1px solid #1e293b;
}

.contract-box {
  min-height: 180px;
  max-height: 240px;
}

.html-box {
  min-height: 420px;
}

.top-tabs {
  margin-bottom: 4px;
}

.sub-tabs {
  margin-top: 4px;
}

.tab-btn {
  padding: 8px 12px;
  border-radius: 999px;
  background: #eef2ff;
  color: #1e293b;
}

.tab-btn.active {
  background: #2563eb;
  color: #ffffff;
}

.preview-notice,
.warn-box,
.empty-box {
  padding: 14px;
  border-radius: 12px;
}

.preview-notice,
.empty-box {
  background: #f8fafc;
  color: #475569;
}

.preview-notice.warning {
  background: #fff7ed;
  color: #9a3412;
}

.warn-box {
  background: #fff7ed;
  color: #9a3412;
}

.warn-box.blocking {
  background: #fef2f2;
  color: #991b1b;
}

.warn-title,
.context-title {
  font-weight: 600;
}

.small {
  min-height: 120px;
}

.status-text {
  font-size: 12px;
  color: #64748b;
}

.info-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.info-grid-compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.info-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
}

.context-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.context-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-stage {
  min-height: 480px;
}

.preview-frame-shell {
  position: relative;
  min-height: 480px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
}

.preview-frame {
  display: block;
  width: 100%;
  min-height: 480px;
  height: 100%;
  border: none;
  background: #ffffff;
}

.modal-frame-shell {
  min-height: 70vh;
}

.modal-frame {
  min-height: 70vh;
}

.preview-loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.72);
  color: #1d4ed8;
  font-weight: 600;
}

.preview-empty {
  min-height: 220px;
  display: flex;
  align-items: center;
}

.preview-modal-body {
  min-height: 200px;
}

.modal-meta {
  margin-bottom: 4px;
}

@media (max-width: 1600px) {
  .workspace {
    grid-template-columns: 260px 1fr 420px;
  }
}

@media (max-width: 1400px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .toolbar,
  .preview-toolbar,
  .action-row,
  .compact-row,
  .tab-row {
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid,
  .info-grid,
  .info-grid-compact,
  .context-grid {
    grid-template-columns: 1fr;
  }

  .compact,
  .compact-control,
  .grow-control {
    max-width: none;
    width: 100%;
  }
}
</style>

