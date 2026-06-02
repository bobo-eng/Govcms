# Audit Log UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a paginated, filterable Audit Log admin UI page under System Management, with backend pagination support.

**Architecture:** Follow existing Spring Data JPA `Pageable` pattern (same as SiteController/UserController). Frontend uses Ant Design Vue `a-table` with expandable rows, matching the existing admin-refresh.css style used by Users.vue and Sites.vue.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Data JPA, Vue 3, TypeScript, Ant Design Vue 4, Vite

---

### Task 1: Backend — Add pagination to AuditLogRepository

**Files:**
- Modify: `src/main/java/gov/cms/admin/repository/AuditLogRepository.java`

**Step 1: Change return type from List to Page and add Pageable parameter**

```java
package gov.cms.admin.repository;

import gov.cms.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:siteId IS NULL OR a.siteId = :siteId)
              AND (:actionType IS NULL OR :actionType = '' OR a.actionType = :actionType)
              AND (:result IS NULL OR :result = '' OR a.result = :result)
              AND (:operatorName IS NULL OR :operatorName = '' OR a.operatorName LIKE %:operatorName%)
            """)
    Page<AuditLog> search(@Param("siteId") Long siteId,
                          @Param("actionType") String actionType,
                          @Param("result") String result,
                          @Param("operatorName") String operatorName,
                          Pageable pageable);

    List<AuditLog> findByRelatedJobIdOrderByCreatedAtDescIdDesc(Long relatedJobId);

    AuditLog findFirstBySiteIdAndObjectTypeOrderByCreatedAtDescIdDesc(Long siteId, String objectType);

    AuditLog findFirstBySiteIdAndObjectTypeAndResultOrderByCreatedAtDescIdDesc(Long siteId, String objectType, String result);
}
```

**Note:** `operatorName` filter changed from exact match to `LIKE %:operatorName%` for fuzzy search.

**Step 2: Run compilation to verify no syntax errors**

Run: `mvn compile -pl . -am`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/repository/AuditLogRepository.java
git commit -m "feat: add pagination support to AuditLogRepository"
```

---

### Task 2: Backend — Update AuditLogService to accept Pageable

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/AuditLogService.java`

**Step 1: Add Pageable import and update list method signature**

```java
package gov.cms.admin.service;

import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final SiteAccessService siteAccessService;

    public AuditLogService(AuditLogRepository auditLogRepository, SiteAccessService siteAccessService) {
        this.auditLogRepository = auditLogRepository;
        this.siteAccessService = siteAccessService;
    }

    public AuditLog record(String actionType,
                           String objectType,
                           Long objectId,
                           Long siteId,
                           String result,
                           String summary,
                           String failureReason,
                           Long relatedJobId) {
        AuditLog log = new AuditLog();
        log.setActionType(actionType);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setSiteId(siteId);
        log.setResult(result);
        log.setSummary(summary);
        log.setFailureReason(failureReason);
        log.setRelatedJobId(relatedJobId);
        log.setOperatorName(resolveOperatorName());
        return auditLogRepository.save(log);
    }

    public Page<AuditLog> list(Long siteId, String actionType, String result, String operatorName, Pageable pageable) {
        Long accessibleSiteId = siteAccessService.isScopedSiteAdmin() ? siteAccessService.resolveAccessibleSiteId(siteId) : siteId;
        return auditLogRepository.search(accessibleSiteId, actionType, result, operatorName, pageable);
    }

    public java.util.List<AuditLog> listByJobId(Long jobId) {
        return auditLogRepository.findByRelatedJobIdOrderByCreatedAtDescIdDesc(jobId);
    }

    private String resolveOperatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }
}
```

**Step 2: Run compilation**

Run: `mvn compile -pl . -am`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/AuditLogService.java
git commit -m "feat: add Pageable support to AuditLogService"
```

---

### Task 3: Backend — Update AuditLogController to return Page and accept Pageable

**Files:**
- Modify: `src/main/java/gov/cms/admin/controller/AuditLogController.java`

**Step 1: Replace the entire file with paginated version**

```java
package gov.cms.admin.controller;

import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<Page<AuditLog>> getLogs(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String operatorName,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditLogService.list(siteId, actionType, result, operatorName, pageable));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAuthority('publish:center:view')")
    public ResponseEntity<List<AuditLog>> getJobLogs(@PathVariable Long jobId) {
        return ResponseEntity.ok(auditLogService.listByJobId(jobId));
    }
}
```

**Step 2: Run compilation and existing tests**

Run: `mvn test -Dtest=AuditLogServiceTest`
Expected: Tests pass (or no tests fail due to these changes)

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/AuditLogController.java
git commit -m "feat: add pagination to AuditLogController"
```

---

### Task 4: Backend — Add audit log menu to DataInitializer

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/DataInitializer.java`

**Step 1: Add audit log menu entry in seedMenus method**

Find the line after:
```java
upsertMenu(menuRepository, createMenu("搜索运营", "/search-ops", "SearchOutlined", null, 9, "search:ops"), null);
```

Add after it:
```java
upsertMenu(menuRepository, createMenu("审计日志", "/system/audit-logs", "AuditOutlined", null, 10, "publish:center"), null);
```

**Step 2: Commit**

```bash
git add src/main/java/gov/cms/admin/config/DataInitializer.java
git commit -m "feat: add audit log menu entry"
```

---

### Task 5: Frontend — Create auditLogs API module

**Files:**
- Create: `frontend/src/api/auditLogs.ts`

**Step 1: Write the API module**

```typescript
import api from '../utils/api'

export interface AuditLogQueryParams {
  siteId?: number | null
  actionType?: string
  result?: string
  operatorName?: string
  page?: number
  size?: number
}

export interface AuditLogItem {
  id: number
  actionType: string
  objectType: string
  objectId: number | null
  siteId: number | null
  operatorName: string
  result: string
  summary: string | null
  failureReason: string | null
  relatedJobId: number | null
  createdAt: string
}

export interface AuditLogPage {
  content: AuditLogItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const getAuditLogs = (params: AuditLogQueryParams) =>
  api.get<AuditLogPage>('/audit-logs', { params })
```

**Step 2: Commit**

```bash
git add frontend/src/api/auditLogs.ts
git commit -m "feat: add auditLogs API module"
```

---

### Task 6: Frontend — Add AuditLogs route

**Files:**
- Modify: `frontend/src/router/index.ts`

**Step 1: Import AuditLogs component and add route**

Add import after existing imports:
```typescript
import AuditLogs from '../views/AuditLogs.vue'
```

Add route in the children array (after SearchOps):
```typescript
{ path: 'system/audit-logs', name: 'AuditLogs', component: AuditLogs }
```

**Step 2: Commit**

```bash
git add frontend/src/router/index.ts
git commit -m "feat: add AuditLogs route"
```

---

### Task 7: Frontend — Create AuditLogs.vue page

**Files:**
- Create: `frontend/src/views/AuditLogs.vue`

**Step 1: Write the complete page component**

```vue
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
```

**Step 2: Commit**

```bash
git add frontend/src/views/AuditLogs.vue
git commit -m "feat: add AuditLogs admin page"
```

---

### Task 8: Verification — Compile backend and type-check frontend

**Step 1: Compile backend**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 2: Type-check frontend**

Run:
```bash
cd frontend
npx vue-tsc --noEmit
```
Expected: No type errors

**Step 3: Run backend tests**

Run: `mvn test`
Expected: All tests pass

**Step 4: Final commit (if any fixes needed)**

If no fixes needed, no additional commit.

---

## Execution Handoff

**Plan complete and saved to `docs/plans/2026-06-02-audit-log-ui-implementation.md`. Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

**Which approach?**
