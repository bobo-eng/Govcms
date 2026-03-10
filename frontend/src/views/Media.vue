<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EyeOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import api from '../utils/api'

interface MediaFile {
  id: number
  originalName: string
  mimeType: string
  extension: string
  fileSize: number
  mediaType: 'image' | 'document'
  uploadedBy: string
  createdAt: string
  updatedAt: string
}

interface ErrorResponse {
  response?: {
    data?: {
      message?: string
    }
  }
}

const { hasPermission } = usePermission()
const canUploadMedia = hasPermission('media:manage:upload')
const canDeleteMedia = hasPermission('media:manage:delete')

const loading = ref(false)
const uploading = ref(false)
const mediaFiles = ref<MediaFile[]>([])
const searchKeyword = ref('')
const filterType = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const acceptedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt']
const acceptedFileTypes = '.jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt'
const typeOptions = [
  { value: '', label: '全部类型' },
  { value: 'image', label: '图片' },
  { value: 'document', label: '文档' }
]

const getErrorMessage = (error: unknown, fallback: string) => {
  const errorWithResponse = error as ErrorResponse
  return errorWithResponse.response?.data?.message || fallback
}

const ensurePermission = (permissionCode: string, actionName: string) => {
  if (hasPermission(permissionCode)) {
    return true
  }
  message.warning(`暂无${actionName}权限`)
  return false
}

const fetchMediaFiles = async () => {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: pagination.value.current - 1,
      size: pagination.value.pageSize
    }

    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    if (filterType.value) {
      params.type = filterType.value
    }

    const response = await api.get('/media', { params })
    mediaFiles.value = response.data.content || []
    pagination.value.total = response.data.totalElements || 0
  } catch (error: unknown) {
    console.error('Failed to fetch media files:', error)
    message.error(getErrorMessage(error, '获取媒体列表失败'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.current = 1
  fetchMediaFiles()
}

const handlePageChange = (page: number, pageSize: number) => {
  pagination.value.current = page
  pagination.value.pageSize = pageSize
  fetchMediaFiles()
}

const triggerUpload = () => {
  if (!ensurePermission('media:manage:upload', '上传媒体')) {
    return
  }
  fileInput.value?.click()
}

const validateFile = (file: File) => {
  if (file.size > 20 * 1024 * 1024) {
    message.error('上传文件不能超过 20MB')
    return false
  }

  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() || '' : ''
  if (!acceptedExtensions.includes(extension)) {
    message.error('仅支持上传图片或文档文件')
    return false
  }

  return true
}

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  if (!ensurePermission('media:manage:upload', '上传媒体')) {
    input.value = ''
    return
  }

  if (!validateFile(file)) {
    input.value = ''
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  uploading.value = true
  try {
    await api.post('/media/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    message.success('媒体上传成功')
    pagination.value.current = 1
    fetchMediaFiles()
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '上传媒体失败'))
  } finally {
    uploading.value = false
    input.value = ''
  }
}

const handleOpen = async (record: MediaFile) => {
  if (!ensurePermission('media:manage:view', '查看媒体')) {
    return
  }

  try {
    const response = await api.get(`/media/${record.id}/preview`, {
      responseType: 'blob'
    })
    const mimeType = (response.data as Blob).type || record.mimeType || 'application/octet-stream'
    const blob = new Blob([response.data], { type: mimeType })
    const objectUrl = URL.createObjectURL(blob)

    if (record.mediaType === 'image' || record.extension === 'pdf') {
      window.open(objectUrl, '_blank', 'noopener,noreferrer')
    } else {
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = record.originalName
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    }

    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '打开媒体文件失败'))
  }
}

const handleDelete = (record: MediaFile) => {
  if (!ensurePermission('media:manage:delete', '删除媒体')) {
    return
  }

  Modal.confirm({
    title: '删除媒体文件',
    content: `确认删除文件“${record.originalName}”吗？此操作不可恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.delete(`/media/${record.id}`)
        message.success('媒体文件删除成功')
        if (mediaFiles.value.length === 1 && pagination.value.current > 1) {
          pagination.value.current -= 1
        }
        fetchMediaFiles()
      } catch (error: unknown) {
        message.error(getErrorMessage(error, '删除媒体文件失败'))
      }
    }
  })
}

const formatFileSize = (fileSize: number) => {
  if (fileSize < 1024) {
    return `${fileSize} B`
  }
  if (fileSize < 1024 * 1024) {
    return `${(fileSize / 1024).toFixed(1)} KB`
  }
  return `${(fileSize / (1024 * 1024)).toFixed(2)} MB`
}

const getMediaTypeText = (mediaType: string) => {
  return mediaType === 'image' ? '图片' : '文档'
}

const getMediaTypeClass = (mediaType: string) => {
  return mediaType === 'image' ? 'image' : 'document'
}

const formatDateTime = (value: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchMediaFiles()
})
</script>

<template>
  <div class="media-page">
    <div class="page-header">
      <div class="header-left">
        <h1>媒体管理</h1>
        <p>管理图片和文档文件，支持上传、筛选、打开与删除</p>
      </div>
      <button v-if="canUploadMedia" class="primary-btn" :disabled="uploading" @click="triggerUpload">
        <UploadOutlined />
        <span>{{ uploading ? '上传中...' : '上传文件' }}</span>
      </button>
      <input
        ref="fileInput"
        type="file"
        class="hidden-input"
        :accept="acceptedFileTypes"
        @change="handleFileChange"
      />
    </div>

    <div class="filter-card">
      <div class="filter-grid">
        <div class="filter-item keyword-item">
          <label>关键字</label>
          <input v-model="searchKeyword" type="text" placeholder="请输入文件名关键字" @keyup.enter="handleSearch" />
        </div>
        <div class="filter-item">
          <label>类型</label>
          <select v-model="filterType">
            <option v-for="item in typeOptions" :key="item.value || 'all'" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="secondary-btn" @click="handleSearch">
            <SearchOutlined />
            <span>查询</span>
          </button>
        </div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <span>共 {{ pagination.total }} 个媒体文件</span>
      </div>

      <div class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>文件名</th>
              <th>类型</th>
              <th>MIME</th>
              <th>大小</th>
              <th>上传人</th>
              <th>上传时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody v-if="!loading && mediaFiles.length">
            <tr v-for="record in mediaFiles" :key="record.id">
              <td>
                <div class="file-name">
                  <span class="file-title">{{ record.originalName }}</span>
                  <span class="file-extension">.{{ record.extension }}</span>
                </div>
              </td>
              <td>
                <span class="tag" :class="getMediaTypeClass(record.mediaType)">{{ getMediaTypeText(record.mediaType) }}</span>
              </td>
              <td>{{ record.mimeType }}</td>
              <td>{{ formatFileSize(record.fileSize) }}</td>
              <td>{{ record.uploadedBy }}</td>
              <td>{{ formatDateTime(record.createdAt) }}</td>
              <td>
                <div class="action-group">
                  <button class="link-btn" @click="handleOpen(record)">
                    <EyeOutlined />
                    <span>{{ record.mediaType === 'image' || record.extension === 'pdf' ? '预览' : '打开' }}</span>
                  </button>
                  <button v-if="canDeleteMedia" class="link-btn danger" @click="handleDelete(record)">
                    <DeleteOutlined />
                    <span>删除</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
          <tbody v-else-if="!loading">
            <tr>
              <td colspan="7" class="empty-cell">暂无媒体文件</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="loading" class="table-loading">正在加载媒体列表...</div>

      <div class="pagination-wrapper">
        <a-pagination
          :current="pagination.current"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          show-size-changer
          :page-size-options="['10', '20', '50']"
          @change="handlePageChange"
          @showSizeChange="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.media-page {
  max-width: 1400px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.header-left h1 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 600;
  color: #0f172a;
}

.header-left p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.primary-btn,
.secondary-btn,
.link-btn {
  border: none;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.primary-btn {
  padding: 0 18px;
  height: 40px;
  background: #2563eb;
  color: #fff;
  font-weight: 500;
  box-shadow: 0 6px 14px -8px rgba(37, 99, 235, 0.7);
}

.primary-btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.secondary-btn {
  height: 40px;
  padding: 0 16px;
  background: #e2e8f0;
  color: #1e293b;
}

.secondary-btn:hover {
  background: #cbd5e1;
}

.hidden-input {
  display: none;
}

.filter-card,
.table-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 8px 32px -24px rgba(15, 23, 42, 0.2);
}

.filter-card {
  padding: 20px;
  margin-bottom: 20px;
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(280px, 1.8fr) minmax(180px, 1fr) auto;
  gap: 16px;
  align-items: end;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item label {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}

.filter-item input,
.filter-item select {
  height: 40px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  padding: 0 14px;
  font-size: 14px;
  color: #0f172a;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.filter-item input:focus,
.filter-item select:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.table-card {
  padding: 20px;
}

.table-toolbar {
  margin-bottom: 16px;
  color: #475569;
  font-size: 14px;
}

.table-wrapper {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 980px;
}

.data-table th,
.data-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  vertical-align: middle;
}

.data-table th {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}

.data-table td {
  color: #0f172a;
  font-size: 14px;
}

.file-name {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-title {
  font-weight: 500;
}

.file-extension {
  color: #64748b;
  font-size: 12px;
}

.tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.tag.image {
  background: #dbeafe;
  color: #1d4ed8;
}

.tag.document {
  background: #dcfce7;
  color: #15803d;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.link-btn {
  background: transparent;
  padding: 0;
  color: #2563eb;
  font-size: 14px;
}

.link-btn:hover {
  color: #1d4ed8;
}

.link-btn.danger {
  color: #dc2626;
}

.link-btn.danger:hover {
  color: #b91c1c;
}

.empty-cell,
.table-loading {
  text-align: center;
  color: #94a3b8;
  padding: 48px 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }

  .pagination-wrapper {
    justify-content: center;
  }
}
</style>