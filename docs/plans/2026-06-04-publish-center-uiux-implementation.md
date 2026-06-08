# 发布中心 UI/UX 实现计划

> 基于 [2026-06-04-publish-center-uiux-design.md](./2026-06-04-publish-center-uiux-design.md)

---

## 架构

全部基于现有技术栈：Vue 3 + Ant Design Vue 4 + `admin-refresh.css`。不引入新依赖。

核心改动：
- `frontend/src/views/PublishCenter.vue` — 主页面重写
- `frontend/src/views/PublishTasks.vue` — 审批页面重写
- `frontend/src/api/publish.ts` — 补充状态映射常量
- `frontend/src/styles/admin-refresh.css` — 添加共享样式

---

## Task 1: 状态映射常量和类型补充

**Files:**
- Modify: `frontend/src/api/publish.ts`

**Step 1: 添加状态常量**

```typescript
export const PUBLISH_STATUS_ORDER = [
  'created', 'queued', 'staging_rendering', 'staging_ready',
  'approved', 'production_rendering', 'published'
] as const

export const publishStatusMeta: Record<string, { label: string; color: string; bg: string; isTerminal?: boolean; isException?: boolean }> = {
  created: { label: '已创建', color: '#64748b', bg: '#f1f5f9' },
  queued: { label: '排队中', color: '#64748b', bg: '#f1f5f9' },
  staging_rendering: { label: 'Staging渲染中', color: '#2563eb', bg: '#dbeafe' },
  staging_ready: { label: '待审批', color: '#d97706', bg: '#fef3c7' },
  approved: { label: '已批准', color: '#0891b2', bg: '#cffafe' },
  production_rendering: { label: 'Production渲染中', color: '#2563eb', bg: '#dbeafe' },
  published: { label: '已发布', color: '#15803d', bg: '#dcfce7', isTerminal: true },
  failed: { label: '失败', color: '#dc2626', bg: '#fee2e2', isException: true },
  rejected: { label: '已拒绝', color: '#991b1b', bg: '#fee2e2', isException: true },
  rolled_back: { label: '已回滚', color: '#7c3aed', bg: '#ede9fe', isTerminal: true },
  rollback_success: { label: '回滚成功', color: '#7c3aed', bg: '#ede9fe', isTerminal: true },
  rollback_failed: { label: '回滚失败', color: '#be123c', bg: '#ffe4e6', isException: true }
}

export const publishStatusLabel = (status: string) => publishStatusMeta[status]?.label || status
```

**Step 2: Commit**

```bash
git add frontend/src/api/publish.ts
git commit -m "feat(publish): add status meta constants and labels"
```

---

## Task 2: 共享样式补充

**Files:**
- Modify: `frontend/src/styles/admin-refresh.css`

**Step 1: 添加 Hero Stats 样式**

```css
.admin-stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.admin-stat-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.admin-stat-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.admin-stat-card.active {
  border-color: #93c5fd;
  background: #f8fafc;
}

.admin-stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  color: #0f172a;
}

.admin-stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}
```

**Step 2: 添加流水线列表样式**

```css
.publish-pipeline-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.publish-pipeline-item {
  display: flex;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.publish-pipeline-item:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.publish-pipeline-item.active {
  border-color: #93c5fd;
  background: #f8fafc;
}

.pipeline-status-bar {
  width: 3px;
  flex-shrink: 0;
  background: var(--status-color, #cbd5e1);
}

.pipeline-body {
  flex: 1;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pipeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pipeline-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  color: #0f172a;
}

.pipeline-id {
  font-variant-numeric: tabular-nums;
  color: #334155;
}

.pipeline-unit {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.pipeline-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pipeline-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.pipeline-status-dot.pulse {
  animation: pipeline-pulse 1.4s ease-in-out infinite;
}

@keyframes pipeline-pulse {
  0% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
  100% { opacity: 1; transform: scale(1); }
}

.pipeline-status-label {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 999px;
}

.pipeline-meta {
  display: flex;
  gap: 14px;
  font-size: 13px;
  color: #64748b;
}

.pipeline-error {
  font-size: 13px;
  color: #b91c1c;
  background: #fee2e2;
  border-radius: 8px;
  padding: 8px 10px;
}

.pipeline-actions {
  display: flex;
  gap: 12px;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.publish-pipeline-item:hover .pipeline-actions {
  opacity: 1;
  transform: translateY(0);
}
```

**Step 3: 添加状态机可视化条样式**

```css
.state-machine-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 12px;
  overflow-x: auto;
}

.state-step {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #e2e8f0;
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.state-step.passed {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #15803d;
}

.state-step.current {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1d4ed8;
  font-weight: 600;
}

.state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.state-label {
  line-height: 16px;
}
```

**Step 4: Commit**

```bash
git add frontend/src/styles/admin-refresh.css
git commit -m "feat(publish): add hero stats, pipeline list, state-machine shared styles"
```

---

## Task 3: PublishCenter.vue 重写

**Files:**
- Modify: `frontend/src/views/PublishCenter.vue`

**Step 1: 重写模板结构**

按设计文档的 5 个区域重写：
1. Hero Stats
2. 发布入口 + 实时摘要
3. 发布流水线
4. 任务摘要侧边栏
5. 任务详情弹窗（含状态机条 + 6 个标签页）

**Step 2: 重写脚本**

- 添加 `stateMachineSteps` computed
- 添加 `stateMachineIndex` computed
- 重写 `handleRollback` 使用 `Modal.confirm` + 输入框
- 重写 `handleRetry` 使用 `Modal.confirm`

**Step 3: Commit**

```bash
git add frontend/src/views/PublishCenter.vue
git commit -m "feat(publish): redesign PublishCenter with pipeline list and state machine"
```

---

## Task 4: PublishTasks.vue 重写

**Files:**
- Modify: `frontend/src/views/PublishTasks.vue`

**Step 1: 复用流水线列表样式**

复用 `publish-pipeline-item` 等样式，只显示 `staging_ready`、`failed`、`rejected` 状态的任务。

**Step 2: 添加审批操作**

- "批准"用主按钮（绿色）
- "拒绝"用边框按钮（红色）

**Step 3: Commit**

```bash
git add frontend/src/views/PublishTasks.vue
git commit -m "feat(publish): redesign PublishTasks with pipeline list and approval UI"
```

---

## Task 5: 类型检查与构建验证

**Step 1: TypeScript 检查**

```bash
cd frontend
npx vue-tsc --noEmit
```

**Step 2: 生产构建**

```bash
npm run build
```

**Step 3: Commit（如有修复）**

```bash
git add -A
git commit -m "fix(publish): resolve TypeScript and build issues"
```

---

## 成功标准

- [ ] `npx vue-tsc --noEmit` 通过
- [ ] `npm run build` 通过
- [ ] 页面视觉有明显设计感
- [ ] 状态颜色准确映射后端状态
- [ ] 回滚操作不再使用 `window.prompt`
- [ ] 详情弹窗有状态机可视化条和标签页分组
- [ ] 历史任务列表为垂直流水线样式
- [ ] PublishTasks.vue 与 PublishCenter 视觉一致
