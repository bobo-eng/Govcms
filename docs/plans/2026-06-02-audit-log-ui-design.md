# Audit Log 管理后台设计文档

## 背景

GovCMS 后端已具备完整的审计日志记录能力（`AuditLog` 实体、`AuditLogService`、`AuditLogController`），但缺少 admin UI 查询页面。本设计补齐这一功能闭环。

## 设计范围

- 后端：改造现有 `AuditLogController.list` 接口，增加分页排序
- 前端：新增 `AuditLogs.vue` 页面、路由、API 模块
- 菜单：新增"系统管理"菜单组及"审计日志"子项

## 后端改造

### 接口变更

`AuditLogController.getLogs` 由 `List<AuditLog>` 改为 `Page<AuditLog>`：

```java
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
```

- 默认分页：`size = 20`，按 `createdAt` 倒序
- 与 `SiteController`、`MediaController`、`UserController` 的分页模式保持一致

### Service & Repository

- `AuditLogService.list` 增加 `Pageable` 参数
- `AuditLogRepository.search` 调整为支持分页的 Spring Data JPA 查询方法

## 前端设计

### 页面：`AuditLogs.vue`

**路由：** `/system/audit-logs`

**布局结构：**

```
+--------------------------------------------------+
| 筛选栏                                            |
|  站点 | 操作类型 | 结果 | 操作人 | [查询] [重置]   |
+--------------------------------------------------+
| 表格（可展开行）                                   |
|  时间 | 操作人 | 操作类型 | 对象类型 | 对象ID | 结果 |
+--------------------------------------------------+
| 分页器（默认 20 条/页）                            |
+--------------------------------------------------+
```

### 筛选条件

| 字段 | 组件 | 说明 |
|------|------|------|
| siteId | `a-select` | 全局 admin 可见，site_admin 隐藏 |
| actionType | `a-select` | 固定枚举：publish, rollback, create, update, delete |
| result | `a-select` | success / failure |
| operatorName | `a-input` | 模糊搜索 |

### 表格列

| 字段 | 中文表头 | 说明 |
|------|----------|------|
| createdAt | 操作时间 | 格式：YYYY-MM-DD HH:mm:ss |
| operatorName | 操作人 | |
| actionType | 操作类型 | |
| objectType | 对象类型 | |
| objectId | 对象ID | |
| result | 结果 | `success` 绿色标签，`failure` 红色标签 |

### 展开行详情

点击行展开后显示：
- `summary`：操作摘要
- `failureReason`：失败原因（仅 failure 时展示，红色文本）

### API 模块

`frontend/src/api/auditLogs.ts`：

```typescript
import api from '../utils/api'

export const getAuditLogs = (params: any) => api.get('/audit-logs', { params })
```

### 权限控制

- 页面访问：`v-if="hasPermission('publish:center:view')"`
- 路由守卫复用现有 `router/index.ts` 的鉴权逻辑

## 菜单配置

由于菜单动态从 `/api/menus/user` 加载（由 `DataInitializer` 初始化）：

- 在 `DataInitializer` 中新增"系统管理"菜单组
- 新增"审计日志"菜单项，权限码 `publish:center:view`，路由 `/system/audit-logs`
- 前端 `MainLayout.vue` 动态渲染，无需硬编码

**注意：** 数据库中已有菜单数据，新增菜单需重新初始化或手动插入。

## 与现有系统的兼容性

- 分页模式与 `ArticleController`、`MediaController`、`SiteController`、`UserController` 完全一致
- 前端表格风格与 `Users.vue`、`Sites.vue` 等现有页面保持一致
- 权限体系复用现有 `publish:center:view`，无需新增权限码

## 验收标准

- [ ] 后端 `/api/audit-logs` 返回 `Page<AuditLog>`，支持分页和排序
- [ ] 前端 `/system/audit-logs` 页面可正常访问
- [ ] 表格支持按站点、操作类型、结果、操作人筛选
- [ ] 分页器工作正常，默认 20 条/页，按时间倒序
- [ ] 展开行可查看 summary 和 failureReason
- [ ] site_admin 只能看到所属站点的日志
- [ ] `mvn test` 全部通过
