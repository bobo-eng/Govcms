# GovCMS 后台菜单重构实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按已批准的设计文档重构后台菜单结构、修正权限错配、统一路径、优化角色默认工作台入口。

**Architecture:** 保持 `MenuService` 不变，只改 `DataInitializer` 中的权限/角色/菜单种子数据，前端 `Login.vue` 按角色动态跳转。

**Tech Stack:** Java 17, Spring Boot 3.2, Vue 3

---

## Task 1: DataInitializer 新增权限

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/DataInitializer.java`

**Step 1: 在 buildPermissions() 中新增两个权限**

找到 `buildPermissions()` 返回的列表，在 `sys` 相关权限之前添加：

```java
createPermission("sys:dashboard", "仪表盘", "sys:dashboard:view", "menu", "sys", "/dashboard", "DashboardOutlined", 0),
createPermission("sys:audit", "审计日志", "sys:audit:view", "menu", "sys", "/system/audit-logs", "AuditOutlined", 5),
```

注意：`sys:dashboard` 的 parentId 为 `sys`，path 为 `/dashboard`。

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/config/DataInitializer.java
git commit -m "feat(menu): add dashboard and audit log permissions"
```

---

## Task 2: DataInitializer 更新角色权限

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/DataInitializer.java`

**Step 1: 给所有角色添加 `sys:dashboard:view`**

修改 `admin`、`site_admin`、`editor`、`reviewer`、`publisher`、`viewer` 的权限集合，全部加入 `"sys:dashboard:view"`。

**Step 2: 给 admin 和 site_admin 添加 `sys:audit:view`**

```java
"sys:audit:view"
```

**Step 3: 确认 publisher 不包含 `sys:audit:view`**

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/config/DataInitializer.java
git commit -m "feat(menu): assign dashboard permission to all roles, audit to admin only"
```

---

## Task 3: DataInitializer 清理旧路径菜单

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/DataInitializer.java`

**Step 1: 在 seedMenus 开头添加清理逻辑**

```java
private void seedMenus(MenuRepository menuRepository) {
    // Clean up legacy top-level menus that are being restructured
    List<String> legacyPaths = List.of(
        "/users", "/roles", "/permissions", "/menus",
        "/navigation", "/topics", "/sites", "/media", "/search-ops"
    );
    for (String path : legacyPaths) {
        menuRepository.findByPath(path).ifPresent(menuRepository::delete);
    }

    // ... rest of seeding
}
```

**注意：** 需要确认 `MenuRepository` 有 `findByPath(String)` 和 `delete(Menu)` 方法。如果不存在，先查看 `MenuRepository`。

**Step 2: Commit**

```bash
git add src/main/java/gov/cms/admin/config/DataInitializer.java
git commit -m "feat(menu): cleanup legacy menu paths before re-seeding"
```

---

## Task 4: DataInitializer 重构菜单结构

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/DataInitializer.java`

**Step 1: 替换 seedMenus 中的菜单定义**

```java
private void seedMenus(MenuRepository menuRepository) {
    // Cleanup legacy paths
    List<String> legacyPaths = List.of(
        "/users", "/roles", "/permissions", "/menus",
        "/navigation", "/topics", "/sites", "/media", "/search-ops"
    );
    for (String path : legacyPaths) {
        menuRepository.findByPath(path).ifPresent(menuRepository::delete);
    }

    upsertMenu(menuRepository, createMenu("仪表盘", "/dashboard", "DashboardOutlined", null, 1, "sys:dashboard:view"), null);

    Menu systemMenu = upsertMenu(menuRepository, createMenu("系统管理", "/system", "SettingOutlined", null, 2, "sys"), null);
    upsertMenu(menuRepository, createMenu("用户管理", "/system/users", "UserOutlined", systemMenu.getId(), 1, "sys:user"), systemMenu.getId());
    upsertMenu(menuRepository, createMenu("角色管理", "/system/roles", "TeamOutlined", systemMenu.getId(), 2, "sys:role"), systemMenu.getId());
    upsertMenu(menuRepository, createMenu("权限管理", "/system/permissions", "LockOutlined", systemMenu.getId(), 3, "sys:permission"), systemMenu.getId());
    upsertMenu(menuRepository, createMenu("菜单管理", "/system/menus", "MenuOutlined", systemMenu.getId(), 4, "sys:menu"), systemMenu.getId());
    upsertMenu(menuRepository, createMenu("审计日志", "/system/audit-logs", "AuditOutlined", systemMenu.getId(), 5, "sys:audit:view"), systemMenu.getId());

    Menu contentMenu = upsertMenu(menuRepository, createMenu("内容管理", "/content", "FileTextOutlined", null, 3, "content"), null);
    upsertMenu(menuRepository, createMenu("审核工作区", "/content/review", "AuditOutlined", contentMenu.getId(), 1, "content:article:review"), contentMenu.getId());
    upsertMenu(menuRepository, createMenu("内容管理", "/content/articles", "FileTextOutlined", contentMenu.getId(), 2, "content:article"), contentMenu.getId());
    upsertMenu(menuRepository, createMenu("栏目管理", "/content/categories", "FolderOutlined", contentMenu.getId(), 3, "content:category"), contentMenu.getId());
    upsertMenu(menuRepository, createMenu("模板管理", "/content/templates", "LayoutOutlined", contentMenu.getId(), 4, "template:manage"), contentMenu.getId());
    upsertMenu(menuRepository, createMenu("导航管理", "/content/navigation", "MenuOutlined", contentMenu.getId(), 5, "navigation:manage"), contentMenu.getId());
    upsertMenu(menuRepository, createMenu("专题管理", "/content/topics", "FileTextOutlined", contentMenu.getId(), 6, "topic:manage"), contentMenu.getId());
    upsertMenu(menuRepository, createMenu("发布中心", "/content/publish", "SendOutlined", contentMenu.getId(), 7, "publish:center"), contentMenu.getId());

    Menu siteOpsMenu = upsertMenu(menuRepository, createMenu("站点运营", "/site-ops", "GlobalOutlined", null, 4, "site:manage"), null);
    upsertMenu(menuRepository, createMenu("站点管理", "/site-ops/sites", "GlobalOutlined", siteOpsMenu.getId(), 1, "site:manage"), siteOpsMenu.getId());
    upsertMenu(menuRepository, createMenu("媒体管理", "/site-ops/media", "CloudOutlined", siteOpsMenu.getId(), 2, "media:manage"), siteOpsMenu.getId());
    upsertMenu(menuRepository, createMenu("搜索运营", "/site-ops/search-ops", "SearchOutlined", siteOpsMenu.getId(), 3, "search:ops"), siteOpsMenu.getId());
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/config/DataInitializer.java
git commit -m "feat(menu): restructure menu hierarchy and unify paths"
```

---

## Task 5: 前端登录角色默认页

**Files:**
- Modify: `frontend/src/views/Login.vue`

**Step 1: 修改登录成功后的跳转逻辑**

找到 `router.push('/dashboard')`，替换为：

```typescript
const roleCode = res.data.roles?.[0] || ''
const defaultRoute: Record<string, string> = {
  reviewer: '/content/review',
  publisher: '/content/publish',
  editor: '/content'
}
router.push(defaultRoute[roleCode] || '/dashboard')
```

**Step 2: 验证 TypeScript**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

**Step 3: Commit**

```bash
git add frontend/src/views/Login.vue
git commit -m "feat(menu): role-based default route after login"
```

---

## Task 6: 运行后端并验证菜单结构

**Files:**
- N/A

**Step 1: 启动应用**

Run: `mvn spring-boot:run`

**Step 2: 启动前端**

Run: `cd frontend && npm run dev`

**Step 3: 验证各角色登录后的菜单和跳转**

| 角色 | 账号 | 预期默认页 | 预期可见菜单 |
|------|------|-----------|-------------|
| admin | admin/admin123 | /dashboard | 全部 |
| editor | 需创建 | /content | 仪表盘 + 内容管理 |
| reviewer | 需创建 | /content/review | 仪表盘 + 内容管理（主要是审核） |
| publisher | 需创建 | /content/publish | 仪表盘 + 内容管理 + 站点运营 |

**Step 4: Commit（如有调整）**

```bash
git add -A
git commit -m "chore(menu): verify menu redesign manually" || echo "Nothing to commit"
```

---

## Task 7: 全量编译与提交

**Step 1: 后端编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 2: 前端类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

**Step 3: 最终确认**

```bash
git status
```
Expected: clean

---

## 执行选项

**Plan complete and saved to `docs/plans/2026-06-04-menu-redesign-implementation.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open a new session with `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
