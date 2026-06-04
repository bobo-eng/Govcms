# GovCMS

一个面向政务场景的 CMS 平台项目，目标交付为“管理后台 + CMS 建站能力 + 静态化门户站点 + 完整源码/部署交付 + 信创适配 + 国密安全能力”。

## 当前定位

截至 **2026-06-04**，当前仓库代码已从 `L0 当前基座` 推进到 `M5 最终交付包` 阶段：

- 后台标准版核心能力完整可用，已完成 8 批后台体验统一化改版
- 内容生命周期六态模型、审核工作区、发布中心（含 Quartz 异步多环境发布与审批流）、媒体依赖校验与审计留痕已全部落地
- 门户受控渲染覆盖 `home / column-list / content-detail / topic-page / error-404`，支持静态发布与回滚
- 站内搜索基于 Hibernate Search + Lucene，支持高亮、筛选、热词 / 零结果统计与索引重建
- Redis 搜索建议引擎（ZSet 实时聚合）已上线
- 审计日志具备独立管理 UI（`/audit-logs`），覆盖发布、回滚、站点管理员分配等关键动作
- 信创交付基线中 DM8 数据库迁移、SM2 签名 JWT、SM4 字段透明加密、SM3 发布产物摘要校验已全部完成
- Redis 缓存（站点树、用户权限）、应用层限流（Bucket4j）、Spring Boot Actuator 健康检查已引入
- 后续实施统一以 `docs/` 目录下的 Markdown 主文档为准

## 当前已交付能力

- 登录认证与 JWT 鉴权（SM2 签名，非 HMAC）
- RBAC 权限模型（admin / site_admin / editor / reviewer / publisher / viewer）
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
- 发布中心（发布前校验、影响范围、同步/异步执行、Quartz 多环境调度、审批流、下线、回滚、任务/产物/日志查看）
- 搜索与搜索索引（Hibernate Search + Lucene，含高亮、筛选增强、热词 / 零结果统计、索引重建）
- Redis 搜索建议引擎（ZSet 实时聚合标题与查询词）
- 媒体基础管理（上传、列表、筛选、预览、删除）
- 媒体引用追踪与发布链路中的缺失媒体告警
- 审计日志独立 UI（`/audit-logs`），覆盖发布 / 重试 / 回滚 / 阻断 / 站点管理员分配 / 媒体删除保护
- 本地文件存储能力
- 后台体验统一化共享样式层 `frontend/src/styles/admin-refresh.css` 及 8 批页面改版落地
- 信创数据库迁移（MySQL → Dameng DM8）
- 国密 SM4 字段级透明加密（JPA `AttributeConverter`）
- 国密 SM2 JWT 签名
- 国密 SM3 发布产物摘要校验（`DigestOutputStream` + `.sm3` 旁路文件）
- Redis 缓存（站点树 `categoryTree`、用户权限 `userPermissions`）
- 应用层限流（Bucket4j + Redis 分布式令牌桶）
- Spring Boot Actuator 健康检查（DB / Redis / Hibernate Search / Quartz）
- `local` / `test` / `dm` / `prod` 多 Profile 配置

## 当前尚未实现

以下能力属于正式建设目标，但当前代码尚未完整落地：

- 独立审计中心 / 运维工作台完整 UI（当前审计日志已具备独立管理 UI `/audit-logs`，但尚未形成完整审计中心）
- 复杂搜索相关度模型与更完整的搜索运营分析能力
- 多环境编排、灰度 / 蓝绿发布
- 搜索索引独立编排与生产级搜索服务能力（当前为 Hibernate Search + Lucene，非独立搜索引擎集群）
- 信创环境完整部署落地（KingbaseES、TongWeb、国产 OS / 政务云）
- 国密传输层（TLS/SM 通道）落地

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
- `docs/deployment-guide.md`

## 文档导航（实施计划）

- `docs/plans/2026-06-02-audit-log-ui-design.md`
- `docs/plans/2026-06-02-audit-log-ui-implementation.md`
- `docs/plans/2026-06-02-gm-crypto-jwt-implementation.md`
- `docs/plans/2026-06-03-quartz-multi-env-publishing.md`
- `docs/plans/2026-06-03-hibernate-search-integration.md`
- `docs/plans/2026-06-03-redis-suggestion-engine.md`
- `docs/plans/2026-06-03-dm-sm4-delivery-design.md`
- `docs/plans/2026-06-03-dm-sm4-delivery-implementation.md`
- `docs/plans/2026-06-04-final-delivery-package-design.md`
- `docs/plans/2026-06-04-final-delivery-package-implementation.md`
- `docs/plans/2026-06-04-menu-redesign-design.md`
- `docs/plans/2026-06-04-menu-redesign-implementation.md`

## 技术现状

### 当前代码技术栈

- 后端：Java 17、Spring Boot 3.2、Spring Security、JWT（SM2 签名）、Spring Data JPA、Hibernate Search 6 + Lucene、Quartz
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue、Axios
- 数据库：MySQL 8（本地开发）、Dameng DM8（信创测试）
- 缓存与中间件：Redis（缓存 + 搜索建议 + 限流共享状态）
- 国密：BouncyCastle GM（SM2 签名/验签、SM3 摘要、SM4 加密），SM4 JPA `AttributeConverter` 字段级透明加密
- 限流：Bucket4j + Redis 分布式令牌桶
- 健康检查：Spring Boot Actuator 自定义 HealthIndicator（DB / Redis / Hibernate Search / Quartz）
- 构建：Maven、npm
- 文件存储：本地文件系统 `./storage/media` 与 `./storage/publish`
- 多 Profile：`local` / `test` / `dm` / `prod`

### 正式交付目标技术口径

- 数据库：KingbaseES
- 应用服务器：TongWeb
- Web 服务器：Nginx
- 运行环境：国产 OS / 政务云兼容
- 安全：国密传输、国密签名、国密数据保护

## 当前限制说明

- 当前默认数据库仍为 MySQL，本地开发默认按 `application-local.yml` 运行；信创生产环境目标为 KingbaseES，当前已提供 Dameng DM8 适配 profile
- 当前发布中心已具备 Quartz 异步多环境调度与审批流，但不包含灰度 / 蓝绿发布、自动回滚编排
- 当前搜索基于 Hibernate Search + Lucene，并非独立搜索引擎集群（如 Elasticsearch）
- 当前审计日志已具备独立管理 UI（`/audit-logs`），覆盖发布 / 回滚 / 重试 / 站点管理员分配等关键动作，但尚未形成完整独立审计中心
- 当前 JWT 认证链路已采用 SM2 签名，SM4 字段加密与 SM3 发布产物摘要已落地，但国密 TLS 传输层尚未完成
- 当前项目可以作为后续 CMS 平台和门户标准版建设基座，但不能直接视为正式交付版本

## 历史参考材料

- `docs/青海省委网信办门户网站运维服务项目建设方案 (2)(2).docx`
- `requirement_analysis.json`
- `system_design.json`

后续如存在口径冲突，以 `docs/` 目录下的 Markdown 主文档为准。






