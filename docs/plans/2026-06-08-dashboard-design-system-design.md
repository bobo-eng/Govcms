# GovCMS 仪表盘重设计 + 设计系统升级

> **评审状态**：已通过 architect、security-reviewer、typescript-reviewer、tdd-guide 四代理评审，关键问题已吸收修正。
> **For Claude**: REQUIRED SUB-SKILL: Use superpowers:writing-plans to implement this plan task-by-task.

---

## 1. 目标

将 Dashboard 从"通用统计卡片 + 假数据"升级为**角色感知的工作台**，同时借此次改版建立可复用的前端组件层、CSS Token 体系和动效规范。

### 核心要求
- **混合模式**：基础布局统一，widget 按角色动态显示/隐藏
- **真实数据**：去掉所有硬编码（趋势、活动、访问量），接入真实数据源
- **安全优先**：后端按角色返回不同 DTO，前端不再信任 `localStorage.roles`
- **性能修复**：替换 `findAll()` 为数据库原生查询

---

## 2. 安全架构（CRITICAL）

### 2.1 数据隔离原则
- **后端是唯一的信任边界**。`DashboardService` 必须注入 `SiteAccessService`，按角色过滤数据：
  - `admin`：全站聚合数据
  - `site_admin`：仅 `managedSiteId` 对应站点的数据
  - `editor`：仅自己的草稿/提交/发布数据
  - `reviewer`：待审核内容 + 自己的审核历史
  - `publisher`：发布队列 + 失败任务 + 待发布
  - `viewer`：只读全局统计

### 2.2 DTO 策略
- **禁止统一 god DTO**。每个角色返回专属结构，前端根据返回字段存在性决定渲染。
- `viewer` 不应收到 `systemHealthStatus`，`editor` 不应收到 `failedTaskCount`。

### 2.3 Health API 权限
- 新建 `HealthSummaryController`，独立路径 `/api/health/summary`
- `@PreAuthorize("hasAnyAuthority('ROLE_admin','ROLE_site_admin')")`
- 只返回简化状态（`UP`/`DOWN`），不暴露底层异常详情

### 2.4 AuditLog 数据暴露控制
- `failureReason` **不得返回给前端**
- 创建 `AuditLogDto` 做字段级过滤
- `site_admin` 只能看到管辖站点的日志（已有 `SiteAccessService` 隔离）

---

## 3. 后端架构

### 3.1 新建 `DashboardService`

```java
@Service
public class DashboardService {
    public DashboardDto getStatsForCurrentUser() {
        User user = getCurrentUser();
        if (isAdmin(user)) return buildAdminStats();
        if (isSiteAdmin(user)) return buildSiteAdminStats(getManagedSiteId(user));
        if (isEditor(user)) return buildEditorStats(user.getUsername());
        if (isReviewer(user)) return buildReviewerStats();
        if (isPublisher(user)) return buildPublisherStats();
        return buildViewerStats();
    }
}
```

### 3.2 查询优化（P0）

| 原实现 | 优化后 |
|--------|--------|
| `articleRepository.findAll().stream().filter(...).count()` | `articleRepository.countByStatus(ArticleStatus.pending_review)` |
| `articleRepository.findAll().stream().filter(...).limit(3)` | `articleRepository.findTop3ByStatusOrderByCreatedAtDesc(ArticleStatus.pending_review)` |
| 硬编码 `recentActivities` | `auditLogRepository.findTop5ByOrderByCreatedAtDesc()` |
| `viewCount = articleCount * 100L` | 删除该字段，或接入真实访问统计 |

### 3.3 缓存策略

| 数据 | 缓存 | TTL |
|------|------|-----|
| articleCount / userCount / siteCount | `@Cacheable("dashboardCounts")` | 5 分钟 |
| recentActivities | `@Cacheable("dashboardActivities")` | 1 分钟 |
| pendingArticles | 不缓存 | — |
| healthSummary | `@Cacheable("healthSummary")` | 10 秒 |

注意：`site_admin` 缓存 key 必须包含 `siteId`。

### 3.4 新增后端文件

- `gov.cms.admin.service.DashboardService`
- `gov.cms.admin.controller.HealthSummaryController`
- `gov.cms.admin.dto.HealthSummary`（record）
- `gov.cms.admin.dto.DashboardDto`（各角色的子类或统一接口）

---

## 4. 前端架构

### 4.1 目录结构

```
frontend/src/
├── components/
│   ├── ui/                    # 原子级通用组件
│   │   ├── StatCard.vue
│   │   ├── EmptyState.vue
│   │   └── LoadingSkeleton.vue
│   ├── layout/
│   │   └── MainLayout.vue     # 从 components/ 根移入
│   └── features/              # 按业务域组织
│       └── dashboard/
│           ├── ActivityFeed.vue
│           ├── ActivityItem.vue
│           ├── DashboardTaskList.vue
│           ├── SystemHealthPanel.vue
│           ├── types.ts       # 就近类型定义
│           └── api.ts         # 领域 API 封装
├── styles/
│   ├── tokens.css             # 全局设计令牌
│   ├── admin-refresh.css      # 精简：仅跨页面共享组件样式
│   └── views/
│       └── dashboard.css      # Dashboard 专属样式（若 scoped 过大时拆分）
```

### 4.2 组件清单

| 组件 | 路径 | Props（泛型 defineProps） | 说明 |
|------|------|--------------------------|------|
| `StatCard` | `components/ui/StatCard.vue` | `title, value, icon, status?, description, index?` | `index` 用于 stagger 动画 |
| `EmptyState` | `components/ui/EmptyState.vue` | `title, description?, icon?, actionText?, actionTo?, variant?` | 全局共享，含 slot footer |
| `ActivityFeed` | `features/dashboard/ActivityFeed.vue` | `items: ActivityItem[]` | 容器，内部用 `<TransitionGroup>` |
| `ActivityItem` | `features/dashboard/ActivityItem.vue` | `item: ActivityItem` | 行组件 |
| `DashboardTaskList` | `features/dashboard/DashboardTaskList.vue` | `tasks, type` | 待审/待发布列表 |
| `SystemHealthPanel` | `features/dashboard/SystemHealthPanel.vue` | `services: HealthItem[]` | 仅 admin/site_admin 可见 |

### 4.3 TypeScript 类型

```typescript
// features/dashboard/types.ts
export interface ActivityItem {
  id: number
  user: string
  action: string
  target: string
  time: string        // ISO 8601
  type: 'publish' | 'edit' | 'upload' | 'review' | 'system'
}

export interface HealthItem {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  label: string
}
```

### 4.4 API 封装

```typescript
// features/dashboard/api.ts
import api from '@/utils/api'
import type { DashboardDto } from './types'

export const fetchDashboardStats = async (): Promise<DashboardDto> => {
  const res = await api.get<DashboardDto>('/statistics/dashboard')
  return res.data
}

export const fetchHealthSummary = async (): Promise<HealthItem[]> => {
  const res = await api.get<HealthItem[]>('/health/summary')
  return res.data
}
```

---

## 5. 各角色 Dashboard 配置

### 5.1 统计卡配置

| 角色 | 统计卡（从左到右） | 右侧面板 |
|------|-------------------|---------|
| **admin** | 内容总数 · 用户总数 · 站点总数 · 待审核数 | **系统健康面板** |
| **site_admin** | 内容总数 · 用户总数 · 站点总数 · 待审核数 | **系统健康面板**（仅本站） |
| **editor** | 我的草稿 · 已提交审核 · 已发布 · 内容总数 | **我的待办** |
| **reviewer** | 待审核数 · 今日已审 · 已发布 · 内容总数 | **待审列表** |
| **publisher** | 发布队列 · 失败任务 · 最近发布 · 待发布 | **发布摘要** |
| **viewer** | 内容总数 · 站点总数 · 已发布 · （无第四张） | **最近动态** |

### 5.2 快捷操作栏（内联在 Dashboard.vue）

- **admin:** 新建内容 · 用户管理 · 站点管理
- **editor:** 新建内容 · 查看我的内容
- **reviewer:** 去审核工作区 · 查看审核历史
- **publisher:** 去发布中心 · 查看失败任务
- **viewer:** 无

---

## 6. CSS Token 与样式架构

### 6.1 `styles/tokens.css`（新建）

```css
:root {
  /* Duration */
  --duration-instant: 0ms;
  --duration-fast: 150ms;
  --duration-normal: 250ms;
  --duration-slow: 400ms;

  /* Easing */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.45, 0, 0.55, 1);

  /* Shadow */
  --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.05);
  --shadow-md: 0 4px 12px rgba(15, 23, 42, 0.06);
  --shadow-lg: 0 8px 24px rgba(15, 23, 42, 0.08);

  /* Space */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;

  /* Color */
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

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

### 6.2 `main.ts` 引入顺序

```typescript
import './styles/tokens.css'
import './styles/admin-refresh.css'
```

### 6.3 Vue 组件样式约定
- 新组件**不写 scoped 样式**，样式由 `admin-refresh.css` 的 BEM 类名或 `tokens.css` 变量提供
- `scoped` 仅用于页面级特殊布局（grid 定义）
- Dashboard.vue 的 scoped style 若超过 200 行，拆分到 `styles/views/dashboard.css`

---

## 7. 动效规范

| 场景 | 实现方案 | 具体参数 |
|------|---------|---------|
| StatCard 入场 stagger | CSS `animation-delay` + `@keyframes card-enter` | delay: `index * 50ms`, duration-normal, ease-out |
| ActivityFeed 列表增删 | Vue `<TransitionGroup name="activity">` | 250ms ease-out |
| SystemHealthPanel 异常 | CSS `@keyframes pulse` | 1.5s infinite, 红色阴影 |
| Hover 状态 | CSS `:hover` + `transition` | duration-fast, opacity + scale |
| 全局降级 | `prefers-reduced-motion: reduce` | 所有动画归零 |

### 7.1 关键帧定义

```css
@keyframes card-enter {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse-danger {
  0%, 100% { box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(220, 38, 38, 0); }
}
```

---

## 8. 无障碍与深色模式基础

### 8.1 本阶段必做
- 所有图标按钮加 `aria-label`
- 空状态文字满足 4.5:1 对比度
- 健康状态不仅用颜色，还用文字标签（"正常"/"异常"）
- `prefers-reduced-motion` 全局支持

### 8.2 深色模式预留
Token 层已定义语义化颜色变量，后续深色模式只需覆盖：
```css
[data-theme="dark"] {
  --color-surface: #0f172a;
  --color-background: #1e293b;
  --color-text: #f8fafc;
}
```

---

## 9. 错误处理

- `Promise.allSettled` 并行加载 dashboard + health，任一失败不白屏
- 统计卡加载失败显示 `--` 而非空白
- Health API 403 时静默隐藏 SystemHealthPanel
- `DashboardService` 对 `null` count 返回 `0L`，避免 NPE

---

## 10. 测试策略

### 10.1 后端测试

| 测试类 | 内容 | 工具 |
|--------|------|------|
| `DashboardServiceTest` | 6 角色参数化测试（`@ParameterizedTest` + `@MethodSource`），验证 DTO 字段正确、SiteAccessService 过滤生效、空数据/异常路径 | JUnit 5 + Mockito |
| `DashboardControllerTest` | 验证 `countByStatus` 替换后数据正确、缓存行为 | MockMvc + Mockito |
| `HealthSummaryControllerTest` | 参数化权限测试（admin/site_admin 200，其余 403） | `@WithMockUser` + MockMvc |

### 10.2 前端测试

- **必须引入 Vitest**：`npm install -D vitest @vue/test-utils jsdom @vitest/coverage-v8`
- 每个新组件至少 3 个测试：正常渲染、空状态、边界值
- `package.json` 新增脚本：`"test": "vitest run"`, `"test:coverage": "vitest run --coverage"`
- 前端类型检查：`npx vue-tsc --noEmit`
- 前端构建：`npm run build`

### 10.3 覆盖率目标
- 后端：80%+（DashboardService + Controller 为核心）
- 前端：80%+（新组件为核心）

---

## 11. 文件清单

### 后端
- `src/main/java/gov/cms/admin/service/DashboardService.java`
- `src/main/java/gov/cms/admin/controller/HealthSummaryController.java`
- `src/main/java/gov/cms/admin/dto/HealthSummary.java`
- `src/main/java/gov/cms/admin/dto/DashboardDto.java`
- `src/test/java/gov/cms/admin/service/DashboardServiceTest.java`
- `src/test/java/gov/cms/admin/controller/HealthSummaryControllerTest.java`
- `src/test/java/gov/cms/admin/controller/DashboardControllerTest.java`（更新）

### 前端
- `frontend/src/components/ui/StatCard.vue`
- `frontend/src/components/ui/EmptyState.vue`
- `frontend/src/components/layout/MainLayout.vue`（从 components/ 根移入）
- `frontend/src/components/features/dashboard/ActivityFeed.vue`
- `frontend/src/components/features/dashboard/ActivityItem.vue`
- `frontend/src/components/features/dashboard/DashboardTaskList.vue`
- `frontend/src/components/features/dashboard/SystemHealthPanel.vue`
- `frontend/src/components/features/dashboard/types.ts`
- `frontend/src/components/features/dashboard/api.ts`
- `frontend/src/styles/tokens.css`
- `frontend/src/styles/views/dashboard.css`（按需）
- `frontend/src/views/Dashboard.vue`（重写）
- `frontend/vitest.config.ts`

---

## 12. 成功标准

- [ ] 后端 `DashboardController` 无 `findAll()`，使用原生 count 查询
- [ ] 各角色登录后 Dashboard 显示对应 widget，无越权数据
- [ ] `npx vue-tsc --noEmit` 通过
- [ ] `npm run build` 通过
- [ ] 后端单元测试覆盖率 ≥ 80%
- [ ] 前端 Vitest 组件测试通过，覆盖率 ≥ 80%
- [ ] `mvn test` 全部通过
- [ ] HealthSummary API 仅 admin/site_admin 可访问
- [ ] `prefers-reduced-motion` 生效
- [ ] 所有统计卡趋势/数据为真实值，无硬编码
