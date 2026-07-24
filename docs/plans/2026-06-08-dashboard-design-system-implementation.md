# Dashboard 重设计 + 设计系统升级 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Dashboard 升级为角色感知工作台，同时建立前端组件层、CSS Token 和动效规范。

**Architecture:** 后端新建 `DashboardService` 按角色返回不同 DTO，替换 `findAll()` 为原生查询；前端按 `features/` 目录组织组件，提取 `StatCard`、`EmptyState` 等通用组件；CSS 拆分为 `tokens.css` + `admin-refresh.css`。

**Tech Stack:** Java 17, Spring Boot 3.2, JUnit 5, Mockito, Vue 3, TypeScript, Vite, Ant Design Vue 4, Vitest

---

## Task 1: 后端 Repository 查询优化

**Files:**
- Modify: `src/main/java/gov/cms/admin/repository/ArticleRepository.java`
- Modify: `src/main/java/gov/cms/admin/repository/AuditLogRepository.java`

**Step 1: 添加 ArticleRepository 原生查询方法**

```java
long countByStatus(ArticleStatus status);

List<Article> findTop3ByStatusOrderByCreatedAtDesc(ArticleStatus status);

long countBySiteId(Long siteId);

long countBySiteIdAndStatus(Long siteId, ArticleStatus status);
```

**Step 2: 添加 AuditLogRepository 查询方法**

```java
List<AuditLog> findTop5ByOrderByCreatedAtDesc();
```

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/repository/ArticleRepository.java
git add src/main/java/gov/cms/admin/repository/AuditLogRepository.java
git commit -m "feat(dashboard): add optimized count and top-N queries for dashboard"
```

---

## Task 2: DashboardService TDD — 编写失败测试

**Files:**
- Create: `src/test/java/gov/cms/admin/service/DashboardServiceTest.java`

**Step 1: 编写基础测试骨架**

```java
package gov.cms.admin.service;

import gov.cms.admin.dto.DashboardDto;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.AuditLogRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private UserRepository userRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SiteAccessService siteAccessService;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        when(articleRepository.count()).thenReturn(100L);
        when(userRepository.count()).thenReturn(10L);
        when(siteRepository.count()).thenReturn(5L);
        when(auditLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
    }

    @Test
    void adminShouldSeeGlobalStats() {
        User admin = createUser("admin");
        when(siteAccessService.isAdmin(admin)).thenReturn(true);

        DashboardDto dto = dashboardService.getStatsForUser(admin);

        assertThat(dto.getArticleCount()).isEqualTo(100L);
        assertThat(dto.getUserCount()).isEqualTo(10L);
        assertThat(dto.getSiteCount()).isEqualTo(5L);
    }
}
```

**Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=DashboardServiceTest`
Expected: FAIL - "DashboardService cannot be resolved"

---

## Task 3: DashboardService 实现

**Files:**
- Create: `src/main/java/gov/cms/admin/service/DashboardService.java`
- Create: `src/main/java/gov/cms/admin/dto/DashboardDto.java`

**Step 1: 创建 DashboardDto**

```java
package gov.cms.admin.dto;

import java.util.List;

public class DashboardDto {
    private Long articleCount;
    private Long userCount;
    private Long siteCount;
    private Long pendingReviewCount;
    private List<RecentActivity> recentActivities;
    private List<PendingArticle> pendingArticles;
    private Long myDraftCount;
    private Long publishQueueCount;
    private Long failedTaskCount;
    private Boolean showHealthPanel;

    public Long getArticleCount() { return articleCount; }
    public void setArticleCount(Long articleCount) { this.articleCount = articleCount; }
    public Long getUserCount() { return userCount; }
    public void setUserCount(Long userCount) { this.userCount = userCount; }
    public Long getSiteCount() { return siteCount; }
    public void setSiteCount(Long siteCount) { this.siteCount = siteCount; }
    public Long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(Long pendingReviewCount) { this.pendingReviewCount = pendingReviewCount; }
    public List<RecentActivity> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<RecentActivity> recentActivities) { this.recentActivities = recentActivities; }
    public List<PendingArticle> getPendingArticles() { return pendingArticles; }
    public void setPendingArticles(List<PendingArticle> pendingArticles) { this.pendingArticles = pendingArticles; }
    public Long getMyDraftCount() { return myDraftCount; }
    public void setMyDraftCount(Long myDraftCount) { this.myDraftCount = myDraftCount; }
    public Long getPublishQueueCount() { return publishQueueCount; }
    public void setPublishQueueCount(Long publishQueueCount) { this.publishQueueCount = publishQueueCount; }
    public Long getFailedTaskCount() { return failedTaskCount; }
    public void setFailedTaskCount(Long failedTaskCount) { this.failedTaskCount = failedTaskCount; }
    public Boolean getShowHealthPanel() { return showHealthPanel; }
    public void setShowHealthPanel(Boolean showHealthPanel) { this.showHealthPanel = showHealthPanel; }

    public static class RecentActivity {
        private Long id;
        private String user;
        private String action;
        private String target;
        private String time;
        private String type;
        // getters/setters...
    }

    public static class PendingArticle {
        private Long id;
        private String title;
        private String type;
        private String author;
        private String date;
        // getters/setters...
    }
}
```

**Step 2: 创建 DashboardService**

```java
package gov.cms.admin.service;

import gov.cms.admin.dto.DashboardDto;
import gov.cms.admin.entity.ArticleStatus;
import gov.cms.admin.entity.AuditLog;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.AuditLogRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final AuditLogRepository auditLogRepository;
    private final SiteAccessService siteAccessService;

    public DashboardService(ArticleRepository articleRepository,
                            UserRepository userRepository,
                            SiteRepository siteRepository,
                            AuditLogRepository auditLogRepository,
                            SiteAccessService siteAccessService) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.auditLogRepository = auditLogRepository;
        this.siteAccessService = siteAccessService;
    }

    public DashboardDto getStatsForUser(User user) {
        if (siteAccessService.isAdmin(user)) {
            return buildAdminStats();
        }
        if (siteAccessService.isSiteAdmin(user)) {
            return buildSiteAdminStats(siteAccessService.getManagedSiteId(user));
        }
        if (siteAccessService.isEditor(user)) {
            return buildEditorStats(user.getUsername());
        }
        if (siteAccessService.isReviewer(user)) {
            return buildReviewerStats();
        }
        if (siteAccessService.isPublisher(user)) {
            return buildPublisherStats();
        }
        return buildViewerStats();
    }

    private DashboardDto buildAdminStats() {
        DashboardDto dto = new DashboardDto();
        dto.setArticleCount(articleRepository.count());
        dto.setUserCount(userRepository.count());
        dto.setSiteCount(siteRepository.count());
        dto.setPendingReviewCount(articleRepository.countByStatus(ArticleStatus.pending_review));
        dto.setRecentActivities(mapActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        dto.setPendingArticles(mapPendingArticles(articleRepository.findTop3ByStatusOrderByCreatedAtDesc(ArticleStatus.pending_review)));
        dto.setShowHealthPanel(true);
        return dto;
    }

    private DashboardDto buildSiteAdminStats(Long siteId) {
        DashboardDto dto = new DashboardDto();
        dto.setArticleCount(articleRepository.countBySiteId(siteId));
        dto.setSiteCount(1L);
        dto.setPendingReviewCount(articleRepository.countBySiteIdAndStatus(siteId, ArticleStatus.pending_review));
        dto.setRecentActivities(mapActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        dto.setShowHealthPanel(true);
        return dto;
    }

    private DashboardDto buildEditorStats(String username) {
        DashboardDto dto = new DashboardDto();
        dto.setMyDraftCount(articleRepository.countByAuthorAndStatus(username, ArticleStatus.draft));
        dto.setRecentActivities(mapActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        return dto;
    }

    private DashboardDto buildReviewerStats() {
        DashboardDto dto = new DashboardDto();
        dto.setPendingReviewCount(articleRepository.countByStatus(ArticleStatus.pending_review));
        dto.setPendingArticles(mapPendingArticles(articleRepository.findTop3ByStatusOrderByCreatedAtDesc(ArticleStatus.pending_review)));
        dto.setRecentActivities(mapActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        return dto;
    }

    private DashboardDto buildPublisherStats() {
        DashboardDto dto = new DashboardDto();
        dto.setPublishQueueCount(0L);
        dto.setFailedTaskCount(0L);
        dto.setRecentActivities(mapActivities(auditLogRepository.findTop5ByOrderByCreatedAtDesc()));
        return dto;
    }

    private DashboardDto buildViewerStats() {
        DashboardDto dto = new DashboardDto();
        dto.setArticleCount(articleRepository.count());
        dto.setSiteCount(siteRepository.count());
        return dto;
    }

    private List<DashboardDto.RecentActivity> mapActivities(List<AuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        return logs.stream()
            .map(log -> {
                DashboardDto.RecentActivity activity = new DashboardDto.RecentActivity();
                activity.setId(log.getId());
                activity.setUser(log.getOperatorName());
                activity.setAction(log.getActionType());
                activity.setTarget(log.getSummary());
                activity.setTime(log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
                activity.setType("system");
                return activity;
            })
            .collect(Collectors.toList());
    }

    private List<DashboardDto.PendingArticle> mapPendingArticles(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }
        return articles.stream()
            .map(article -> {
                DashboardDto.PendingArticle pending = new DashboardDto.PendingArticle();
                pending.setId(article.getId());
                pending.setTitle(article.getTitle());
                pending.setType("文章");
                pending.setAuthor(article.getAuthor());
                pending.setDate(article.getCreatedAt() != null ? article.getCreatedAt().toString().split("T")[0] : "");
                return pending;
            })
            .collect(Collectors.toList());
    }
}
```

**Step 3: 运行测试**

Run: `mvn test -Dtest=DashboardServiceTest`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/service/DashboardService.java
git add src/main/java/gov/cms/admin/dto/DashboardDto.java
git add src/test/java/gov/cms/admin/service/DashboardServiceTest.java
git commit -m "feat(dashboard): add DashboardService with role-based stats"
```

---

## Task 4: HealthSummaryController TDD

**Files:**
- Create: `src/test/java/gov/cms/admin/controller/HealthSummaryControllerTest.java`
- Create: `src/main/java/gov/cms/admin/controller/HealthSummaryController.java`
- Create: `src/main/java/gov/cms/admin/dto/HealthSummary.java`

**Step 1: 编写 HealthSummaryControllerTest**

```java
package gov.cms.admin.controller;

import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthSummaryController.class)
@Import(SecurityConfig.class)
class HealthSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "ROLE_admin, 200",
        "ROLE_site_admin, 200",
        "ROLE_editor, 403",
        "ROLE_reviewer, 403",
        "ROLE_publisher, 403"
    })
    @WithMockUser(authorities = "{0}")
    void shouldEnforceRoleAccess(String authority, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/health/summary"))
               .andExpect(status().is(expectedStatus));
    }
}
```

**Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=HealthSummaryControllerTest`
Expected: FAIL - "HealthSummaryController cannot be resolved"

**Step 3: 创建 HealthSummary DTO**

```java
package gov.cms.admin.dto;

public record HealthSummary(String db, String redis, String search, String quartz) {}
```

**Step 4: 创建 HealthSummaryController**

```java
package gov.cms.admin.controller;

import gov.cms.admin.dto.HealthSummary;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthSummaryController {

    private final HealthEndpoint healthEndpoint;

    public HealthSummaryController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_admin','ROLE_site_admin')")
    public ResponseEntity<HealthSummary> getHealthSummary() {
        var health = healthEndpoint.health();
        var components = health.getComponents();

        String db = getStatus(components, "db");
        String redis = getStatus(components, "redis");
        String search = getStatus(components, "hibernateSearch");
        String quartz = getStatus(components, "quartz");

        return ResponseEntity.ok(new HealthSummary(db, redis, search, quartz));
    }

    private String getStatus(java.util.Map<String, org.springframework.boot.actuate.health.HealthComponent> components, String key) {
        if (components == null || !components.containsKey(key)) {
            return "UNKNOWN";
        }
        var status = components.get(key).getStatus();
        return status != null ? status.getCode() : "UNKNOWN";
    }
}
```

**Step 5: 运行测试**

Run: `mvn test -Dtest=HealthSummaryControllerTest`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/HealthSummaryController.java
git add src/main/java/gov/cms/admin/dto/HealthSummary.java
git add src/test/java/gov/cms/admin/controller/HealthSummaryControllerTest.java
git commit -m "feat(health): add HealthSummaryController with role-based access"
```

---

## Task 5: 更新 DashboardController

**Files:**
- Modify: `src/main/java/gov/cms/admin/controller/DashboardController.java`
- Modify: `src/test/java/gov/cms/admin/controller/DashboardControllerTest.java`

**Step 1: 重写 DashboardController**

```java
package gov.cms.admin.controller;

import gov.cms.admin.dto.DashboardDto;
import gov.cms.admin.service.DashboardService;
import gov.cms.admin.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('content:article:view')")
    public ResponseEntity<DashboardDto> getDashboardStats() {
        var currentUser = userService.getCurrentUser();
        DashboardDto stats = dashboardService.getStatsForUser(currentUser);
        return ResponseEntity.ok(stats);
    }
}
```

**Step 2: 更新 DashboardControllerTest**

```java
package gov.cms.admin.controller;

import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.DashboardDto;
import gov.cms.admin.entity.User;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.DashboardService;
import gov.cms.admin.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private UserService userService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "content:article:view")
    void dashboardReturnsRoleBasedStats() throws Exception {
        User mockUser = new User();
        mockUser.setUsername("admin");

        DashboardDto dto = new DashboardDto();
        dto.setArticleCount(100L);
        dto.setSiteCount(5L);

        when(userService.getCurrentUser()).thenReturn(mockUser);
        when(dashboardService.getStatsForUser(mockUser)).thenReturn(dto);

        mockMvc.perform(get("/api/statistics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(100))
                .andExpect(jsonPath("$.siteCount").value(5));
    }
}
```

**Step 3: 运行测试**

Run: `mvn test -Dtest=DashboardControllerTest`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/DashboardController.java
git add src/test/java/gov/cms/admin/controller/DashboardControllerTest.java
git commit -m "refactor(dashboard): delegate to DashboardService for role-based stats"
```

---

## Task 6: 前端基础设施 — Vitest + tokens.css

**Files:**
- Create: `frontend/vitest.config.ts`
- Modify: `frontend/package.json`
- Create: `frontend/src/styles/tokens.css`

**Step 1: 安装 Vitest**

```bash
cd frontend
npm install -D vitest @vue/test-utils jsdom @vitest/coverage-v8
```

**Step 2: 创建 vitest.config.ts**

```typescript
import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      thresholds: {
        branches: 80,
        functions: 80,
        lines: 80,
        statements: 80
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
});
```

**Step 3: 更新 package.json 脚本**

```json
{
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage"
  }
}
```

**Step 4: 创建 tokens.css**

```css
:root {
  --duration-instant: 0ms;
  --duration-fast: 150ms;
  --duration-normal: 250ms;
  --duration-slow: 400ms;

  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.45, 0, 0.55, 1);

  --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.05);
  --shadow-md: 0 4px 12px rgba(15, 23, 42, 0.06);
  --shadow-lg: 0 8px 24px rgba(15, 23, 42, 0.08);

  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;

  --color-surface: #ffffff;
  --color-background: #f8fafc;
  --color-border: #e2e8f0;
  --color-text: #0f172a;
  --color-text-secondary: #475569;
  --color-text-muted: #64748b;
  --color-primary: #2563eb;
  --color-success: #16a34a;
  --color-danger: #dc2626;
  --color-warning: #ca8a04;
}

@keyframes card-enter {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse-danger {
  0%, 100% { box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(220, 38, 38, 0); }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

**Step 5: 更新 main.ts 引入 tokens.css**

```typescript
import './styles/tokens.css'
import './styles/admin-refresh.css'
```

**Step 6: Commit**

```bash
git add frontend/vitest.config.ts
 git add frontend/package.json
 git add frontend/src/styles/tokens.css
 git add frontend/src/main.ts
 git commit -m "feat(design-system): add Vitest, CSS tokens, and animation keyframes"
```

---

## Task 7: 目录结构调整 + EmptyState 组件

**Files:**
- Create: `frontend/src/components/ui/EmptyState.vue`
- Create: `frontend/src/components/ui/__tests__/EmptyState.spec.ts`
- Move: `frontend/src/components/MainLayout.vue` → `frontend/src/components/layout/MainLayout.vue`

**Step 1: 创建目录**

```bash
mkdir -p frontend/src/components/ui/__tests__
mkdir -p frontend/src/components/layout
mkdir -p frontend/src/components/features/dashboard
mkdir -p frontend/src/styles/views
```

**Step 2: 移动 MainLayout**

```bash
git mv frontend/src/components/MainLayout.vue frontend/src/components/layout/MainLayout.vue
```

**Step 3: 更新引用 MainLayout 的文件**

搜索并更新所有 `import MainLayout from '../components/MainLayout.vue'` 为正确的相对路径。

**Step 4: 创建 EmptyState.vue**

```vue
<script setup lang="ts">
import { FileTextOutlined, InboxOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

interface Props {
  title: string
  description?: string
  icon?: 'file' | 'inbox' | 'search' | 'warning'
  actionText?: string
  actionTo?: string
  variant?: 'default' | 'compact'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default'
})

const router = useRouter()

const iconComponent = computed(() => {
  switch (props.icon) {
    case 'search': return SearchOutlined
    case 'warning': return FileTextOutlined
    case 'file': return FileTextOutlined
    case 'inbox':
    default: return InboxOutlined
  }
})

const handleAction = () => {
  if (props.actionTo) {
    router.push(props.actionTo)
  }
}
</script>

<template>
  <div class="empty-state" :class="`variant-${variant}`">
    <div class="empty-state-icon">
      <component :is="iconComponent" />
    </div>
    <h4 class="empty-state-title">{{ title }}</h4>
    <p v-if="description" class="empty-state-desc">{{ description }}</p>
    <div v-if="$slots.footer || actionText" class="empty-state-footer">
      <slot name="footer">
        <button v-if="actionText && actionTo" class="admin-primary-btn" @click="handleAction">
          {{ actionText }}
        </button>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  text-align: center;
  gap: var(--space-3);
}

.empty-state.variant-compact {
  padding: var(--space-4);
  gap: var(--space-2);
}

.empty-state-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: var(--color-background);
  color: var(--color-text-muted);
  font-size: 28px;
}

.variant-compact .empty-state-icon {
  width: 40px;
  height: 40px;
  font-size: 20px;
  border-radius: 10px;
}

.empty-state-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.empty-state-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
  max-width: 320px;
}

.empty-state-footer {
  margin-top: var(--space-2);
}
</style>
```

**Step 5: 创建 EmptyState 测试**

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../EmptyState.vue'

describe('EmptyState', () => {
  it('renders title and description', () => {
    const wrapper = mount(EmptyState, {
      props: { title: '暂无数据', description: '请稍后重试' }
    })
    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).toContain('请稍后重试')
  })

  it('renders action button when actionText and actionTo provided', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'X', actionText: '去创建', actionTo: '/content' }
    })
    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.text()).toContain('去创建')
  })

  it('does not render action button without actionTo', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'X', actionText: '去创建' }
    })
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
```

**Step 6: 运行测试**

Run: `cd frontend && npm test -- src/components/ui/__tests__/EmptyState.spec.ts`
Expected: PASS

**Step 7: Commit**

```bash
git add frontend/src/components/ui/EmptyState.vue
 git add frontend/src/components/ui/__tests__/EmptyState.spec.ts
 git add frontend/src/components/layout/MainLayout.vue
 git rm frontend/src/components/MainLayout.vue
 git commit -m "feat(components): add EmptyState, move MainLayout to layout/"
```

---

## Task 8: StatCard 组件 + 测试

**Files:**
- Create: `frontend/src/components/ui/StatCard.vue`
- Create: `frontend/src/components/ui/__tests__/StatCard.spec.ts`

**Step 1: 创建 StatCard.vue**

```vue
<script setup lang="ts">
interface Props {
  title: string
  value: number | string
  icon: any
  status?: 'normal' | 'warning' | 'danger'
  description?: string
  index?: number
}

const props = withDefaults(defineProps<Props>(), {
  status: 'normal'
})
</script>

<template>
  <div
    class="stat-card"
    :class="`status-${status}`"
    :style="{ animationDelay: `${(index ?? 0) * 50}ms` }"
  >
    <div class="stat-card-header">
      <span class="stat-card-title">{{ title }}</span>
      <div class="stat-card-icon">
        <component :is="icon" />
      </div>
    </div>
    <div class="stat-card-value">{{ value }}</div>
    <div v-if="description" class="stat-card-desc">{{ description }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  opacity: 0;
  transform: translateY(12px);
  animation: card-enter var(--duration-normal) var(--ease-out) forwards;
}

.stat-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-card-title {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.stat-card-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eff6ff;
  color: var(--color-primary);
  font-size: 18px;
}

.stat-card-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.stat-card-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.status-warning .stat-card-desc {
  color: var(--color-warning);
}

.status-danger .stat-card-desc {
  color: var(--color-danger);
}

@media (prefers-reduced-motion: reduce) {
  .stat-card {
    animation: none;
    opacity: 1;
    transform: none;
  }
}
</style>
```

**Step 2: 创建 StatCard 测试**

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { FileTextOutlined } from '@ant-design/icons-vue'
import StatCard from '../StatCard.vue'

describe('StatCard', () => {
  it('renders title and value', () => {
    const wrapper = mount(StatCard, {
      props: {
        title: '内容总数',
        value: 128,
        icon: FileTextOutlined
      }
    })
    expect(wrapper.text()).toContain('内容总数')
    expect(wrapper.text()).toContain('128')
  })

  it('renders description when provided', () => {
    const wrapper = mount(StatCard, {
      props: {
        title: 'X',
        value: 0,
        icon: FileTextOutlined,
        description: '较上周 +12%'
      }
    })
    expect(wrapper.text()).toContain('较上周 +12%')
  })

  it('applies danger status class', () => {
    const wrapper = mount(StatCard, {
      props: {
        title: 'X',
        value: 5,
        icon: FileTextOutlined,
        status: 'danger'
      }
    })
    expect(wrapper.find('.status-danger').exists()).toBe(true)
  })
})
```

**Step 3: 运行测试**

Run: `cd frontend && npm test -- src/components/ui/__tests__/StatCard.spec.ts`
Expected: PASS

**Step 4: Commit**

```bash
git add frontend/src/components/ui/StatCard.vue
 git add frontend/src/components/ui/__tests__/StatCard.spec.ts
 git commit -m "feat(components): add StatCard with stagger animation"
```

---

## Task 9: Dashboard 领域组件

**Files:**
- Create: `frontend/src/components/features/dashboard/types.ts`
- Create: `frontend/src/components/features/dashboard/api.ts`
- Create: `frontend/src/components/features/dashboard/ActivityItem.vue`
- Create: `frontend/src/components/features/dashboard/ActivityFeed.vue`
- Create: `frontend/src/components/features/dashboard/DashboardTaskList.vue`
- Create: `frontend/src/components/features/dashboard/SystemHealthPanel.vue`

**Step 1: 创建 types.ts**

```typescript
export interface ActivityItem {
  id: number
  user: string
  action: string
  target: string
  time: string
  type: 'publish' | 'edit' | 'upload' | 'review' | 'system'
}

export interface HealthItem {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  label: string
}

export interface TaskItem {
  id: number
  title: string
  author: string
  date: string
  type: string
}

export interface DashboardDto {
  articleCount?: number
  userCount?: number
  siteCount?: number
  pendingReviewCount?: number
  myDraftCount?: number
  publishQueueCount?: number
  failedTaskCount?: number
  showHealthPanel?: boolean
  recentActivities?: ActivityItem[]
  pendingArticles?: TaskItem[]
}
```

**Step 2: 创建 api.ts**

```typescript
import api from '@/utils/api'
import type { DashboardDto, HealthItem } from './types'

export const fetchDashboardStats = async (): Promise<DashboardDto> => {
  const res = await api.get<DashboardDto>('/statistics/dashboard')
  return res.data
}

export const fetchHealthSummary = async (): Promise<HealthItem[]> => {
  const res = await api.get<HealthItem[]>('/health/summary')
  return res.data
}
```

**Step 3: 创建 ActivityItem.vue**

```vue
<script setup lang="ts">
import { FileTextOutlined, EditOutlined, CloudUploadOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import type { ActivityItem } from './types'

interface Props {
  item: ActivityItem
}

defineProps<Props>()

const iconMap: Record<string, any> = {
  publish: FileTextOutlined,
  edit: EditOutlined,
  upload: CloudUploadOutlined,
  review: CheckCircleOutlined,
  system: FileTextOutlined
}
</script>

<template>
  <div class="activity-item">
    <div class="activity-icon" :class="item.type">
      <component :is="iconMap[item.type] || FileTextOutlined" />
    </div>
    <div class="activity-content">
      <div class="activity-main">
        <span class="activity-user">{{ item.user }}</span>
        <span class="activity-action">{{ item.action }}</span>
      </div>
      <div class="activity-target">{{ item.target }}</div>
    </div>
    <div class="activity-time">{{ item.time }}</div>
  </div>
</template>

<style scoped>
.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--color-background);
  border-radius: 12px;
  transition: background var(--duration-fast);
}

.activity-item:hover {
  background: #f1f5f9;
}

.activity-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eff6ff;
  color: var(--color-primary);
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.activity-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.activity-user {
  font-weight: 600;
  color: var(--color-text);
}

.activity-action {
  color: var(--color-text-secondary);
}

.activity-target {
  font-size: 13px;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.activity-time {
  font-size: 12px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}
</style>
```

**Step 4: 创建 ActivityFeed.vue**

```vue
<script setup lang="ts">
import type { ActivityItem } from './types'
import ActivityItemComponent from './ActivityItem.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

interface Props {
  items: ActivityItem[]
}

defineProps<Props>()
</script>

<template>
  <div class="activity-feed">
    <h3 class="feed-title">近期活动</h3>
    <TransitionGroup v-if="items.length" name="activity" tag="div" class="feed-list">
      <ActivityItemComponent
        v-for="item in items"
        :key="item.id"
        :item="item"
      />
    </TransitionGroup>
    <EmptyState
      v-else
      title="暂无活动记录"
      description="当用户执行发布、编辑等操作后，将在此显示"
      icon="inbox"
    />
  </div>
</template>

<style scoped>
.activity-feed {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.feed-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.activity-enter-active,
.activity-leave-active {
  transition: all var(--duration-normal) var(--ease-out);
}

.activity-enter-from,
.activity-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
```

**Step 5: 创建 DashboardTaskList.vue**

```vue
<script setup lang="ts">
import type { TaskItem } from './types'
import EmptyState from '@/components/ui/EmptyState.vue'

interface Props {
  tasks: TaskItem[]
  title: string
  emptyText?: string
}

withDefaults(defineProps<Props>(), {
  emptyText: '暂无待处理内容'
})
</script>

<template>
  <div class="task-list">
    <h3 class="task-list-title">{{ title }}</h3>
    <div v-if="tasks.length" class="task-items">
      <div v-for="task in tasks" :key="task.id" class="task-item">
        <div class="task-info">
          <span class="task-title">{{ task.title }}</span>
          <span class="task-meta">{{ task.author }} · {{ task.date }}</span>
        </div>
        <span class="task-type">{{ task.type }}</span>
      </div>
    </div>
    <EmptyState v-else :title="emptyText" icon="inbox" variant="compact" />
  </div>
</template>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.task-list-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.task-items {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 12px;
  background: var(--color-background);
  border-radius: 12px;
}

.task-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.task-title {
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-meta {
  font-size: 12px;
  color: var(--color-text-muted);
}

.task-type {
  font-size: 12px;
  padding: 4px 10px;
  background: #e2e8f0;
  border-radius: 999px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
</style>
```

**Step 6: 创建 SystemHealthPanel.vue**

```vue
<script setup lang="ts">
import type { HealthItem } from './types'

interface Props {
  services: HealthItem[]
}

defineProps<Props>()
</script>

<template>
  <div class="health-panel">
    <h3 class="health-title">系统状态</h3>
    <div class="health-grid">
      <div
        v-for="service in services"
        :key="service.name"
        class="health-item"
        :class="{ down: service.status === 'DOWN', unknown: service.status === 'UNKNOWN' }"
      >
        <div class="health-indicator" :class="service.status.toLowerCase()" />
        <span class="health-name">{{ service.label }}</span>
        <span class="health-status">{{ service.status === 'UP' ? '正常' : service.status === 'DOWN' ? '异常' : '未知' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.health-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: var(--space-4);
}

.health-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 var(--space-3) 0;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-3);
}

.health-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: 10px;
  background: var(--color-background);
  transition: background var(--duration-fast);
}

.health-indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-success);
}

.health-indicator.down {
  background: var(--color-danger);
  animation: pulse-danger 1.5s infinite;
}

.health-indicator.unknown {
  background: var(--color-warning);
}

.health-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}

.health-status {
  font-size: 12px;
  color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .health-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
```

**Step 7: Commit**

```bash
git add frontend/src/components/features/dashboard/
git commit -m "feat(dashboard): add ActivityFeed, TaskList, HealthPanel components"
```

---

## Task 10: 重写 Dashboard.vue

**Files:**
- Modify: `frontend/src/views/Dashboard.vue`

**Step 1: 重写 Dashboard.vue**

```vue
<script setup lang="ts">
import '../styles/admin-refresh.css'

import { ref, computed, onMounted } from 'vue'
import {
  FileTextOutlined, TeamOutlined, FolderOutlined,
  EyeOutlined, PlusOutlined, EditOutlined,
  CloudUploadOutlined, UserAddOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, RocketOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import StatCard from '@/components/ui/StatCard.vue'
import ActivityFeed from '@/components/features/dashboard/ActivityFeed.vue'
import DashboardTaskList from '@/components/features/dashboard/DashboardTaskList.vue'
import SystemHealthPanel from '@/components/features/dashboard/SystemHealthPanel.vue'
import type { DashboardDto, HealthItem } from '@/components/features/dashboard/types'
import { fetchDashboardStats, fetchHealthSummary } from '@/components/features/dashboard/api'

const router = useRouter()
const loading = ref(false)
const healthLoading = ref(false)
const stats = ref<DashboardDto>({})
const healthData = ref<HealthItem[]>([])

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
})

const hasField = (key: keyof DashboardDto) => {
  return stats.value[key] !== undefined && stats.value[key] !== null
}

const statCards = computed(() => {
  const cards = []
  if (hasField('articleCount')) {
    cards.push({ title: '内容总数', value: stats.value.articleCount, icon: FileTextOutlined, key: 'articleCount' })
  }
  if (hasField('userCount')) {
    cards.push({ title: '用户总数', value: stats.value.userCount, icon: TeamOutlined, key: 'userCount' })
  }
  if (hasField('siteCount')) {
    cards.push({ title: '站点总数', value: stats.value.siteCount, icon: FolderOutlined, key: 'siteCount' })
  }
  if (hasField('pendingReviewCount')) {
    cards.push({
      title: '待审核数',
      value: stats.value.pendingReviewCount,
      icon: CheckCircleOutlined,
      key: 'pendingReviewCount',
      status: (stats.value.pendingReviewCount ?? 0) > 0 ? 'warning' : 'normal',
      description: `${stats.value.pendingReviewCount} 项待处理`
    })
  }
  if (hasField('myDraftCount')) {
    cards.push({ title: '我的草稿', value: stats.value.myDraftCount, icon: EditOutlined, key: 'myDraftCount' })
  }
  if (hasField('publishQueueCount')) {
    cards.push({ title: '发布队列', value: stats.value.publishQueueCount, icon: RocketOutlined, key: 'publishQueueCount' })
  }
  if (hasField('failedTaskCount')) {
    cards.push({
      title: '失败任务',
      value: stats.value.failedTaskCount,
      icon: ExclamationCircleOutlined,
      key: 'failedTaskCount',
      status: (stats.value.failedTaskCount ?? 0) > 0 ? 'danger' : 'normal',
      description: `${stats.value.failedTaskCount} 项需关注`
    })
  }
  return cards.slice(0, 4)
})

const quickActions = computed(() => {
  const actions = []
  actions.push({ label: '新建内容', icon: PlusOutlined, to: '/content' })
  if (hasField('userCount')) {
    actions.push({ label: '用户管理', icon: TeamOutlined, to: '/system/users' })
  }
  if (hasField('siteCount')) {
    actions.push({ label: '站点管理', icon: FolderOutlined, to: '/site-ops/sites' })
  }
  return actions
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await fetchDashboardStats()
    stats.value = res
  } catch (e) {
    console.error('获取仪表盘数据失败:', e)
  } finally {
    loading.value = false
  }
}

const loadHealth = async () => {
  if (!stats.value.showHealthPanel) return
  healthLoading.value = true
  try {
    const res = await fetchHealthSummary()
    healthData.value = res
  } catch (e) {
    console.error('获取健康状态失败:', e)
  } finally {
    healthLoading.value = false
  }
}

onMounted(async () => {
  await loadData()
  await loadHealth()
})
</script>

<template>
  <div class="admin-page dashboard-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">欢迎回来</h1>
        <p class="admin-page-desc">{{ today }}</p>
      </div>
      <div class="quick-actions">
        <button
          v-for="action in quickActions"
          :key="action.label"
          class="admin-primary-btn"
          @click="router.push(action.to)"
        >
          <component :is="action.icon" />
          <span>{{ action.label }}</span>
        </button>
      </div>
    </div>

    <div class="dashboard-stat-grid">
      <StatCard
        v-for="(card, index) in statCards"
        :key="card.key"
        :title="card.title"
        :value="card.value ?? 0"
        :icon="card.icon"
        :status="card.status"
        :description="card.description"
        :index="index"
      />
    </div>

    <div class="dashboard-content-grid">
      <div class="admin-card">
        <ActivityFeed :items="stats.recentActivities ?? []" />
      </div>

      <div class="dashboard-right-panel">
        <SystemHealthPanel
          v-if="stats.showHealthPanel"
          :services="healthData"
        />
        <DashboardTaskList
          v-if="stats.pendingArticles?.length"
          :tasks="stats.pendingArticles"
          title="待审核内容"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page .dashboard-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.dashboard-page .dashboard-content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.dashboard-page .dashboard-right-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-page .quick-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 1080px) {
  .dashboard-page .dashboard-stat-grid,
  .dashboard-page .dashboard-content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

**Step 2: 验证类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: PASS (无类型错误)

**Step 3: Commit**

```bash
git add frontend/src/views/Dashboard.vue
git commit -m "feat(dashboard): rewrite Dashboard.vue with role-aware widgets"
```

---

## Task 11: 最终验证

**Step 1: 后端测试**

Run: `mvn test`
Expected: ALL PASS

**Step 2: 前端类型检查 + 构建**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: PASS

Run: `cd frontend && npm run build`
Expected: PASS

**Step 3: 前端测试**

Run: `cd frontend && npm test`
Expected: ALL PASS (EmptyState + StatCard tests)

**Step 4: Commit**

```bash
git add .
git commit -m "test(dashboard): add frontend and backend tests, verify build"
```

---

## 附录：参考文档

- 设计文档: `docs/plans/2026-06-08-dashboard-design-system-design.md`
- 角色体系: `docs/16-role-system-definition.md`
- Admin UX 规范: `docs/18-admin-ux-visual-and-interaction-guidelines.md`
