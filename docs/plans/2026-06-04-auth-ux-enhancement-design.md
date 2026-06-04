# GovCMS 认证与 UX 增强设计

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:writing-plans to implement this plan task-by-task.

**Goal:** 完成"记住我"、"忘记密码"提示、"退出确认"、"右上角消息提醒"四个认证/UX增强功能。

**Architecture:** 后端新增 `Notification` 实体与 REST API 支撑消息中心；前端 `Login.vue` 和 `MainLayout.vue` 联动改造；`JwtUtil` 根据 rememberMe 参数调整 token TTL；不侵入现有业务代码，通知初始由 `DataInitializer` 注入示例数据。

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, JWT, Vue 3, Ant Design Vue 4, Axios

---

## 背景

当前系统已具备完整 RBAC 登录认证能力，但以下体验细节缺失：

1. **记住我**：Login.vue 有复选框 UI 但无实际功能。
2. **忘记密码**：有占位链接但无任何交互。
3. **退出提醒**：点击退出图标直接退出，无二次确认。
4. **消息提醒**：右上角铃铛仅有静态红点，无真实消息数据与交互。

## 设计范围

### 1. 记住我 (Remember Me)

**前端：**
- `Login.vue` 复选框绑定 `rememberMe: boolean`，登录请求体带上该字段。
- `saveSession()` 将 `rememberMe` 一并存入 `localStorage`。
- 进入 `/login` 路由时，若 `token` 存在且未过期且 `rememberMe === true`，直接按角色跳转到默认首页（免密自动登录）。

**后端：**
- `AuthController.login()` 接收 `rememberMe: boolean`。
- `JwtUtil.generateToken(String username, boolean rememberMe)` 根据参数设置不同过期时间：
  - 未勾选：2 小时（`JWT_EXPIRATION = 7200000`）
  - 勾选记住我：7 天（`JWT_EXPIRATION_REMEMBER = 604800000`）
- `JwtUtil.extractExpiration()` 正常解析，无需额外改动。

### 2. 忘记密码 (Forgot Password)

- 不新增自助重置流程（按需求走管理员重置）。
- `Login.vue` "忘记密码？"链接改为触发 Ant Design Vue `Modal.info`，提示文案：
  - "请联系管理员重置密码。管理员可在"系统管理 > 用户管理"中为您重置。"
- 无需后端改动，`sys:user:reset-password` 权限已存在。

### 3. 退出提醒 (Logout Confirmation)

- `MainLayout.vue` `handleLogout` 用 `Modal.confirm` 包裹：
  - 标题："确认退出"
  - 内容："您确定要退出当前账号吗？"
  - 确认按钮："退出"
  - 取消按钮："取消"
- 点击确认后再执行 `clearSession()` 并 `router.push('/login')`。

### 4. 消息提醒（通知中心）

#### 4.1 后端实体

```java
@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String type; // info, warning, error
    private boolean read;
    private LocalDateTime createdAt;
}
```

#### 4.2 后端 API

- `GET /api/notifications?page=0&size=5` — 当前登录用户消息列表（按 `createdAt` 倒序，分页）
- `GET /api/notifications/unread-count` — 当前用户未读消息数
- `PUT /api/notifications/{id}/read` — 单条标记已读
- `PUT /api/notifications/read-all` — 全部标记已读

所有接口需 JWT 认证。

#### 4.3 前端交互

- `MainLayout.vue` 右上角铃铛图标 `BellOutlined` 实现 `a-popover` 下拉面板：
  - 展示最近 5 条消息（标题 + 时间，未读加粗）
  - 每条消息 hover 显示"标记已读"按钮
  - 底部"查看全部"入口（跳转到 `/notifications`，本次仅预留）
  - 面板关闭时，若消息未手动标记已读，保持未读状态
- 未读消息用红点 + 数字角标（`a-badge`）展示。
- 轮询策略：
  - 每 30 秒请求一次 `unread-count`
  - `document.visibilitychange` 页面获得焦点时立即刷新一次
  - 轮询失败静默处理，不弹错误提示

#### 4.4 初始数据

`DataInitializer` 给默认 `admin` 用户插入 2 条示例通知：
1. "欢迎登录 GovCMS"（type: info）
2. "您的文章已通过审核"（type: info）

#### 4.5 后续扩展点

各业务模块如需推送通知，调用：
```java
notificationService.createNotification(userId, title, content, type);
```
本次不侵入现有业务代码。

### 5. 数据流

```
User Login
  -> Login.vue (rememberMe checkbox)
  -> POST /api/auth/login { username, password, rememberMe }
  -> AuthController -> JwtUtil.generateToken(username, rememberMe)
  -> returns token with adjusted expiration
  -> Login.vue saveSession({ token, username, roles, permissions, rememberMe })
  -> if rememberMe=true, next visit to /login auto-redirects to dashboard

User in MainLayout
  -> onMounted: fetchMenus + fetchUnreadCount
  -> setInterval(30s): fetchUnreadCount
  -> visibilitychange: fetchUnreadCount
  -> click bell: open popover with recent 5 notifications
  -> click "mark as read": PUT /api/notifications/{id}/read
  -> click logout: Modal.confirm -> clearSession -> /login
```

### 6. 错误处理

- 记住我 token 过期：按正常流程进入登录页。
- 消息轮询失败：静默忽略，不打扰用户。
- 消息 API 401：由 Axios 拦截器统一跳转登录页。
- 忘记密码 Modal：纯前端交互，无错误场景。
- 退出确认：取消则保持当前页面状态。

### 7. 测试策略

| 测试类型 | 内容 |
|---------|------|
| 单元测试 | `JwtUtil` 根据 `rememberMe` 生成不同过期时间的 token |
| 单元测试 | `NotificationService` 未读计数、标记已读、全部已读逻辑 |
| 集成测试 | `NotificationController` 各端点权限与数据隔离 |
| 手动测试 | 各角色登录后记住我自动跳转、消息红点、轮询、退出确认 |

### 8. 已知限制

- 忘记密码不实现自助重置，依赖管理员人工操作。
- 消息中心初始仅有示例数据，业务事件通知需后续各模块主动调用。
- 消息列表"查看全部"页面本次仅预留入口，不实现完整消息列表页。

## 文件清单

- `src/main/java/gov/cms/admin/entity/Notification.java`
- `src/main/java/gov/cms/admin/repository/NotificationRepository.java`
- `src/main/java/gov/cms/admin/service/NotificationService.java`
- `src/main/java/gov/cms/admin/controller/NotificationController.java`
- `src/main/java/gov/cms/admin/security/JwtUtil.java`（修改 generateToken 签名）
- `src/main/java/gov/cms/admin/controller/AuthController.java`（修改 login 接收 rememberMe）
- `src/main/java/gov/cms/admin/config/DataInitializer.java`（插入示例通知）
- `frontend/src/views/Login.vue`（记住我、忘记密码、自动跳转）
- `frontend/src/components/MainLayout.vue`（退出确认、消息中心）
- `frontend/src/utils/session.ts`（扩展 saveSession / loadSession 接口）
