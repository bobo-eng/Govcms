# GovCMS 文档中心

## 文档角色

本目录是 GovCMS 项目的唯一事实来源，用于统一项目定位、分层口径、当前状态、目标范围、实施路线和交付标准。

当前统一口径如下：

- 当前仓库代码处于 `L0 当前基座`，已经具备后台 MVP 原型能力。
- 首期正式建设目标是 `L1 CMS 平台共享层`、`L2 后台标准版`、`L3 门户标准版`。
- 正式交付要求包含完整源码、数据库脚本、部署脚本、配置说明、运维文档、验收文档。
- 信创适配和国密能力不是后置专题，而是首期正式交付基线。

## 文档清单

- `docs/01-project-positioning-and-layering.md`
  - 项目定位、分层定义、状态标签、统一描述口径。
- `docs/02-current-state-matrix.md`
  - 现有代码能力与目标方案的对照矩阵。
- `docs/03-backoffice-prd.md`
  - 后台标准版产品范围、角色、模块、流程和接口分组。
- `docs/04-portal-prd.md`
  - 门户标准版范围、静态化运行模式、发布链路和页面能力。
- `docs/05-xinchuang-gm-delivery.md`
  - 信创适配、国密要求、交付物、部署方案、验收基线。
- `docs/06-roadmap-and-acceptance.md`
  - 实施路线图、阶段目标、阶段验收与总体验收。
- `docs/07-column-management-prd.md`
  - L1 第一份详细 PRD，聚焦栏目管理的对象、流程、规则、权限与验收。
- `docs/08-template-management-prd.md`
  - L1 第二份详细 PRD，聚焦模板管理的类型、绑定、预览、版本、发布契约与验收。
- `docs/09-content-lifecycle-prd.md`
  - L1 第三份详细 PRD，聚焦内容状态流转、审核、发布、下线、审计与验收。
- `docs/10-publish-render-contract-prd.md`
  - L1 第四份详细 PRD，聚焦发布输入、影响范围、渲染上下文、产物、重试与回滚契约。

- `docs/11-column-management-technical-design.md`
  - L1 第一份技术落地文档，聚焦栏目管理的数据模型、Java 分层、接口清单与校验规则。

- `docs/12-template-management-technical-design.md`
  - L1 第二份技术落地文档，聚焦模板管理的模型、版本、绑定、预览与安全校验。

- `docs/13-content-lifecycle-technical-design.md`
  - L1 第三份技术落地文档，聚焦内容状态机、流转历史、发布前校验与生命周期接口。

- `docs/14-publish-render-technical-design.md`
  - L1 第四份技术落地文档，聚焦发布任务、影响项、渲染上下文、产物、重试与回滚。

## 历史参考输入

以下文件保留为历史输入材料和方案来源，不再作为当前项目主口径：

- `docs/青海省委网信办门户网站运维服务项目建设方案 (2)(2).docx`
- `requirement_analysis.json`
- `system_design.json`

后续若这些历史材料与本目录内容冲突，以本目录内容为准。


