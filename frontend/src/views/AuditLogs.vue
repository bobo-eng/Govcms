<script setup lang="ts">
import '../styles/admin-refresh.css'

import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { usePermission } from '../composables/usePermission'
import { getAuditLogs, type AuditLogItem, type AuditLogQueryParams } from '../api/auditLogs'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'

const { hasPermission } = usePermission()
const canViewAuditLog = hasPermission('publish:center:view')

const loading = ref(false)
const auditLogs = ref<AuditLogItem[]>([])
const siteOptions = ref<SiteOptionItem[]>([])

const queryParams = ref<AuditLogQueryParams>(({
  siteId: null,
  actionType: undefined,
  result: undefined,
  operatorName: undefined,
  page: 0,
  size: 20
}))

const pagination = ref({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条`
})

const actionTypeOptions = [
  { label: '全部', value: undefined },
  { label: '发布', value: 'publish' },
  { label: '回滚', value: 'rollback' },
  { label: '创建', value: 'create' },
  { label: '更新', value: 'update' },
  { label: '删除', value: 'delete' }
]

const resultOptions = [
  { label: '全部', value: undefined },
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failure' }
]

const isScopedSiteAdmin = computed(() => {
  const roles = JSON.parse(localStorage.getItem('roles') || '[]')
  return roles.includes('site_admin')
})

const columns = [
  {
    title: '操作时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 180
  },
  {
    title: '操作人',
    dataIndex: 'operatorName',
    key: 'operatorName',
    width: 120
  },
  {
    title: '操作类型',
    dataIndex: 'actionType',
    key: 'actionType',
    width: 120
  },
  {
    title: '对象类型',
    dataIndex: 'objectType',
    key: 'objectType',
    width: 120
  },
  {
    title: '对象ID',
    dataIndex: 'objectId',
    key: 'objectId',
    width: 100
  },
  {
    title: '结果',
    dataIndex: 'result',
    key: 'result',
    width: 100
  }
]

const fetchAuditLogs = async () => {
  if (!canViewAuditLog) return
  loading.value = true
  try {
    const res = await getAuditLogs({
      ...queryParams.value,
      page: queryParams.value.page,
      size: queryParams.value.size
    })
    auditLogs.value = res.data.content || []
    pagination.value.total = res.data.totalElements || 0
    pagination.value.current = (res.data.number || 0) + 1
    pagination.value.pageSize = res.data.size || 20
  } catch (err: any) {
    message.error(err.response?.data?.error || '加载审计日志失败')
  } finally {
    loading.value = false
  }
}

const fetchSites = async () => {
  try {
    const res = await fetchSiteOptions()
    siteOptions.value = res.data || []
  } catch {
    // ignore
  }
}

const handleSearch = () => {
  queryParams.value.page = 0
  pagination.value.current = 1
  fetchAuditLogs()
}

const handleReset = () => {
  queryParams.value = {
    siteId: isScopedSiteAdmin.value ? siteOptions.value[0]?.id : null,
    actionType: undefined,
    result: undefined,
    operatorName: undefined,
    page: 0,
    size: 20
  }
  pagination.value.current = 1
  fetchAuditLogs()
}

const handleTableChange = (pag: any) => {
  queryParams.value.page = pag.current - 1
  queryParams.value.size = pag.pageSize
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchAuditLogs()
}

const getResultColor = (result: string) => {
  if (result === 'success') return 'success'
  if (result === 'failure') return 'error'
  return 'default'
}

const getResultText = (result: string) => {
  if (result === 'success') return '成功'
  if (result === 'failure') return '失败'
  return result
}

onMounted(() => {
  fetchSites().then(() => {
    if (isScopedSiteAdmin.value && siteOptions.value.length > 0) {
      queryParams.value.siteId = siteOptions.value[0].id
    }
    fetchAuditLogs()
  })
})
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <h1 class="admin-page-title">审计日志</h1>
    </div>

    <div v-if="!canViewAuditLog" class="admin-page-content">
      <a-alert type="warning" message="您没有查看审计日志的权限" />
    </div>

    <template v-else>
      <div class="admin-filter-bar">
        <a-select
          v-if="!isScopedSiteAdmin"
          v-model:value="queryParams.siteId"
          placeholder="选择站点"
          style="width: 160px"
          allow-clear
          :options="siteOptions.map(s => ({ label: s.name, value: s.id }))"
        />
        <a-select
          v-model:value="queryParams.actionType"
          placeholder="操作类型"
          style="width: 140px"
          allow-clear
          :options="actionTypeOptions"
        />
        <a-select
          v-model:value="queryParams.result"
          placeholder="结果"
          style="width: 120px"
          allow-clear
          :options="resultOptions"
        />
        <a-input
          v-model:value="queryParams.operatorName"
          placeholder="操作人"
          style="width: 160px"
          allow-clear
        />
        <a-button type="primary" @click="handleSearch">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>

      <div class="admin-page-content">
        <a-table
          :columns="columns"
          :data-source="auditLogs"
          :loading="loading"
          :pagination="pagination"
          row-key="id"
          size="middle"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'result'">
              <a-tag :color="getResultColor(record.result)">
                {{ getResultText(record.result) }}
              </a-tag>
            </template>
          </template>

          <template #expandedRowRender="{ record }">
            <div class="audit-log-detail">
              <p v-if="record.summary"><strong>摘要：</strong>{{ record.summary }}</p>
              <p v-if="record.failureReason" class="failure-reason">
                <strong>失败原因：</strong>{{ record.failureReason }}
              </p>
              <p v-if="record.relatedJobId"><strong>关联任务ID：</strong>{{ record.relatedJobId }}</p>
            </div>
          </template>
        </a-table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.audit-log-detail {
  padding: 8px 16px;
  background: #fafafa;
  border-radius: 4px;
}
.failure-reason {
  color: #cf1322;
}
</style>
