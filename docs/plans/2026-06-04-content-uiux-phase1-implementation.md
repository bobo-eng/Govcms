# 内容管理模块 UI/UX 优化（第一阶段）实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不改动后端的前提下，按 `docs/plans/2026-06-04-content-uiux-phase1-design.md` 完成 Content.vue 与 Review.vue 的视觉、交互与流程闭环优化。

**Architecture:** 所有改动集中在前端 `frontend/src/views/` 与公共样式 `frontend/src/styles/admin-refresh.css`；继续使用 Ant Design Vue 4 现有组件，不新增依赖；通过 `npx vue-tsc --noEmit` 与 `npm run build` 保证类型与构建正确。

**Tech Stack:** Vue 3, TypeScript, Vite, Ant Design Vue 4, CSS.

---

### Task 1: 添加公共骨架屏与空状态样式

**Files:**
- Modify: `frontend/src/styles/admin-refresh.css`（追加到文件末尾）

**Step 1: 写样式**

在 `admin-refresh.css` 末尾追加：

```css
/* Skeleton */
.admin-skeleton-row {
  height: 48px;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: admin-skeleton-shimmer 1.2s infinite;
  border-radius: 8px;
  margin-bottom: 8px;
}

@keyframes admin-skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Empty state */
.admin-empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #64748b;
  text-align: center;
}

.admin-empty-box svg {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  color: #94a3b8;
}

.admin-empty-box p {
  margin: 0 0 16px;
  font-size: 14px;
}
```

**Step 2: 验证无报错**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 3: Commit**

```bash
git add frontend/src/styles/admin-refresh.css
git commit -m "feat(content-ui): add skeleton and empty-state utilities"
```

---

### Task 2: Content.vue — 表格加载骨架屏与空状态

**Files:**
- Modify: `frontend/src/views/Content.vue`

**Step 1: 替换加载与空状态模板**

找到表格 `tbody` 区域（约第 317-323 行），将：

```html
<tr v-if="loading">
  <td colspan="6" class="admin-empty-cell">加载中...</td>
</tr>
<tr v-else-if="!articles.length">
  <td colspan="6" class="admin-empty-cell">暂无数据</td>
</tr>
```

替换为：

```html
<template v-if="loading">
  <tr v-for="n in 6" :key="`sk-${n}">
    <td colspan="6">
      <div class="admin-skeleton-row" style="margin: 8px 16px;"></div>
    </td>
  </tr>
</template>
<tr v-else-if="!articles.length">
  <td colspan="6">
    <div class="admin-empty-box">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <rect x="3" y="3" width="18" height="18" rx="4"/>
        <path d="M9 9h6H9z"/>
      </svg>
      <p>暂无内容稿件</p>
      <button v-if="canCreate" class="admin-primary-btn" @click="openCreate">新建内容</button>
    </div>
  </td>
</tr>
```

**Step 2: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 3: Commit**

```bash
git add frontend/src/views/Content.vue
git commit -m "feat(content-ui): skeleton loading and empty state in content list"
```

---

### Task 3: Content.vue — 删除确认弹窗

**Files:**
- Modify: `frontend/src/views/Content.vue`

**Step 1: 导入 Modal**

将 `import { message } from 'ant-design-vue'` 改为：

```ts
import { message, Modal } from 'ant-design-vue'
```

**Step 2: 替换 window.confirm**

找到 `removeArticle` 方法（约第 212-225 行），替换为：

```ts
const removeArticle = async (record: ArticleItem) => {
  if (!canDelete.value) {
    message.warning('没有删除内容权限')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `删除后《${record.title}》将无法恢复，是否继续？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteArticle(record.id)
        message.success('删除成功')
        await loadArticles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '删除失败')
      }
    }
  })
}
```

**Step 3: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 4: Commit**

```bash
git add frontend/src/views/Content.vue
git commit -m "feat(content-ui): replace window.confirm with Modal.confirm for delete"
```

---

### Task 4: Content.vue — 搜索防抖、清空筛选、分页页数选择

**Files:**
- Modify: `frontend/src/views/Content.vue`

**Step 1: 搜索防抖**

在 `filters` ref 之后（约第 52 行后）引入 timer：

```ts
let keywordDebounceTimer: ReturnType<typeof setTimeout> | null = null
```

在 `onMounted` 之前添加 watch：

```ts
watch(() => filters.value.keyword, () => {
  if (keywordDebounceTimer) clearTimeout(keywordDebounceTimer)
  keywordDebounceTimer = setTimeout(() => {
    pagination.value.current = 1
    loadArticles()
  }, 300)
})
```

将搜索 input 的 `@keyup.enter="loadArticles"` 移除：

```html
<input v-model="filters.keyword" class="admin-search-input" placeholder="搜索标题、摘要或作者" />
```

**Step 2: 清空筛选按钮**

在工具栏查询按钮后追加：

```html
<button class="admin-secondary-btn" @click="clearFilters">清空</button>
```

添加方法：

```ts
const clearFilters = () => {
  filters.value = { keyword: '', status: '', siteId: undefined, primaryCategoryId: undefined }
  pagination.value.current = 1
  loadArticles()
}
```

**Step 3: 分页页数选择**

替换分页区为：

```html
<div class="admin-pagination">
  <span class="admin-pagination-total">共 {{ pagination.total }} 条</span>
  <div class="admin-pagination-controls">
    <select v-model="pagination.pageSize" class="admin-filter-select" @change="pagination.current = 1; loadArticles()">
      <option :value="10">10 条/页</option>
      <option :value="20">20 条/页</option>
      <option :value="50">50 条/页</option>
    </select>
    <button class="admin-page-btn" :disabled="pagination.current <= 1" @click="pagination.current -= 1; loadArticles()">上一页</button>
    <span class="admin-page-info">第 {{ pagination.current }} 页</span>
    <button class="admin-page-btn" :disabled="pagination.current * pagination.pageSize >= pagination.total" @click="pagination.current += 1; loadArticles()">下一页</button>
  </div>
</div>
```

**Step 4: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 5: Commit**

```bash
git add frontend/src/views/Content.vue
git commit -m "feat(content-ui): debounce search, clear filters, and page size selector"
```

---

### Task 5: Content.vue — 顶部状态统计卡

**Files:**
- Modify: `frontend/src/views/Content.vue`

**Step 1: 添加计算属性**

在 `statusOptions` 定义之后添加：

```ts
const statusCounts = computed(() => {
  const map: Record<string, number> = {}
  articles.value.forEach(a => {
    map[a.status || 'draft'] = (map[a.status || 'draft'] || 0) + 1
  })
  return map
})
```

**Step 2: 添加模板区域**

在页面描述段落之后、工具栏卡片之前插入：

```html
<div class="admin-stats-row">
  <div
    v-for="s in statusOptions.filter(x => x.value)"
    :key="s.value"
    class="admin-stat-card"
    :class="{ active: filters.status === s.value }"
    @click="filters.status = s.value; pagination.current = 1; loadArticles()"
  >
    <span class="admin-stat-value">{{ statusCounts[s.value] || 0 }}</span>
    <span class="admin-stat-label">{{ s.label }}</span>
  </div>
</div>
```

**Step 3: 添加 scoped 样式**

在 `<style scoped>` 末尾追加：

```css
.admin-stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.admin-stat-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 20px;
  min-width: 100px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: all 0.15s;
}

.admin-stat-card:hover,
.admin-stat-card.active {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
}

.admin-stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.admin-stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}
```

**Step 4: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 5: Commit**

```bash
git add frontend/src/views/Content.vue
git commit -m "feat(content-ui): add status summary cards with quick filter"
```

---

### Task 6: Content.vue — 状态驱动的行内主操作

**Files:**
- Modify: `frontend/src/views/Content.vue`

**Step 1: 重构操作列**

替换操作列（约第 336-344 行）为：

```html
<td>
  <div class="article-actions">
    <button class="admin-link-action" @click="openEdit(item)">详情</button>

    <template v-if="item.status === 'draft' || item.status === 'rejected'">
      <button v-if="canUpdate" class="admin-link-action" @click="openEdit(item)">编辑</button>
      <button v-if="canSubmit" class="admin-link-action" @click="submitReviewAction(item)">提交审核</button>
      <button v-if="canDelete" class="admin-link-action danger-link" @click="removeArticle(item)">删除</button>
    </template>

    <template v-if="item.status === 'approved'">
      <button class="admin-link-action primary-link" @click="gotoPublish(item, 'incremental')">去发布中心</button>
    </template>

    <template v-if="item.status === 'published'">
      <button class="admin-link-action" @click="gotoPublish(item, 'offline')">下线</button>
    </template>

    <button class="admin-link-action" @click="viewPublishCheck(item)">发布检查</button>
  </div>
</td>
```

在 `<style scoped>` 增加：

```css
.primary-link {
  color: #2563eb;
  font-weight: 600;
}
.danger-link {
  color: #dc2626;
}
```

**Step 2: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 3: Commit**

```bash
git add frontend/src/views/Content.vue
git commit -m "feat(content-ui): status-driven primary row actions"
```

---

### Task 7: Review.vue — 审核确认、加载与空状态

**Files:**
- Modify: `frontend/src/views/Review.vue`

**Step 1: 导入 Modal**

将 `import { message } from 'ant-design-vue'` 改为：

```ts
import { message, Modal } from 'ant-design-vue'
```

**Step 2: 表格加载与空状态**

替换表格 `tbody` 中的加载与空状态（约第 158-164 行）：

```html
<template v-if="loading">
  <tr v-for="n in 5" :key="`sk-${n}">
    <td colspan="5">
      <div class="admin-skeleton-row" style="margin: 8px 16px;"></div>
    </td>
  </tr>
</template>
<tr v-else-if="!articles.length">
  <td colspan="5">
    <div class="admin-empty-box">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <rect x="3" y="3" width="18" height="18" rx="4"/>
        <path d="M9 9h6H9z"/>
      </svg>
      <p>当前没有待审核内容</p>
    </div>
  </td>
</tr>
```

**Step 3: 通过前增加 Modal.confirm**

在 `selectedArticle` ref 附近添加 `approvingId`：

```ts
const approvingId = ref<number | null>(null)
```

替换 `handleApprove`：

```ts
const handleApprove = async (record: ArticleItem) => {
  Modal.confirm({
    title: '确认通过',
    content: `确认通过《${record.title}》？通过后内容将进入待发布状态。`,
    okText: '通过',
    cancelText: '取消',
    async onOk() {
      approvingId.value = record.id
      try {
        await approveArticle(record.id)
        message.success('审核通过成功')
        if (selectedArticle.value?.id === record.id) {
          detailOpen.value = false
        }
        await loadArticles()
      } catch (error: any) {
        message.error(error.response?.data?.message || '审核通过失败')
      } finally {
        approvingId.value = null
      }
    }
  })
}
```

**Step 4: 驳回按钮 loading**

添加 `rejecting` ref：

```ts
const rejecting = ref(false)
```

修改 `handleReject`：

```ts
const handleReject = async () => {
  if (!selectedArticle.value) return
  if (!rejectReason.value.trim()) {
    message.warning('请输入驳回原因')
    return
  }
  rejecting.value = true
  try {
    await rejectArticle(selectedArticle.value.id, rejectReason.value.trim())
    message.success('驳回成功')
    rejectOpen.value = false
    detailOpen.value = false
    await loadArticles()
  } catch (error: any) {
    message.error(error.response?.data?.message || '驳回失败')
  } finally {
    rejecting.value = false
  }
}
```

驳回弹窗确认按钮绑定 loading：

```html
<button class="admin-danger-btn" :disabled="rejecting" @click="handleReject">
  {{ rejecting ? '驳回中...' : '确认驳回' }}
</button>
```

**Step 5: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 6: Commit**

```bash
git add frontend/src/views/Review.vue
git commit -m "feat(review-ui): confirm on approve, skeleton, empty state, and loading"
```

---

### Task 8: 修复筛选变化时页码未重置

**Files:**
- Modify: `frontend/src/views/Content.vue`

**Step 1: 统一筛选变化处理**

将栏目和状态下拉：

```html
<select v-model="filters.primaryCategoryId" class="admin-filter-select" @change="onFilterChange">
<select v-model="filters.status" class="admin-filter-select" @change="onFilterChange">
```

添加方法：

```ts
const onFilterChange = () => {
  pagination.value.current = 1
  loadArticles()
}
```

**Step 2: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 3: Commit**

```bash
git add frontend/src/views/Content.vue
git commit -m "fix(content-ui): reset page when filters change"
```

---

### Task 9: 最终验证

**Files:**
- 不涉及文件修改

**Step 1: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: `ok`

**Step 2: 生产构建检查**

Run: `cd frontend && npm run build`
Expected: `dist/` 生成成功，无 TypeScript 或 Vite 报错。

**Step 3: 手动走查清单**

启动 dev server：`cd frontend && npm run dev`

- [ ] Content 列表加载时显示 6 行骨架屏
- [ ] Content 空状态显示图标 + "暂无内容稿件" + 新建按钮（有权限时）
- [ ] 删除时弹出 `Modal.confirm` 并显示标题
- [ ] 搜索框输入后 300ms 自动搜索，不频繁请求
- [ ] 清空按钮重置所有筛选并刷新
- [ ] 分页页数切换正常工作
- [ ] 顶部统计卡点击后自动筛选对应状态
- [ ] 行内操作按钮随状态变化（草稿→编辑/提交/删除，待发布→去发布中心，已发布→下线）
- [ ] Review 列表加载显示骨架屏
- [ ] Review 空状态显示 "当前没有待审核内容"
- [ ] 审核通过前弹出确认弹窗
- [ ] 驳回按钮有 loading 文案

**Step 4: 提交最终改动（如有）**

```bash
git add frontend/
git commit -m "feat(content-ui): complete phase 1 UX refresh"
```
