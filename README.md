# GovCMS Admin

一个基于 `Spring Boot 3.2 + Vue 3` 的 GovCMS 管理后台原型项目。

截至 **2026-03-10**，项目已经完成认证登录、RBAC、动态菜单、按钮级权限控制、用户/角色/权限/菜单管理、内容管理、站点管理、媒体管理 MVP 第一版，以及本地/测试环境配置拆分，具备一套可启动、可演示、可自测的后台 MVP 骨架。

## 当前状态

### 已实现能力
- 登录认证与 JWT 鉴权
- RBAC 权限模型（用户 / 角色 / 权限）
- 动态菜单与按钮级权限控制
- 仪表盘
- 用户管理
- 角色管理
- 权限管理
- 菜单管理
- 内容管理（文章 CRUD + 发布 / 下线）
- 站点管理（列表、筛选、新增、编辑、删除、启停）
- 媒体管理（上传、列表、筛选、打开 / 预览、删除）
- `local` / `test` 双 Profile 配置

### 下一阶段重点
- 更完整的自动化测试与验收矩阵
- 内容与媒体的进一步关联能力
- 部署脚本与交付说明
- 多租户 / 工作流等第二阶段能力

## 技术栈

- 后端：Java 17、Spring Boot 3.2、Spring Security、JWT、Spring Data JPA
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue、Axios
- 数据库：MySQL 8
- 构建：Maven、npm
- 文件存储：本地文件系统（默认 `./storage/media`）

## 目录结构

```text
.
├─ src/                        # Spring Boot 后端
├─ frontend/                   # Vue 3 前端
├─ storage/                    # 本地媒体文件目录（运行时生成，已忽略）
├─ README.md                   # 项目入口文档
├─ acceptance_checklist.md     # 手工验收清单
├─ progress.md                 # 当前进度记录
├─ task_plan.md                # 当前任务规划
├─ findings.md                 # 现状研究与能力总结
├─ system_design.json          # 架构设计文档
└─ requirement_analysis.json   # 需求分析文档
```

## 配置说明

项目已拆分为两套 Spring Profile：

- `src/main/resources/application.yml`：公共配置，默认 Profile 为 `local`
- `src/main/resources/application-local.yml`：本机开发环境
- `src/main/resources/application-test.yml`：测试环境示例配置

当前默认口径：

- 默认 Profile：`local`
- 本地数据库：`govcms`
- 后端端口：`8080`
- 前端端口：`4869`
- 媒体存储目录：`./storage/media`
- 单文件上传上限：`20MB`

## 本地启动

### 1. 环境准备
- Java 17+
- Node.js 18+
- npm 9+
- MySQL 8+

项目内已提供本地 Maven，可直接使用：

- `D:\project\GovCMS\.tools\apache-maven-3.9.12\bin\mvn.cmd`

### 2. 创建数据库

```sql
CREATE DATABASE govcms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如需修改数据库连接，请编辑：

- `src/main/resources/application-local.yml`
- `src/main/resources/application-test.yml`

### 3. 启动后端

推荐在项目根目录执行：

```powershell
.\.tools\apache-maven-3.9.12\bin\mvn.cmd -Dmaven.repo.local=.\.m2\repository spring-boot:run
```

默认使用 `local` Profile；`test` 仅作为备用示例，不作为默认启动路径。

后端启动后访问：

```text
http://127.0.0.1:8080
```

### 4. 启动前端

在另一个终端执行：

```powershell
cd frontend
npm install
npm run dev
```

前端开发地址：

```text
http://127.0.0.1:4869
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

## 默认管理员

首次启动时，如果数据库中不存在 `admin` 用户，系统会自动创建默认管理员：

- 用户名：`admin`
- 密码：`admin123`
- 邮箱：`admin@govcms.local`

注意：如果你之前在旧会话中登录过，建议**重新登录一次**，以刷新本地权限缓存。

## 验证过的命令

以下命令已在本机完成验证：

### 后端启动

```powershell
.\.tools\apache-maven-3.9.12\bin\mvn.cmd -Dmaven.repo.local=.\.m2\repository spring-boot:run
```

### 后端针对性测试（站点）

```powershell
.\.tools\apache-maven-3.9.12\bin\mvn.cmd -Dmaven.repo.local=.\.m2\repository -Dtest=SiteServiceTest,SiteControllerTest,DashboardControllerTest test
```

### 后端针对性测试（媒体）

```powershell
.\.tools\apache-maven-3.9.12\bin\mvn.cmd -Dmaven.repo.local=.\.m2\repository -Dtest=MediaServiceTest,MediaControllerTest test
```

### 前端构建

```powershell
cd frontend
npm run build
```

### 前端开发启动

```powershell
cd frontend
npm install
npm run dev
```

## 快速验收入口

手工验收与验证步骤见：

- `acceptance_checklist.md`

如果你只是想快速确认主链路，建议最少验证：

1. 后端能在 `8080` 启动
2. 前端能在 `4869` 打开
3. `admin / admin123` 可正常登录
4. 站点管理页面可完成新增、编辑、删除
5. 媒体管理页面可完成上传、筛选、打开 / 预览、删除

## 已验证测试范围

### 自动化验证
- 后端测试：`SiteServiceTest`、`SiteControllerTest`、`DashboardControllerTest`
- 后端测试：`MediaServiceTest`、`MediaControllerTest`
- 前端构建：`npm run build`
- UI 冒烟：已本地无头验证“登录 → 站点管理 → 新增 → 编辑 → 删除”链路

### 手工验证建议
- 登录与退出
- 动态菜单是否完整显示
- 用户 / 角色 / 权限 / 菜单 / 内容 / 站点 / 媒体页面能否正常打开
- 站点管理 CRUD 是否正常
- 媒体管理上传、筛选、打开 / 预览、删除是否正常

## 常见问题

### 登录页显示演示账号，但无法登录

请确认：
1. 后端已正常启动
2. 数据库已创建且连接配置正确
3. 首次启动已完成默认管理员初始化
4. 浏览器已清理旧 Token，重新登录一次

### 上传媒体文件失败

请确认：
1. 文件类型属于图片或文档白名单
2. 单文件未超过 `20MB`
3. 当前账号具备 `media:manage:upload` 权限
4. 项目根目录下 `storage/media` 可写

### 页面能打开，但操作按钮不显示

请确认当前账号是否具备对应按钮权限；本项目已启用前端按钮显隐和后端接口权限双重控制。