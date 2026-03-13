# GovCMS

一个面向政务场景的 CMS 平台项目，目标交付为“管理后台 + CMS 建站能力 + 静态化门户站点 + 完整源码/部署交付 + 信创适配 + 国密安全能力”。

## 当前定位

截至 **2026-03-12**，当前仓库代码仍处于 `L0 当前基座` 向 `M2 CMS 平台共享层` 过渡阶段：

- 已具备后台 MVP 原型能力，可启动、可演示、可验证
- 已补齐栏目管理与模板管理的后台闭环，可作为后续建站能力收口基座
- 门户真实渲染、静态发布体系、信创/国密正式交付仍未完成
- 后续实施统一以 `docs/` 目录下的新文档体系为准

## 当前已交付能力

- 登录认证与 JWT 鉴权
- RBAC 权限模型
- 动态菜单与按钮级权限控制
- 仪表盘
- 用户、角色、权限、菜单管理
- 内容基础管理（文章 CRUD、发布、下线）
- 站点基础管理（列表、筛选、新增、编辑、删除、启停）
- 栏目管理（树、移动、影响范围、模板关联校验）
- 模板管理（模板新增/编辑、版本保存、回滚、绑定、影响范围、预览快照）
- 栏目模板联动（`Category.listTemplateId/detailTemplateId` 与 `TemplateBinding` 双向同步）
- 模板预览渲染契约准备态（受控 schema + context snapshot，不生成真实 HTML 产物）
- 媒体基础管理（上传、列表、筛选、预览、删除）
- 本地文件存储能力
- `local` / `test` 双 Profile 配置

## 当前尚未实现

以下能力属于正式建设目标，但当前代码尚未落地：

- 专题管理与专题模板前端入口
- 审核中心与完整发布中心
- 门户首页、栏目页、详情页、专题页的真实门户渲染
- 整站静态发布与增量更新
- 信创环境部署落地
- 国密传输、签名和敏感数据保护落地

## 文档导航

- `docs/README.md`
- `docs/01-project-positioning-and-layering.md`
- `docs/02-current-state-matrix.md`
- `docs/03-backoffice-prd.md`
- `docs/04-portal-prd.md`
- `docs/05-xinchuang-gm-delivery.md`
- `docs/06-roadmap-and-acceptance.md`
- `docs/08-template-management-prd.md`
- `docs/10-publish-render-contract-prd.md`
- `docs/12-template-management-technical-design.md`
- `docs/14-publish-render-technical-design.md`

## 技术现状

### 当前代码技术栈

- 后端：Java 17、Spring Boot 3.2、Spring Security、JWT、Spring Data JPA
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue、Axios
- 数据库：MySQL 8
- 构建：Maven、npm
- 文件存储：本地文件系统 `./storage/media`

### 正式交付目标技术口径

- 数据库：KingbaseES
- 应用服务器：TongWeb
- Web 服务器：Nginx
- 运行环境：国产 OS / 政务云兼容
- 安全：国密传输、国密签名、国密数据保护

## 目录结构

```text
.
├─ src/                        # Spring Boot 后端
├─ frontend/                   # Vue 3 前端
├─ docs/                       # 当前项目主文档体系
├─ storage/                    # 本地媒体目录（运行时生成）
├─ README.md                   # 项目入口说明
├─ progress.md                 # 当前阶段进度
├─ task_plan.md                # 当前实施计划
├─ findings.md                 # 现状与差距总结
├─ acceptance_checklist.md     # 当前阶段验收清单
├─ requirement_analysis.json   # 历史输入材料
└─ system_design.json          # 历史输入材料
```

## 当前本地启动方式

### 1. 环境准备

- Java 17+
- Node.js 18+
- npm 9+
- MySQL 8+

### 2. 创建数据库

```sql
CREATE DATABASE govcms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 启动后端

```powershell
.\.tools\apache-maven-3.9.12\bin\mvn.cmd -Dmaven.repo.local=.\.m2\repository spring-boot:run
```

默认访问地址：`http://127.0.0.1:8080`

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

默认访问地址：`http://127.0.0.1:4869`

## 当前限制说明

- 当前默认数据库仍为 MySQL，本地开发默认按 `application-local.yml` 运行
- 当前模板预览返回的是“渲染准备快照”，不是门户 HTML 产物
- 当前认证仍为普通 JWT 签名链路，不代表正式国密方案已经落地
- 当前项目可以作为后续 CMS 平台和门户标准版建设基座，但不能直接视为正式交付版本

## 历史参考材料

- `docs/青海省委网信办门户网站运维服务项目建设方案 (2)(2).docx`
- `requirement_analysis.json`
- `system_design.json`

后续如存在口径冲突，以 `docs/` 目录下的 Markdown 主文档为准。
