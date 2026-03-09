# 权限与角色管理系统设计

## 1. 权限模型设计

### 1.1 权限类型

| 类型 | 说明 | 示例 |
|------|------|------|
| **菜单权限** | 控制页面访问 | 用户管理、内容管理 |
| **按钮权限** | 控制操作功能 | 新增、编辑、删除 |
| **API权限** | 控制接口访问 | GET /api/users |
| **数据权限** | 控制数据范围 | 本部门、本人、全部 |

### 1.2 权限结构

```json
{
  "id": "perm_001",
  "name": "用户管理",
  "code": "user:view",
  "type": "menu",
  "parentId": null,
  "path": "/users",
  "icon": "UserOutlined",
  "sort": 1,
  "children": [
    {
      "id": "perm_001_1",
      "name": "新增用户",
      "code": "user:create",
      "type": "button",
      "parentId": "perm_001"
    },
    {
      "id": "perm_001_2", 
      "name": "编辑用户",
      "code": "user:update",
      "type": "button",
      "parentId": "perm_001"
    },
    {
      "id": "perm_001_3",
      "name": "删除用户",
      "code": "user:delete",
      "type": "button", 
      "parentId": "perm_001"
    }
  ]
}
```

### 1.3 系统预设权限

```
系统管理
├── 用户管理 (sys:user)
│   ├── 查看用户 (sys:user:view)
│   ├── 新增用户 (sys:user:create)
│   ├── 编辑用户 (sys:user:update)
│   └── 删除用户 (sys:user:delete)
├── 角色管理 (sys:role)
│   ├── 查看角色 (sys:role:view)
│   ├── 新增角色 (sys:role:create)
│   ├── 编辑角色 (sys:role:update)
│   └── 删除角色 (sys:role:delete)
├── 组织管理 (sys:org)
│   ├── 查看组织 (sys:org:view)
│   ├── 新增组织 (sys:org:create)
│   ├── 编辑组织 (sys:org:update)
│   └── 删除组织 (sys:org:delete)
└── 系统配置 (sys:config)
    └── 系统配置 (sys:config:view)

内容管理
├── 内容管理 (content:article)
│   ├── 查看内容 (content:article:view)
│   ├── 新增内容 (content:article:create)
│   ├── 编辑内容 (content:article:update)
│   ├── 删除内容 (content:article:delete)
│   └── 发布内容 (content:article:publish)
├── 栏目管理 (content:category)
│   ├── 查看栏目 (content:category:view)
│   ├── 新增栏目 (content:category:create)
│   ├── 编辑栏目 (content:category:update)
│   └── 删除栏目 (content:category:delete)
└── 媒体管理 (content:media)
    ├── 查看媒体 (content:media:view)
    └── 上传媒体 (content:media:upload)
```

---

## 2. 角色模型设计

### 2.1 角色结构

```json
{
  "id": 1,
  "name": "系统管理员",
  "code": "admin",
  "description": "拥有系统所有权限",
  "status": "enabled",
  "sort": 1,
  "permissions": [
    { "id": "perm_001", "code": "user:view" },
    { "id": "perm_001_1", "code": "user:create" }
  ],
  "createdAt": "2026-01-01",
  "updatedAt": "2026-01-01"
}
```

### 2.2 预设角色

| 角色编码 | 角色名称 | 说明 |
|---------|----------|------|
| admin | 系统管理员 | 超级管理员，拥有所有权限 |
| org_admin | 组织管理员 | 管理本组织所有用户和内容 |
| editor | 内容编辑 | 创建、编辑内容 |
| reviewer | 审核人员 | 审核内容 |
| viewer | 普通用户 | 仅查看内容 |

---

## 3. 用户-角色-权限关系

```
用户 ←→ 角色 ←→ 权限
  ↓
  └──→ 组织 (数据隔离)
```

### 3.1 关系表设计

```sql
-- 用户角色关联表
CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

-- 角色权限关联表  
CREATE TABLE role_permissions (
  role_id BIGINT NOT NULL,
  permission_id VARCHAR(50) NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);
```

---

## 4. API 接口设计

### 4.1 权限管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/permissions | 获取权限列表（树形） |
| GET | /api/permissions/all | 获取所有权限（扁平） |
| GET | /api/permissions/{id} | 获取权限详情 |
| GET | /api/permissions/menu | 获取用户有权限的菜单 |

### 4.2 角色管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/roles | 获取角色列表 |
| POST | /api/roles | 创建角色 |
| GET | /api/roles/{id} | 获取角色详情 |
| PUT | /api/roles/{id} | 更新角色 |
| DELETE | /api/roles/{id} | 删除角色 |
| PUT | /api/roles/{id}/permissions | 分配权限 |

### 4.3 用户角色管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/users/{id}/roles | 获取用户角色 |
| PUT | /api/users/{id}/roles | 分配用户角色 |

---

## 5. 数据权限设计

### 5.1 权限范围

| 范围编码 | 范围名称 | 说明 |
|----------|----------|------|
| ALL | 全部数据 | 超级管理员 |
| ORG | 组织数据 | 本组织数据 |
| DEPT | 部门数据 | 本部门数据 |
| SELF | 个人数据 | 仅本人数据 |

### 5.2 数据权限注解

```java
@DataScope(orgScope = true, deptScope = true)
public List<User> findUsers() {
    // 自动过滤数据范围
}
```

---

## 6. 前端设计

### 6.1 角色管理页面

- 角色列表（表格）
- 新增/编辑角色弹窗
- 权限分配（树形选择）
- 角色状态开关

### 6.2 权限管理页面

- 权限树展示
- 权限详情
- 菜单图标选择

### 6.3 用户分配角色

- 用户列表
- 角色多选

---

## 7. 实现计划

### Phase 1: 基础权限
- [ ] 权限实体 & Repository
- [ ] 角色实体 & Repository
- [ ] 权限管理 API
- [ ] 角色管理 API
- [ ] 预设权限数据初始化

### Phase 2: 用户集成
- [ ] 用户-角色关联
- [ ] 角色分配 API
- [ ] 前端角色管理页面

### Phase 3: 权限控制
- [ ] 接口权限拦截
- [ ] 按钮级别控制
- [ ] 菜单权限过滤
- [ ] 数据权限注解

---

*设计版本: 1.0*  
*更新时间: 2026-03-06*
