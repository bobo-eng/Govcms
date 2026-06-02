# GovCMS

一个面向政务场景的 CMS 平台项目，目标交付为“管理后台 + CMS 建站能力 + 静态化门户站点 + 完整源码/部署交付 + 信创适配 + 国密安全能力”。

## 当前定位

截至 **2026-04-01**，当前仓库代码已从 `L0 当前基座` 明显推进到 `M2 CMS 平台共享层`、`M3 后台标准版` 和 `M4 门户标准版` 的增强可用阶段：

- 已具备后台标准版核心能力，可启动、可演示、可验证，并已完成 8 批后台体验统一化改版
- 已补齐栏目、模板、导航、专题、站点管理员与发布中心的后台闭环，后台高频页已基本形成统一体验体系
- 已落地内容生命周期六态模型、审核工作区、发布中心增强、媒体依赖校验与审计留痕
- 已具备首页 / 栏目页 / 内容详情页 / 专题页的受控门户渲染与静态发布基础
- 已具备站内搜索、数据库索引、搜索高亮、热词/零结果统计与索引运维入口的增强闭环
- 异步发布编排、独立审计中心、信创/国密正式交付仍未完成
- 后续实施统一以 `docs/` 目录下的 Markdown 主文档为准

## 当前已交付能力

- 登录认证与 JWT 鉴权
- RBAC 权限模型
- 动态菜单与按钮级权限控制
- 仪表盘
- 用户、角色、权限、菜单管理
- 站点管理员正式角色与单站点治理绑定
- 内容基础管理（文章 CRUD）
- 内容生命周期管理（`draft / pending_review / rejected / approved / published / offline`）
- 审核工作区（查看、通过、驳回）
- 站点基础管理与站点管理员单站点视角
- 栏目管理（树、移动、排序、影响范围、模板关联校验）
- 模板管理（新增/编辑、版本保存、回滚、绑定、影响范围、预览）
- 导航管理（独立对象、树结构、排序、目标配置、发布影响计算）
- 专题管理（独立对象、模板绑定、手工编排、基础规则聚合）
- 门户受控页面渲染（`home / column-list / content-detail / topic-page / error-404`）
- 发布中心增强（发布前校验、影响范围、同步执行、下线、回滚、任务/产物/日志查看）
- 搜索与搜索索引增强闭环（内容 / 专题 / 栏目，含高亮、筛选增强、热词 / 零结果统计、索引重建）
- 媒体基础管理（上传、列表、筛选、预览、删除）
- 媒体引用追踪与发布链路中的缺失媒体告警
- 审计日志首批能力（发布 / 重试 / 回滚 / 阻断 / 站点管理员分配 / 媒体删除保护）
- 本地文件存储能力
- 后台体验统一化共享样式层 `frontend/src/styles/admin-refresh.css` 及 8 批页面改版落地
- `local` / `test` 双 Profile 配置

## 当前尚未实现

以下能力属于正式建设目标，但当前代码尚未完整落地：

- 异步发布编排与生产级发布调度
- 独立审计中心 / 运维工作台完整 UI
- 后台体验统一化已完成，现进入维护态；仅剩 `Templates` / `PublishCenter` 局部样式可按零散维护项继续优化
- 搜索建议、复杂相关度模型与更完整的搜索运营分析能力
- 多环境编排、灰度 / 蓝绿发布
- 搜索索引独立编排与生产级搜索服务能力
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
- `docs/18-admin-ux-visual-and-interaction-guidelines.md`
- `docs/19-admin-ux-key-page-guidance.md`
- `docs/20-admin-ux-refresh-priority.md`
- `docs/21-admin-ux-residual-checklist.md`

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
- 当前发布中心仍为同步执行实现，不包含异步队列、多环境编排、灰度/蓝绿发布
- 当前搜索索引采用数据库表实现，不是独立搜索引擎方案
- 当前审计能力已覆盖发布关键动作，但尚未形成独立审计中心 UI
- 当前认证仍为普通 JWT 签名链路，不代表正式国密方案已经落地
- 当前项目可以作为后续 CMS 平台和门户标准版建设基座，但不能直接视为正式交付版本

## 历史参考材料

- `docs/青海省委网信办门户网站运维服务项目建设方案 (2)(2).docx`
- `requirement_analysis.json`
- `system_design.json`

后续如存在口径冲突，以 `docs/` 目录下的 Markdown 主文档为准。






