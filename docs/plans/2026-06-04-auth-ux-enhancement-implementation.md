# GovCMS 认证与 UX 增强实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按已批准的设计文档完成"记住我"、"忘记密码"提示、"退出确认"、"右上角消息提醒"四个认证/UX增强功能。

**Architecture:** 后端新增 `Notification` 实体与 REST API 支撑消息中心；前端 `Login.vue` 和 `MainLayout.vue` 联动改造；`JwtUtil` 根据 rememberMe 参数调整 token TTL；不侵入现有业务代码，通知初始由 `DataInitializer` 注入示例数据。

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, JWT, Vue 3, Ant Design Vue 4, Axios

---

## Task 1: 后端 Notification 实体 + Repository

**Files:**
- Create: `src/main/java/gov/cms/admin/entity/Notification.java`
- Create: `src/main/java/gov/cms/admin/repository/NotificationRepository.java`

**Step 1: 创建 Notification 实体**

```java
package gov.cms.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false, length = 20)
    private String type = "info";

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

**Step 2: 创建 NotificationRepository**

```java
package gov.cms.admin.repository;

import gov.cms.admin.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);
}
```

**Step 3: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/entity/Notification.java src/main/java/gov/cms/admin/repository/NotificationRepository.java
git commit -m "feat(notification): add Notification entity and repository"
```

---

## Task 2: 后端 NotificationService

**Files:**
- Create: `src/main/java/gov/cms/admin/service/NotificationService.java`

**Step 1: 创建 NotificationService**

```java
package gov.cms.admin.service;

import gov.cms.admin.entity.Notification;
import gov.cms.admin.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Page<Notification> findByUserId(Long userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public long countUnreadByUserId(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to user");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent()
                .forEach(n -> {
                    if (!n.isRead()) {
                        n.setRead(true);
                        notificationRepository.save(n);
                    }
                });
    }

    @Transactional
    public Notification createNotification(Long userId, String title, String content, String type) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRead(false);
        return notificationRepository.save(notification);
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/NotificationService.java
git commit -m "feat(notification): add NotificationService with CRUD and unread count"
```

---

## Task 3: 后端 NotificationController

**Files:**
- Create: `src/main/java/gov/cms/admin/controller/NotificationController.java`

**Step 1: 创建 NotificationController**

```java
package gov.cms.admin.controller;

import gov.cms.admin.entity.Notification;
import gov.cms.admin.entity.User;
import gov.cms.admin.service.NotificationService;
import gov.cms.admin.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Page<Notification>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(notificationService.findByUserId(user.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        long count = notificationService.countUnreadByUserId(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/NotificationController.java
git commit -m "feat(notification): add NotificationController with list, unread-count, mark-read endpoints"
```

---

## Task 4: 后端 JwtUtil 支持 rememberMe

**Files:**
- Modify: `src/main/java/gov/cms/admin/security/JwtUtil.java`

**Step 1: 阅读 JwtUtil 当前代码**

先读取 `src/main/java/gov/cms/admin/security/JwtUtil.java` 确认当前 generateToken 签名。

**Step 2: 修改 generateToken 方法**

找到 `generateToken(String username)`，修改为：

```java
private static final long JWT_EXPIRATION = 7200000; // 2 hours
private static final long JWT_EXPIRATION_REMEMBER = 604800000; // 7 days

public String generateToken(String username, boolean rememberMe) {
    long expiration = rememberMe ? JWT_EXPIRATION_REMEMBER : JWT_EXPIRATION;
    return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}

public String generateToken(String username) {
    return generateToken(username, false);
}
```

注意：如果项目使用 SM2 签名而非 HS256，保持原有 `signWith` 方式不变，只修改过期时间逻辑。

**Step 3: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/security/JwtUtil.java
git commit -m "feat(auth): support rememberMe with extended token TTL (7 days)"
```

---

## Task 5: 后端 AuthController 接收 rememberMe

**Files:**
- Modify: `src/main/java/gov/cms/admin/controller/AuthController.java`

**Step 1: 修改 login 方法**

找到 `login` 方法，修改请求 DTO 或参数以接收 `rememberMe`。

如果当前 login 方法参数是 `LoginRequest`，在 `LoginRequest` 中新增字段：

```java
private boolean rememberMe;
// getter + setter
```

然后修改 `login` 方法中生成 token 的调用：

```java
String token = jwtUtil.generateToken(user.getUsername(), loginRequest.isRememberMe());
```

返回的响应中可带上 `rememberMe` 标志（可选）。

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/controller/AuthController.java src/main/java/gov/cms/admin/dto/LoginRequest.java
git commit -m "feat(auth): accept rememberMe flag in login request"
```

---

## Task 6: 后端 DataInitializer 插入示例通知

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/DataInitializer.java`

**Step 1: 新增 seedNotifications 方法**

在 `seedDefaultAdmin` 之后添加：

```java
private void seedNotifications(NotificationRepository notificationRepository, UserRepository userRepository) {
    User admin = userRepository.findByUsername("admin").orElse(null);
    if (admin == null) {
        return;
    }
    if (notificationRepository.countByUserIdAndReadFalse(admin.getId()) > 0) {
        return; // already seeded
    }

    Notification n1 = new Notification();
    n1.setUserId(admin.getId());
    n1.setTitle("欢迎登录 GovCMS");
    n1.setContent("欢迎使用 GovCMS 政府内容管理系统。");
    n1.setType("info");
    n1.setRead(false);
    notificationRepository.save(n1);

    Notification n2 = new Notification();
    n2.setUserId(admin.getId());
    n2.setTitle("您的文章已通过审核");
    n2.setContent("您提交的文章已由审核员通过，请前往发布中心执行发布。");
    n2.setType("info");
    n2.setRead(false);
    notificationRepository.save(n2);
}
```

并在 `initData` 中调用 `seedNotifications(notificationRepository, userRepository)`，确保注入 `NotificationRepository`。

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/config/DataInitializer.java
git commit -m "feat(notification): seed sample notifications for admin user"
```

---

## Task 7: 前端 session.ts 扩展 rememberMe

**Files:**
- Modify: `frontend/src/utils/session.ts`

**Step 1: 修改 saveSession 和 loadSession**

找到 `saveSession` 和 `loadSession`，确保支持 `rememberMe` 字段：

```typescript
interface SessionData {
  token: string
  username: string
  roles: string[]
  permissions: string[]
  rememberMe?: boolean
}

export function saveSession(data: SessionData) {
  localStorage.setItem('govcms_session', JSON.stringify(data))
}

export function loadSession(): SessionData | null {
  const raw = localStorage.getItem('govcms_session')
  return raw ? JSON.parse(raw) : null
}

export function getToken(): string | null {
  return loadSession()?.token || null
}

export function getUsername(): string | null {
  return loadSession()?.username || null
}

export function getRoles(): string[] {
  return loadSession()?.roles || []
}

export function clearSession() {
  localStorage.removeItem('govcms_session')
}
```

**Step 2: 验证 TypeScript**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

**Step 3: Commit**

```bash
git add frontend/src/utils/session.ts
git commit -m "feat(session): extend session utils with rememberMe field"
```

---

## Task 8: 前端 Login.vue 记住我 + 忘记密码 + 自动跳转

**Files:**
- Modify: `frontend/src/views/Login.vue`

**Step 1: 修改 script setup**

```typescript
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import axios from 'axios'
import { clearSession, saveSession, loadSession } from '../utils/session'

const router = useRouter()
const loading = ref(false)

const formState = reactive({
  username: '',
  password: '',
  rememberMe: false
})

onMounted(() => {
  const session = loadSession()
  if (session?.token && session?.rememberMe) {
    // Token validity is checked by backend on next request;
    // here we just auto-redirect to skip the login page.
    const roleCode = session.roles?.[0] || ''
    const defaultRoute: Record<string, string> = {
      reviewer: '/content/review',
      publisher: '/content/publish',
      editor: '/content'
    }
    router.push(defaultRoute[roleCode] || '/dashboard')
  }
})

const onFinish = async () => {
  if (!formState.username || !formState.password) {
    message.warning('请输入用户名和密码')
    return
  }

  loading.value = true

  try {
    const res = await axios.post('/api/auth/login', {
      username: formState.username,
      password: formState.password,
      rememberMe: formState.rememberMe
    }, { timeout: 10000 })

    if (res.data.token) {
      clearSession()
      saveSession({
        token: res.data.token,
        username: res.data.username || formState.username,
        roles: res.data.roles || [],
        permissions: res.data.permissions || [],
        rememberMe: formState.rememberMe
      })
      message.success('登录成功')
      const roleCode = res.data.roles?.[0] || ''
      const defaultRoute: Record<string, string> = {
        reviewer: '/content/review',
        publisher: '/content/publish',
        editor: '/content'
      }
      router.push(defaultRoute[roleCode] || '/dashboard')
      return
    }

    message.error(res.data?.message || '登录失败')
  } catch (error) {
    if (axios.isAxiosError(error)) {
      if (error.response) {
        message.error(error.response.data?.message || '用户名或密码错误')
      } else if (error.request) {
        message.error('无法连接到服务器')
      } else {
        message.error('登录失败')
      }
      return
    }
    message.error('登录失败')
  } finally {
    loading.value = false
  }
}

const showForgotPassword = () => {
  Modal.info({
    title: '忘记密码？',
    content: '请联系管理员重置密码。管理员可在"系统管理 > 用户管理"中为您重置。',
    okText: '知道了'
  })
}
```

**Step 2: 修改模板中的记住我复选框和忘记密码链接**

确保模板中：
- 复选框 `v-model="formState.rememberMe"`
- 忘记密码链接 `@click.prevent="showForgotPassword"`

```vue
<div class="form-options">
  <label class="checkbox-wrapper">
    <input type="checkbox" class="checkbox" v-model="formState.rememberMe" />
    <span class="checkbox-label">记住我</span>
  </label>
  <a href="#" class="link" @click.prevent="showForgotPassword">忘记密码？</a>
</div>
```

**Step 3: 验证 TypeScript**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

**Step 4: Commit**

```bash
git add frontend/src/views/Login.vue
git commit -m "feat(auth): remember-me, forgot-password modal, and auto-redirect"
```

---

## Task 9: 前端 MainLayout.vue 退出确认 + 消息中心

**Files:**
- Modify: `frontend/src/components/MainLayout.vue`

**Step 1: 引入 Modal 和新增状态**

```typescript
import { Modal } from 'ant-design-vue'

const notifications = ref<any[]>([])
const unreadCount = ref(0)
const notificationVisible = ref(false)
```

**Step 2: 修改 handleLogout**

```typescript
const handleLogout = () => {
  Modal.confirm({
    title: '确认退出',
    content: '您确定要退出当前账号吗？',
    okText: '退出',
    cancelText: '取消',
    onOk: () => {
      clearSession()
      router.push('/login')
    }
  })
}
```

**Step 3: 新增消息相关方法**

```typescript
const fetchUnreadCount = async () => {
  try {
    const res = await api.get('/notifications/unread-count')
    unreadCount.value = res.data.count || 0
  } catch {
    // silent fail
  }
}

const fetchNotifications = async () => {
  try {
    const res = await api.get('/notifications?page=0&size=5')
    notifications.value = res.data.content || []
  } catch {
    notifications.value = []
  }
}

const markAsRead = async (id: number) => {
  try {
    await api.put(`/notifications/${id}/read`)
    await fetchUnreadCount()
    await fetchNotifications()
  } catch {
    // silent fail
  }
}

const markAllAsRead = async () => {
  try {
    await api.put('/notifications/read-all')
    await fetchUnreadCount()
    await fetchNotifications()
  } catch {
    // silent fail
  }
}
```

**Step 4: 在 onMounted 中启动轮询**

```typescript
onMounted(async () => {
  username.value = getUsername() || 'Admin'
  const roleCode = getRoles()[0] || ''
  roleLabel.value = ({ admin: '管理员', site_admin: '站点管理员', editor: '编辑', reviewer: '审核员', publisher: '发布员' } as Record<string, string>)[roleCode] || '用户'
  await fetchMenus()
  updateSelectedKeys()
  await fetchUnreadCount()
  await fetchNotifications()

  const interval = setInterval(fetchUnreadCount, 30000)
  const onVisibilityChange = () => {
    if (!document.hidden) {
      fetchUnreadCount()
    }
  }
  document.addEventListener('visibilitychange', onVisibilityChange)

  // cleanup on unmount (optional for Vue 3 composition API without explicit unmount hook in this scope)
})
```

**Step 5: 修改模板中的铃铛区域**

用 `a-badge` + `a-popover` 包裹铃铛：

```vue
<div class="header-action">
  <a-popover
    v-model:open="notificationVisible"
    placement="bottomRight"
    trigger="click"
    @open-change="(visible: boolean) => { if (visible) fetchNotifications() }"
  >
    <template #content>
      <div style="width: 320px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <span style="font-weight: 600;">消息通知</span>
          <a v-if="unreadCount > 0" @click="markAllAsRead">全部已读</a>
        </div>
        <div v-if="notifications.length === 0" style="color: #94a3b8; text-align: center; padding: 16px;">暂无消息</div>
        <div
          v-for="n in notifications"
          :key="n.id"
          style="padding: 8px 0; border-bottom: 1px solid #f1f5f9; cursor: pointer;"
          @click="!n.read && markAsRead(n.id)"
        >
          <div style="display: flex; justify-content: space-between;">
            <span :style="{ fontWeight: n.read ? 'normal' : '600', color: '#1e293b' }">{{ n.title }}</span>
            <span style="font-size: 12px; color: #94a3b8;">{{ n.createdAt }}</span>
          </div>
          <div style="font-size: 13px; color: #64748b; margin-top: 4px;">{{ n.content }}</div>
        </div>
      </div>
    </template>
    <a-badge :count="unreadCount" :offset="[-4, 4]">
      <BellOutlined />
    </a-badge>
  </a-popover>
</div>
```

注意：模板中直接用内联 style 是临时的，若项目有现成样式体系可替换。

**Step 6: 验证 TypeScript**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

**Step 7: Commit**

```bash
git add frontend/src/components/MainLayout.vue
git commit -m "feat(ui): logout confirmation and notification center with polling"
```

---

## Task 10: 后端编译与测试

**Step 1: 后端编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 2: 后端测试**

Run: `mvn test -q`
Expected: 全部通过（现有测试不受影响）

**Step 3: Commit（如有调整）**

```bash
git add -A
git commit -m "chore: verify backend compilation and tests" || echo "Nothing to commit"
```

---

## Task 11: 前端类型检查与构建

**Step 1: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

**Step 2: 生产构建**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

**Step 3: Commit（如有调整）**

```bash
git add -A
git commit -m "chore: verify frontend build" || echo "Nothing to commit"
```

---

## Task 12: 最终确认

**Step 1: 检查 git status**

Run: `git status`
Expected: clean

**Step 2: 功能清单**

| 功能 | 状态 |
|------|------|
| 记住我 | 勾选后 token 7 天有效，下次打开登录页自动跳转 |
| 忘记密码 | 点击弹出 Modal，提示联系管理员 |
| 退出提醒 | 点击退出图标弹出确认框 |
| 消息提醒 | 铃铛角标显示未读数，点击下拉展示最近 5 条，30 秒轮询 |

---

## 执行选项

**Plan complete and saved to `docs/plans/2026-06-04-auth-ux-enhancement-implementation.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open a new session with `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
