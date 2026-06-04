# GovCMS 后台菜单重构设计

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:writing-plans to implement this plan task-by-task.

**Goal:** 解决当前后台菜单结构松散、路径不统一、权限错配的问题，同时按角色优化登录后的默认工作台入口。

**Architecture:** 保持现有 `Menu` 实体和 `MenuService.getUserMenus` 的权限驱动过滤模型不变，仅调整 `DataInitializer` 中的权限/角色/菜单种子数据，并统一前端路径。登录默认页由前端根据 `LoginResponse.roles[0]` 动态决定。

**Tech Stack:** Java 17, Spring Boot 3.2, Vue 3, Ant Design Vue 4

---

## 背景

当前菜单存在以下问题：
1. **结构松散**：用户/角色/权限/菜单都是一级菜单，未聚合到"系统管理"下。
2. **路径不一致**：内容管理子菜单大多是 `/content/*`，但"导航管理"是 `/navigation"，"专题管理"是 `/topics`。
3. **权限错配**：审计日志的 `permissionId` 是 `publish:center`，导致只有发布中心权限的人才能看到审计日志。
4. **仪表盘权限**：仪表盘绑定 `content:article:view`，没有内容查看权限的用户无法看到首页。
5. **角色工作台体验差**：所有角色登录后都跳到 `/dashboard`，但 editor、reviewer、publisher 的核心工作区分别是内容列表、审核工作区、发布中心。

## 设计范围

### 1. 新菜单结构

```
仪表盘              /dashboard                sys:dashboard:view
系统管理 ▼          /system                   sys
  用户管理          /system/users             sys:user
  角色管理          /system/roles             sys:role
  权限管理          /system/permissions       sys:permission
  菜单管理          /system/menus             sys:menu
  审计日志          /system/audit-logs        sys:audit:view
内容管理 ▼          /content                  content
  审核工作区        /content/review           content:article:review
  内容管理          /content/articles         content:article
  栏目管理          /content/categories       content:category
  模板管理          /content/templates        template:manage
  导航管理          /content/navigation       navigation:manage
  专题管理          /content/topics           topic:manage
  发布中心          /content/publish          publish:center
站点运营 ▼          /site-ops                 site:manage | media:manage | search:ops
  站点管理          /site-ops/sites           site:manage
  媒体管理          /site-ops/media           media:manage
  搜索运营          /site-ops/search-ops      search:ops
```

### 2. 权限变更

新增权限：
- `sys:dashboard:view` — 仪表盘查看
- `sys:audit:view` — 审计日志查看

角色权限调整：
- `admin`：添加 `sys:dashboard:view`、`sys:audit:view`
- `site_admin`：添加 `sys:dashboard:view`、`sys:audit:view`
- `editor`：添加 `sys:dashboard:view`
- `reviewer`：添加 `sys:dashboard:view`
- `publisher`：添加 `sys:dashboard:view`（**不添加审计日志权限**）
- `viewer`：添加 `sys:dashboard:view`

### 3. 旧菜单清理

`DataInitializer.seedMenus` 通过 `findByPath` 做 upsert。当路径变化时，旧路径菜单（如 `/users`）会残留。解决方案：在 `seedMenus` 开头先删除所有旧路径的菜单记录。

待删除的旧路径：
- `/users`
- `/roles`
- `/permissions`
- `/menus`
- `/navigation`
- `/topics`
- `/sites`
- `/media`
- `/search-ops`

### 4. 角色默认页

登录成功后，前端根据 `LoginResponse.roles[0]` 跳转：

| 角色 | 默认页 | 原因 |
|------|--------|------|
| `admin` | `/dashboard` | 系统概览 |
| `site_admin` | `/dashboard` | 系统概览 |
| `editor` | `/content` | 内容生产入口 |
| `reviewer` | `/content/review` | 审核工作区 |
| `publisher` | `/content/publish` | 发布中心 |
| `viewer` | `/dashboard` | 只读浏览 |

实现位置：`frontend/src/views/Login.vue` 中的 `router.push('/dashboard')`。

### 5. 数据流

```
DataInitializer.run()
  -> 删除旧路径菜单
  -> seedPermissions() [新增 sys:dashboard:view, sys:audit:view]
  -> seedRoles() [更新各角色权限集合]
  -> seedMenus() [按新结构和路径写入菜单]

User Login
  -> AuthController.login() [返回 roles]
  -> Login.vue [根据 roles[0] 决定跳转路径]
  -> MainLayout.vue [从 /api/menus/user 获取过滤后的菜单树]
```

### 6. 错误处理

- 旧菜单清理失败：记录 warn 日志，不影响新菜单写入
- 权限未正确分配：用户将看不到对应菜单（符合预期，按权限过滤）
- 前端角色匹配失败：fallback 到 `/dashboard`

### 7. 测试策略

| 测试类型 | 内容 |
|---------|------|
| 单元测试 | DataInitializer 启动后菜单树结构正确 |
| 集成测试 | 各角色用户登录后返回正确的默认路由 |
| 手动测试 | 各角色登录后侧边栏菜单显示符合预期 |

### 8. 已知限制

- 只调整菜单结构和路径，不改动 `Menu` 实体和 `MenuService` 的过滤逻辑
- 多角色用户的默认页取 `roles[0]`，未来可按优先级排序
- 旧菜单清理仅在应用启动时执行一次

## 文件清单

- `src/main/java/gov/cms/admin/config/DataInitializer.java`
- `frontend/src/views/Login.vue`
- `frontend/src/router/index.ts`（如需补路由重定向）
