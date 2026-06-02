<script setup lang="ts">
import '../styles/admin-refresh.css'

import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface MediaReferenceItem {
  objectType: string
  objectId: number
  title: string
  path?: string | null
}

interface MediaItem {
  id: number
  filename: string
  originalFilename?: string | null
  contentType?: string | null
  fileSize?: number | null
  storagePath?: string | null
  previewUrl?: string | null
  status?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  references?: MediaReferenceItem[]
  referenceCount?: number
  fileMissing?: boolean
}

const { hasPermission } = usePermission()
const canUpload = hasPermission('media:manage:upload')
const canDelete = hasPermission('media:manage:delete')

const loading = ref(false)
const uploading = ref(false)
const rows = ref<MediaItem[]>([])
const pagination = ref({ current: 1, pageSize: 12, total: 0 })
const filters = ref({ keyword: '', type: '', fileState: '', referenced: '' })
const fileInput = ref<HTMLInputElement | null>(null)

const loadMedia = async () => {
  loading.value = true
  try {
    const response = await api.get('/media', {
      params: {
        page: pagination.value.current - 1,
        size: pagination.value.pageSize,
        keyword: filters.value.keyword || undefined,
        contentType: filters.value.type || undefined
      }
    })
    rows.value = (response.data.content || []).filter((item: MediaItem) => {
      if (filters.value.fileState === 'missing' && !item.fileMissing) return false
      if (filters.value.fileState === 'ok' && item.fileMissing) return false
      if (filters.value.referenced === 'yes' && !(item.referenceCount && item.referenceCount > 0)) return false
      if (filters.value.referenced === 'no' && item.referenceCount && item.referenceCount > 0) return false
      return true
    })
    pagination.value.total = response.data.totalElements || 0
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载媒体列表失败')
  } finally {
    loading.value = false
  }
}

const triggerUpload = () => {
  fileInput.value?.click()
}

const handleUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files?.length) {
    return
  }
  const formData = new FormData()
  Array.from(files).forEach(file => formData.append('files', file))
  uploading.value = true
  try {
    await api.post('/media/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    message.success('媒体上传成功')
    await loadMedia()
  } catch (error: any) {
    message.error(error.response?.data?.message || '媒体上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

const handleDelete = async (record: MediaItem) => {
  if (!canDelete) {
    message.warning('暂无删除媒体权限')
    return
  }
  if (!window.confirm(`确认删除媒体“${record.originalFilename || record.filename}”吗？`)) {
    return
  }
  try {
    await api.delete(`/media/${record.id}`)
    message.success('媒体删除成功')
    await loadMedia()
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除媒体失败')
  }
}

const formatFileSize = (size?: number | null) => {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

const formatDate = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 16) : '-'
const fileStateLabel = (item: MediaItem) => item.fileMissing ? '文件缺失' : '文件正常'
const fileStateClass = (item: MediaItem) => item.fileMissing ? 'admin-status-badge--default' : 'admin-status-badge--success'

const handlePageChange = (page: number) => {
  pagination.value.current = page
  loadMedia()
}

onMounted(() => {
  loadMedia()
})
</script>

<template>
  <div class="admin-page media-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">媒体管理</h1>
        <p class="admin-page-desc">管理上传文件、引用状态、文件有效性与删除保护。</p>
      </div>
      <button v-if="canUpload" class="admin-primary-btn" :disabled="uploading" @click="triggerUpload">
        <PlusOutlined />
        <span>{{ uploading ? '上传中...' : '上传媒体' }}</span>
      </button>
      <input ref="fileInput" type="file" multiple class="hidden-input" @change="handleUpload" />
    </div>

    <div class="admin-toolbar-card">
      <div class="admin-toolbar-row">
        <div class="admin-search-box">
          <SearchOutlined class="admin-search-icon" />
          <input v-model="filters.keyword" class="admin-search-input" placeholder="搜索文件名或原始文件名" @keyup.enter="loadMedia" />
        </div>
        <input v-model="filters.type" class="admin-form-input media-type-filter" placeholder="内容类型，例如 image/png" @keyup.enter="loadMedia" />
        <select v-model="filters.fileState" class="admin-filter-select" @change="loadMedia">
          <option value="">全部文件状态</option>
          <option value="ok">文件正常</option>
          <option value="missing">文件缺失</option>
        </select>
        <select v-model="filters.referenced" class="admin-filter-select" @change="loadMedia">
          <option value="">全部引用状态</option>
          <option value="yes">已被引用</option>
          <option value="no">未被引用</option>
        </select>
        <button class="admin-secondary-btn" @click="loadMedia">查询</button>
      </div>
    </div>

    <div class="admin-table-card">
      <div class="media-toolbar">共 {{ pagination.total }} 个媒体文件</div>
      <table class="admin-data-table">
        <thead>
          <tr>
            <th>文件</th>
            <th>大小</th>
            <th>内容类型</th>
            <th>文件状态</th>
            <th>引用情况</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="admin-empty-cell">加载中...</td></tr>
          <tr v-else-if="!rows.length"><td colspan="7" class="admin-empty-cell">暂无媒体数据</td></tr>
          <tr v-for="item in rows" :key="item.id">
            <td>
              <div class="media-name-cell">
                <strong>{{ item.originalFilename || item.filename }}</strong>
                <span class="admin-sub-text">{{ item.filename }}</span>
              </div>
            </td>
            <td>{{ formatFileSize(item.fileSize) }}</td>
            <td>{{ item.contentType || '-' }}</td>
            <td><span :class="['admin-status-badge', fileStateClass(item)]">{{ fileStateLabel(item) }}</span></td>
            <td>
              <div class="media-reference-cell">
                <span>{{ item.referenceCount || 0 }} 个引用</span>
                <span class="admin-sub-text">{{ item.references?.[0]?.title || '暂无引用摘要' }}</span>
              </div>
            </td>
            <td class="admin-muted-cell">{{ formatDate(item.updatedAt || item.createdAt) }}</td>
            <td>
              <div class="media-actions">
                <a v-if="item.previewUrl" class="admin-link-action" :href="item.previewUrl" target="_blank" rel="noopener">预览</a>
                <button v-if="canDelete" class="admin-icon-btn admin-icon-btn--danger" @click="handleDelete(item)"><DeleteOutlined /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="admin-pagination">
        <span class="admin-pagination-total">共 {{ pagination.total }} 条</span>
        <div class="admin-pagination-controls">
          <button class="admin-page-btn" :disabled="pagination.current === 1" @click="handlePageChange(pagination.current - 1)">上一页</button>
          <span class="admin-page-info">{{ pagination.current }} / {{ Math.ceil(pagination.total / pagination.pageSize) || 1 }}</span>
          <button class="admin-page-btn" :disabled="pagination.current >= Math.ceil(pagination.total / pagination.pageSize)" @click="handlePageChange(pagination.current + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.media-page .hidden-input {
  display: none;
}

.media-page .media-toolbar {
  padding: 16px 16px 0;
  color: #475569;
  font-size: 14px;
  line-height: 22px;
}

.media-page .media-type-filter {
  width: 220px;
}

.media-page .media-name-cell,
.media-page .media-reference-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.media-page .media-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 1080px) {
  .media-page .media-type-filter {
    width: 100%;
  }
}
</style>
