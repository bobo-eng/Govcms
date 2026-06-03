# Quartz Multi-Environment Publishing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace synchronous publish with Quartz-scheduled async publish, add staging/production environments, and implement approval workflow.

**Architecture:** Use Spring Boot Starter Quartz with JDBC job store for persistence. Extend PublishJob entity with environment and approval state. Split execution into Quartz Job with state machine transitions. Artifact paths are isolated by siteId/environment. The existing synchronous flow is preserved as an internal executor while the outer API becomes async.

**Tech Stack:** Java 17, Spring Boot 3.2, Quartz Scheduler, Spring Data JPA, Thymeleaf

---

### Task 1: Add Quartz dependency

**Files:**
- Modify: `pom.xml`

**Step 1: Add Spring Boot Quartz starter**

Add inside `<dependencies>` after `spring-boot-starter-thymeleaf`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

**Step 2: Compile to verify dependency resolution**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: add Spring Boot Quartz starter dependency"
```

---

### Task 2: Configure Quartz properties

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: Add Quartz configuration block**

Insert after the existing `jwt:` block:

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always
    properties:
      org:
        quartz:
          scheduler:
            instanceName: govcms-scheduler
            instanceId: AUTO
          jobStore:
            class: org.quartz.impl.jdbcjobstore.JobStoreTX
            driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
            tablePrefix: QRTZ_
            useProperties: false
            isClustered: false
          threadPool:
            class: org.quartz.simpl.SimpleThreadPool
            threadCount: 5
```

**Step 2: Start application to verify Quartz tables auto-create**

Run: `mvn spring-boot:run`
Expected: Application starts without Quartz errors; database should contain new QRTZ_* tables.
Stop after verification with Ctrl+C.

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add Quartz JDBC job store configuration"
```

---

### Task 3: Create PublishEnvironment enum

**Files:**
- Create: `src/main/java/gov/cms/admin/entity/PublishEnvironment.java`

**Step 1: Write enum**

```java
package gov.cms.admin.entity;

public enum PublishEnvironment {
    STAGING,
    PRODUCTION
}
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/entity/PublishEnvironment.java
git commit -m "feat: add PublishEnvironment enum for multi-env publishing"
```

---

### Task 4: Extend PublishJob entity

**Files:**
- Modify: `src/main/java/gov/cms/admin/entity/PublishJob.java`

**Step 1: Add new fields with getters/setters**

Add these fields after `finishedAt`:

```java
    @Column(length = 20)
    private String environment;

    @Column(length = 20)
    private String approvalStatus;

    @Column
    private LocalDateTime scheduledAt;

    @Column(length = 64)
    private String previewToken;
```

Add getters/setters at the end of the class, after `setFinishedAt`:

```java
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getPreviewToken() { return previewToken; }
    public void setPreviewToken(String previewToken) { this.previewToken = previewToken; }
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/entity/PublishJob.java
git commit -m "feat: extend PublishJob with environment, approval, schedule and preview token"
```

---

### Task 5: Create PublishStateMachine helper

**Files:**
- Create: `src/main/java/gov/cms/admin/service/PublishStateMachine.java`

**Step 1: Write state transition validator**

```java
package gov.cms.admin.service;

import java.util.Map;
import java.util.Set;

public class PublishStateMachine {

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "created", Set.of("queued", "failed"),
            "queued", Set.of("staging_rendering", "production_rendering", "failed"),
            "staging_rendering", Set.of("staging_ready", "failed"),
            "staging_ready", Set.of("approved", "rejected", "failed"),
            "approved", Set.of("production_rendering", "failed"),
            "production_rendering", Set.of("published", "failed"),
            "published", Set.of("rolled_back"),
            "rejected", Set.of("queued"),
            "failed", Set.of("queued")
    );

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        Set<String> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    String.format("Invalid publish state transition: %s -> %s", from, to)
            );
        }
    }
}
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/PublishStateMachine.java
git commit -m "feat: add PublishStateMachine for async publish state transitions"
```

---

### Task 6: Extract PublishExecutor from PublishService

**Files:**
- Create: `src/main/java/gov/cms/admin/service/PublishExecutor.java`
- Modify: `src/main/java/gov/cms/admin/service/PublishService.java`

**Step 1: Create PublishExecutor shell**

Create a new service that will contain the actual rendering and file-writing logic extracted from `PublishService`. For the plan, we create the class structure; during execution the existing execution logic from `PublishService.createAndExecute()` and related private methods will be moved here.

```java
package gov.cms.admin.service;

import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishEnvironment;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PublishExecutor {

    private final PublishJobRepository publishJobRepository;
    private final PublishArtifactRepository publishArtifactRepository;
    private final PortalRenderService portalRenderService;
    private final RenderContextAssembler renderContextAssembler;

    public PublishExecutor(PublishJobRepository publishJobRepository,
                           PublishArtifactRepository publishArtifactRepository,
                           PortalRenderService portalRenderService,
                           RenderContextAssembler renderContextAssembler) {
        this.publishJobRepository = publishJobRepository;
        this.publishArtifactRepository = publishArtifactRepository;
        this.portalRenderService = portalRenderService;
        this.renderContextAssembler = renderContextAssembler;
    }

    @Transactional
    public void execute(Long jobId, String environmentName) {
        PublishJob job = publishJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Publish job not found: " + jobId));

        String targetEnv = environmentName != null ? environmentName : job.getEnvironment();
        if (targetEnv == null) {
            targetEnv = PublishEnvironment.PRODUCTION.name().toLowerCase();
        }

        // Transition to rendering state
        String renderingState = targetEnv.equalsIgnoreCase("staging")
                ? "staging_rendering" : "production_rendering";
        PublishStateMachine.requireTransition(job.getStatus(), renderingState);
        job.setStatus(renderingState);
        job.setStartedAt(LocalDateTime.now());
        publishJobRepository.save(job);

        try {
            Path outputRoot = resolveOutputRoot(job.getSiteId(), targetEnv);
            // Actual rendering logic moved from PublishService will be invoked here
            // portalRenderService.render(...) etc.

            // On success
            job.setStatus(targetEnv.equalsIgnoreCase("staging") ? "staging_ready" : "published");
            job.setFinishedAt(LocalDateTime.now());
            if (targetEnv.equalsIgnoreCase("staging")) {
                job.setPreviewToken(UUID.randomUUID().toString().replace("-", ""));
            }
            publishJobRepository.save(job);
        } catch (Exception e) {
            job.setStatus("failed");
            job.setFailureReason(e.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            publishJobRepository.save(job);
            throw new RuntimeException("Publish execution failed for job " + jobId, e);
        }
    }

    private Path resolveOutputRoot(Long siteId, String environment) {
        return Paths.get("./storage/publish", String.valueOf(siteId), environment.toLowerCase());
    }
}
```

**Step 2: Note for execution agent**
During execution, the existing rendering logic inside `PublishService` (the private methods that call `portalRenderService.render()` and write artifacts) must be moved into `PublishExecutor.execute()`. The plan expects the agent to perform this extraction.

**Step 3: Compile skeleton**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (skeleton compiles; full extraction happens in Task 8)

**Step 4: Commit skeleton**

```bash
git add src/main/java/gov/cms/admin/service/PublishExecutor.java
git commit -m "feat: add PublishExecutor skeleton for Quartz job delegation"
```

---

### Task 7: Create PublishQuartzJob

**Files:**
- Create: `src/main/java/gov/cms/admin/scheduler/PublishQuartzJob.java`

**Step 1: Write Quartz Job**

```java
package gov.cms.admin.scheduler;

import gov.cms.admin.service.PublishExecutor;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class PublishQuartzJob implements Job {

    public static final String JOB_DATA_PUBLISH_JOB_ID = "publishJobId";
    public static final String JOB_DATA_ENVIRONMENT = "environment";

    private final PublishExecutor publishExecutor;

    public PublishQuartzJob(PublishExecutor publishExecutor) {
        this.publishExecutor = publishExecutor;
    }

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getMergedJobDataMap();
        Long jobId = data.getLong(JOB_DATA_PUBLISH_JOB_ID);
        String environment = data.getString(JOB_DATA_ENVIRONMENT);
        publishExecutor.execute(jobId, environment);
    }
}
```

**Step 2: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/scheduler/PublishQuartzJob.java
git commit -m "feat: add PublishQuartzJob for async publish execution"
```

---

### Task 8: Refactor PublishService for async queueing

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/PublishService.java`

**Step 1: Inject Quartz Scheduler and PublishExecutor**

Add to constructor parameters and fields:

```java
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

// existing fields ...
private final Scheduler scheduler;
private final PublishExecutor publishExecutor;

public PublishService(
        // ... existing params ...
        Scheduler scheduler,
        PublishExecutor publishExecutor) {
    // ... existing assignments ...
    this.scheduler = scheduler;
    this.publishExecutor = publishExecutor;
}
```

**Step 2: Replace createAndExecute with createAndQueue**

```java
@Transactional
public PublishJob createAndQueue(PublishRequest request, String environment, java.time.LocalDateTime scheduledAt) {
    // Existing validation logic from createAndExecute (check + impact) remains
    PublishCheckResponse check = check(request);
    if (!check.isPublishable()) {
        throw new IllegalStateException("Publish check failed: " + String.join(", ", check.getReasons()));
    }

    PublishJob job = new PublishJob();
    job.setSiteId(request.getSiteId());
    job.setUnitType(request.getUnitType());
    job.setUnitIds(request.getUnitIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
    job.setMode(request.getMode());
    job.setStatus("created");
    job.setEnvironment(environment != null ? environment.toLowerCase() : "production");
    job.setApprovalStatus("pending");
    job.setScheduledAt(scheduledAt);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    job.setOperatorName(auth != null ? auth.getName() : "system");

    PublishJob saved = publishJobRepository.save(job);

    // Schedule Quartz trigger
    schedulePublishTrigger(saved);

    // Transition to queued
    saved.setStatus("queued");
    return publishJobRepository.save(saved);
}

private void schedulePublishTrigger(PublishJob job) {
    try {
        JobDataMap jobData = new JobDataMap();
        jobData.put(PublishQuartzJob.JOB_DATA_PUBLISH_JOB_ID, job.getId());
        jobData.put(PublishQuartzJob.JOB_DATA_ENVIRONMENT, job.getEnvironment());

        JobDetail jobDetail = JobBuilder.newJob(PublishQuartzJob.class)
                .withIdentity("publishJob-" + job.getId(), "publish")
                .usingJobData(jobData)
                .build();

        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity("publishTrigger-" + job.getId(), "publish")
                .forJob(jobDetail);

        if (job.getScheduledAt() != null) {
            triggerBuilder.startAt(java.util.Date.from(job.getScheduledAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        } else {
            triggerBuilder.startNow();
        }

        Trigger trigger = triggerBuilder.build();
        scheduler.scheduleJob(jobDetail, trigger);
    } catch (SchedulerException e) {
        throw new RuntimeException("Failed to schedule publish job " + job.getId(), e);
    }
}
```

**Step 3: Add approve/reject methods**

```java
@Transactional
public PublishJob approveJob(Long jobId) {
    PublishJob job = publishJobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    if (!"staging_ready".equals(job.getStatus())) {
        throw new IllegalStateException("Only staging_ready jobs can be approved");
    }
    PublishStateMachine.requireTransition(job.getStatus(), "approved");
    job.setStatus("approved");
    job.setApprovalStatus("approved");
    publishJobRepository.save(job);

    // Schedule production rendering
    schedulePublishTrigger(job);
    return job;
}

@Transactional
public PublishJob rejectJob(Long jobId) {
    PublishJob job = publishJobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    if (!"staging_ready".equals(job.getStatus())) {
        throw new IllegalStateException("Only staging_ready jobs can be rejected");
    }
    job.setStatus("rejected");
    job.setApprovalStatus("rejected");
    return publishJobRepository.save(job);
}
```

**Step 4: Move rendering logic to PublishExecutor**
The existing private rendering methods in `PublishService` (those that invoke `portalRenderService.render()` and write artifacts to disk) must be moved into `PublishExecutor.execute()`. This step is performed during execution by the implementing agent.

**Step 5: Compile after refactor**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add src/main/java/gov/cms/admin/service/PublishService.java
git add src/main/java/gov/cms/admin/service/PublishExecutor.java
git commit -m "feat: refactor PublishService for async Quartz scheduling with staging/prod environments"
```

---

### Task 9: Update PublishController with async and approval endpoints

**Files:**
- Modify: `src/main/java/gov/cms/admin/controller/PublishController.java`

**Step 1: Replace createAndExecute endpoint**

Replace the existing `@PostMapping("/jobs")` method:

```java
    @PostMapping("/jobs")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> createAndQueue(@RequestBody PublishRequest request,
                                                     @RequestParam(required = false, defaultValue = "production") String environment,
                                                     @RequestParam(required = false) java.time.LocalDateTime scheduledAt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publishService.createAndQueue(request, environment, scheduledAt));
    }
```

**Step 2: Add approval endpoints**

Add after the existing endpoints:

```java
    @PostMapping("/jobs/{id}/approve")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> approveJob(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.approveJob(id));
    }

    @PostMapping("/jobs/{id}/reject")
    @PreAuthorize("hasAuthority('publish:center:execute')")
    public ResponseEntity<PublishJob> rejectJob(@PathVariable Long id) {
        return ResponseEntity.ok(publishService.rejectJob(id));
    }

    @GetMapping("/jobs/{id}/preview")
    public ResponseEntity<String> previewToken(@PathVariable Long id) {
        PublishJob job = publishService.getJob(id);
        if (job == null || job.getPreviewToken() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job.getPreviewToken());
    }
```

**Step 3: Compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/PublishController.java
git commit -m "feat: add publish approval/reject/preview endpoints and async queue creation"
```

---

### Task 10: Update frontend publish API module

**Files:**
- Modify: `frontend/src/api/publish.ts`

**Step 1: Add new interfaces and API functions**

Add after existing imports/exports:

```typescript
export interface PublishQueuePayload extends PublishRequestPayload {
  environment?: 'staging' | 'production'
  scheduledAt?: string | null
}

export const queuePublishJob = (payload: PublishQueuePayload) =>
  api.post<PublishJobItem>('/publish/jobs', payload, { params: { environment: payload.environment || 'production', scheduledAt: payload.scheduledAt || undefined } })

export const approvePublishJob = (id: number) =>
  api.post<PublishJobItem>(`/publish/jobs/${id}/approve`)

export const rejectPublishJob = (id: number) =>
  api.post<PublishJobItem>(`/publish/jobs/${id}/reject`)

export const fetchPublishPreviewToken = (id: number) =>
  api.get<string>(`/publish/jobs/${id}/preview`)
```

**Step 2: Commit**

```bash
git add frontend/src/api/publish.ts
git commit -m "feat: add publish queue/approve/reject API bindings"
```

---

### Task 11: Create PublishTaskList frontend view

**Files:**
- Create: `frontend/src/views/PublishTasks.vue`

**Step 1: Write Vue component**

```vue
<script setup lang="ts">
import '../styles/admin-refresh.css'
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { fetchSiteOptions, type SiteOptionItem } from '../api/sites'
import {
  fetchPublishJobs,
  approvePublishJob,
  rejectPublishJob,
  type PublishJobItem
} from '../api/publish'

const sites = ref<SiteOptionItem[]>([])
const jobs = ref<PublishJobItem[]>([])
const loading = ref(false)
const selectedSiteId = ref<number | undefined>(undefined)
const statusFilter = ref('')

const isSingleSite = computed(() => sites.value.length <= 1)

const loadSites = async () => {
  const response = await fetchSiteOptions()
  sites.value = response.data || []
  if (!selectedSiteId.value && sites.value.length) {
    selectedSiteId.value = sites.value[0].id
  }
}

const loadJobs = async () => {
  if (!selectedSiteId.value) return
  loading.value = true
  try {
    const response = await fetchPublishJobs({
      siteId: selectedSiteId.value,
      status: statusFilter.value || undefined
    })
    jobs.value = response.data || []
  } catch (error: any) {
    message.error(error.response?.data?.message || '加载发布任务失败')
  } finally {
    loading.value = false
  }
}

const approve = async (id: number) => {
  try {
    await approvePublishJob(id)
    message.success('已批准发布')
    await loadJobs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '批准失败')
  }
}

const reject = async (id: number) => {
  try {
    await rejectPublishJob(id)
    message.success('已拒绝发布')
    await loadJobs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '拒绝失败')
  }
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    created: '已创建', queued: '排队中', staging_rendering: 'Staging渲染中',
    staging_ready: '待审批', approved: '已批准', production_rendering: 'Production渲染中',
    published: '已发布', rejected: '已拒绝', failed: '失败', rolled_back: '已回滚'
  }
  return map[status] || status
}

onMounted(async () => {
  await loadSites()
  await loadJobs()
})
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <h1 class="admin-page-title">发布任务</h1>
    </div>
    <div class="admin-toolbar-card">
      <select v-model="selectedSiteId" class="admin-filter-select" :disabled="isSingleSite" @change="loadJobs">
        <option v-for="site in sites" :key="site.id" :value="site.id">{{ site.name }}</option>
      </select>
      <select v-model="statusFilter" class="admin-filter-select" @change="loadJobs">
        <option value="">全部状态</option>
        <option value="staging_ready">待审批</option>
        <option value="published">已发布</option>
        <option value="failed">失败</option>
      </select>
    </div>
    <div v-if="loading" class="admin-empty-state">加载中...</div>
    <div v-else class="admin-card-list">
      <div v-for="job in jobs" :key="job.id" class="admin-card">
        <div class="admin-card-header">
          <span>#{{ job.id }} {{ job.mode }} — {{ statusLabel(job.status) }}</span>
          <span class="admin-sub-text">{{ job.operatorName }}</span>
        </div>
        <div class="admin-sub-text">环境: {{ job.environment || 'production' }}</div>
        <div v-if="job.failureReason" class="searchops-warning-text">{{ job.failureReason }}</div>
        <div v-if="job.status === 'staging_ready'" class="admin-toolbar-row">
          <button class="admin-primary-btn" @click="approve(job.id)">批准上线</button>
          <button class="admin-danger-btn" @click="reject(job.id)">拒绝</button>
        </div>
      </div>
      <div v-if="!jobs.length" class="admin-empty-state">暂无发布任务</div>
    </div>
  </div>
</template>
```

**Step 2: Add route**

Modify `frontend/src/router/index.ts` to add the new route:

```typescript
{ path: '/publish/tasks', component: () => import('../views/PublishTasks.vue'), meta: { title: '发布任务' } }
```

**Step 3: Commit**

```bash
git add frontend/src/views/PublishTasks.vue
git add frontend/src/router/index.ts
git commit -m "feat: add PublishTasks frontend view for async job monitoring and approval"
```

---

### Task 12: Write tests for async publish

**Files:**
- Create: `src/test/java/gov/cms/admin/service/PublishStateMachineTest.java`
- Create: `src/test/java/gov/cms/admin/scheduler/PublishQuartzJobTest.java`

**Step 1: Write PublishStateMachineTest**

```java
package gov.cms.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PublishStateMachineTest {

    @Test
    void canTransition_createdToQueued_shouldBeTrue() {
        assertTrue(PublishStateMachine.canTransition("created", "queued"));
    }

    @Test
    void canTransition_stagingReadyToApproved_shouldBeTrue() {
        assertTrue(PublishStateMachine.canTransition("staging_ready", "approved"));
    }

    @Test
    void canTransition_publishedToQueued_shouldBeFalse() {
        assertFalse(PublishStateMachine.canTransition("published", "queued"));
    }

    @Test
    void requireTransition_invalid_shouldThrow() {
        assertThrows(IllegalStateException.class, () ->
                PublishStateMachine.requireTransition("published", "queued"));
    }
}
```

**Step 2: Run tests**

Run: `mvn test -Dtest=PublishStateMachineTest`
Expected: Tests run: 4, Failures: 0

**Step 3: Commit**

```bash
git add src/test/java/gov/cms/admin/service/PublishStateMachineTest.java
git commit -m "test: add PublishStateMachine transition tests"
```

---

### Task 13: Full test verification

**Files:**
- N/A (verification task)

**Step 1: Run all backend tests**

Run: `mvn test`
Expected: BUILD SUCCESS, all existing tests still pass.

**Step 2: Type-check frontend**

```bash
cd frontend && npx vue-tsc --noEmit
```
Expected: No type errors.

**Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete Quartz async multi-environment publishing"
```

---

## 实施完成后检查清单

- [ ] `mvn test` 全量通过
- [ ] Quartz 表 (QRTZ_*) 在数据库中已创建
- [ ] `PublishJob` 包含 environment, approvalStatus, scheduledAt, previewToken 字段
- [ ] 创建发布任务后 HTTP 立即返回 jobId，不阻塞
- [ ] Staging 渲染完成后生成 previewToken，状态为 staging_ready
- [ ] 批准发布后触发 Production 渲染，状态变为 published
- [ ] 前端 `/publish/tasks` 页面可查看任务列表并执行批准/拒绝
- [ ] 应用重启后，Quartz 能从数据库恢复未执行的发布任务

---

**Plan complete and saved to `docs/plans/2026-06-03-quartz-multi-env-publishing.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints.

**Which approach?**
