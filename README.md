# GovCMS

一个面向政务场景的 CMS 平台项目，目标交付为“管理后台 + CMS 建站能力 + 静态化门户站点 + 完整源码/部署交付 + 信创适配 + 国密安全能力”。

## 当前定位

截至 **2026-03-17**，当前仓库代码已从 `L0 当前基座` 明显推进到 `M2 CMS 平台共享层` 和 `M3 后台标准版` 的首批落地阶段：

- 已具备后台 MVP 原型能力，可启动、可演示、可验证
- 已补齐栏目管理与模板管理的后台闭环，可作为建站基础能力收口基座
- 已落地内容生命周期六态模型、审核工作区与发布中心 MVP
- 已具备受控 HTML 预览与正式发布/下线/回滚的最小链路
- 门户标准版、导航/专题正式对象、信创/国密正式交付仍未完成
- 后续实施统一以 `docs/` 目录下的 Markdown 主文档为准

## 当前已交付能力

- 登录认证与 JWT 鉴权
- RBAC 权限模型
- 动态菜单与按钮级权限控制
- 仪表盘
- 用户、角色、权限、菜单管理
- 内容基础管理（文章 CRUD）
- 内容生命周期管理（`draft / pending_review / rejected / approved / published / offline`）
- 审核工作区（查看、通过、驳回）
- 发布中心 MVP（发布前校验、影响范围、同步执行、下线、回滚、任务/产物/日志查看）
- 站点基础管理（列表、筛选、新增、编辑、删除、启停）
- 栏目管理（树、移动、影响范围、模板关联校验）
- 模板管理（新增/编辑、版本保存、回滚、绑定、影响范围、预览）
- 栏目模板联动（`Category.listTemplateId/detailTemplateId` 与 `TemplateBinding` 双向同步）
- 模板预览与受控渲染准备态（返回 `renderedHtml` + schema/context snapshot）
- 媒体基础管理（上传、列表、筛选、预览、删除）
- 本地文件存储能力
- `local` / `test` 双 Profile 配置

## 当前尚未实现

以下能力属于正式建设目标，但当前代码尚未完整落地：

- 独立导航对象与导航管理工作台
- 独立专题对象、专题模板与专题前端入口
- 站点管理员正式角色与站点级治理能力
- 门户首页、栏目页、详情页、专题页的标准版体系化建设
- `topic-page` 的正式渲染与发布支持
- 异步发布编排、整站标准版静态发布增强与搜索索引能力
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
- `docs/09-content-lifecycle-prd.md`
- `docs/10-publish-render-contract-prd.md`
- `docs/12-template-management-technical-design.md`
- `docs/14-publish-render-technical-design.md`
- `docs/15-publish-center-mvp-prd.md`
- `docs/16-role-system-definition.md`
- `docs/17-navigation-topic-site-admin-modeling.md`

## 技术现状

### 当前代码技术栈

- 后端：Java 17、Spring Boot 3.2、Spring Security、JWT、Spring Data JPA
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue、Axios
- 数据库：MySQL 8
- 构建：Maven、npm
- 文件存储：本地文件系统 `./storage/media` 与 `./storage/publish`

### 正式交付目标技术口径

- 数据库：KingbaseES
- 应用服务器：TongWeb
- Web 服务器：Nginx
- 运行环境：国产 OS / 政务云兼容
- 安全：国密传输、国密签名、国密数据保护

## 当前限制说明

- 当前默认数据库仍为 MySQL，本地开发默认按 `application-local.yml` 运行
- 当前发布中心为同步执行 MVP，不包含异步队列、多环境编排、灰度/蓝绿发布
- 当前正式发布仅覆盖 `home`、`column-list`、`content-detail`、`error-404` 等受控页面类型
- 当前 `topic-page` 仍未支持正式发布
- 当前认证仍为普通 JWT 签名链路，不代表正式国密方案已经落地
- 当前项目可以作为后续 CMS 平台和门户标准版建设基座，但不能直接视为正式交付版本

## 历史参考材料

- `docs/青海省委网信办门户网站运维服务项目建设方案 (2)(2).docx`
- `requirement_analysis.json`
- `system_design.json`

后续如存在口径冲突，以 `docs/` 目录下的 Markdown 主文档为准。