# GovCMS 模板管理技术设计

## 1. 文档信息

- 文档名称：模板管理技术设计
- 所属阶段：`L1 CMS 平台共享层`
- 当前状态：V1.0 初稿
- 最近更新：2026-03-11
- 关联产品文档：`docs/08-template-management-prd.md`
- 目标读者：产品经理、后端工程师、前端工程师、测试工程师、架构师

## 2. 设计目标

本文件将模板管理 PRD 下沉为可开发的技术方案，重点明确以下内容：

- 模板、模板版本、模板绑定三类核心对象
- 数据库存储结构与索引策略
- Java 实体、DTO、Repository、Service、Controller 分层建议
- 模板预览、版本回滚、绑定生效、安全限制的接口边界
- 与站点、栏目、内容、专题、发布中心的联动契约

本文件只覆盖模板管理自身的技术落地，不展开门户渲染引擎的具体实现代码；渲染引擎只保留契约和输入输出要求。

## 3. 与现状代码衔接

### 3.1 当前现状

当前 `L0` 基座中尚未存在模板域对象，现有代码以以下能力为基础：

- `Site`：站点实体，已具备站点管理能力
- `Article`：当前内容实体，仍是基础文章模型
- `MediaFile`：媒体资产基础能力已存在
- 现有控制器采用 Spring MVC + JPA + `ResponseEntity` 风格
- 现有权限模型采用 `resource:module:action` 风格的 authority 编码

### 3.2 本阶段衔接原则

- 模板管理新建模块时，命名风格与现有 `Site`、`Article`、`MediaFile`、`Permission` 模块保持一致
- 首期不引入复杂脚本模板引擎，不允许模板直接执行任意 Java、Groovy、JavaScript 服务端脚本
- 首期模板定义以结构化 JSON Schema 为主，而不是自由 HTML 源码上传模式
- 模板绑定需要兼容后续栏目管理、内容生命周期和发布中心，不建议把绑定逻辑散落到各业务表中硬编码
- 为兼容后续 `KingbaseES` 适配，避免依赖数据库专有 JSON 查询能力；结构字段首期以文本存储、应用层解析为主

### 3.3 首期实施边界

首期模板管理只交付以下能力：

- 模板列表、详情、新增、编辑、停用
- 模板版本保存、历史查看、版本回滚
- 模板与站点/栏目/专题的绑定关系管理
- 样例数据预览与真实对象预览入口
- 模板影响范围与绑定使用情况查询

首期暂不实现：

- 拖拽式低代码编辑器
- 组件市场
- 跨站点模板市场
- 前端可视化 diff 对比器
- 自定义模板脚本执行环境

## 4. 领域模型设计

### 4.1 核心实体

首期建议新增以下 3 个核心实体：

- `Template`：模板主对象，负责模板元信息、状态和当前版本指针
- `TemplateVersion`：模板版本对象，负责保存每次可回溯的结构快照
- `TemplateBinding`：模板绑定对象，负责模板与站点/栏目/专题/详情规则之间的关联关系

### 4.2 对象职责

#### `Template`

职责：

- 定义模板的基础信息
- 定义模板类型与状态
- 记录当前生效版本
- 汇总绑定数量与最近更新时间

#### `TemplateVersion`

职责：

- 保存布局快照、区块快照、SEO 快照
- 保存版本号、版本说明、创建人和创建时间
- 支持回滚和预览
- 为发布中心提供稳定版本输入

#### `TemplateBinding`

职责：

- 管理模板绑定的目标对象类型和目标对象 ID
- 限制每个对象在同一绑定槽位下仅能有一个有效模板
- 为影响范围分析提供来源数据
- 为发布中心提供模板变更影响对象集合

### 4.3 推荐枚举

#### 模板类型 `TemplateType`

- `home`：首页模板
- `column_list`：栏目页模板
- `content_detail`：内容详情模板
- `topic_page`：专题页模板
- `not_found`：404 页面模板

#### 模板状态 `TemplateStatus`

- `draft`：草稿
- `active`：启用
- `disabled`：停用

#### 绑定目标类型 `TemplateBindingTargetType`

- `site`
- `column`
- `topic`
- `content_rule`

#### 绑定槽位 `TemplateBindingSlot`

- `site_home`
- `site_detail_default`
- `site_404`
- `column_list`
- `column_detail_default`
- `topic_page`

### 4.4 推荐字段

#### 模板主表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| siteId | Long | 是 | 所属站点 ID |
| name | String(100) | 是 | 模板名称 |
| code | String(100) | 是 | 模板编码，站点内唯一 |
| type | String(30) | 是 | 模板类型 |
| status | String(20) | 是 | 模板状态 |
| description | String(1000) | 否 | 模板说明 |
| currentVersionId | Long | 否 | 当前生效版本 ID |
| latestVersionNo | Integer | 是 | 最新版本号 |
| defaultPreviewSource | String(30) | 否 | 默认预览数据源类型 |
| bindingCount | Integer | 是 | 当前绑定数量缓存值 |
| createdBy | String(100) | 是 | 创建人 |
| updatedBy | String(100) | 是 | 更新人 |
| createdAt | LocalDateTime | 是 | 创建时间 |
| updatedAt | LocalDateTime | 是 | 更新时间 |

#### 模板版本表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| templateId | Long | 是 | 所属模板 ID |
| versionNo | Integer | 是 | 版本号，从 1 递增 |
| layoutSchema | Text | 是 | 布局结构快照 |
| blockSchema | Text | 是 | 区块定义快照 |
| seoSchema | Text | 否 | SEO 配置快照 |
| styleSchema | Text | 否 | 样式配置快照 |
| changeLog | String(1000) | 否 | 版本说明 |
| createdBy | String(100) | 是 | 创建人 |
| createdAt | LocalDateTime | 是 | 创建时间 |

#### 模板绑定表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| siteId | Long | 是 | 所属站点 ID |
| templateId | Long | 是 | 模板 ID |
| templateVersionId | Long | 否 | 绑定时锁定版本，可选 |
| targetType | String(30) | 是 | 绑定目标类型 |
| targetId | Long | 是 | 绑定目标对象 ID |
| bindingSlot | String(50) | 是 | 绑定槽位 |
| status | String(20) | 是 | 绑定状态：active / inactive |
| createdBy | String(100) | 是 | 创建人 |
| updatedBy | String(100) | 是 | 更新人 |
| createdAt | LocalDateTime | 是 | 创建时间 |
| updatedAt | LocalDateTime | 是 | 更新时间 |

## 5. 数据库设计

### 5.1 主表设计

建议新增表：

- `templates`
- `template_versions`
- `template_bindings`

### 5.2 `templates` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| site_id | BIGINT | 非空，索引 |
| name | VARCHAR(100) | 非空 |
| code | VARCHAR(100) | 非空 |
| type | VARCHAR(30) | 非空 |
| status | VARCHAR(20) | 非空 |
| description | VARCHAR(1000) | 可空 |
| current_version_id | BIGINT | 可空 |
| latest_version_no | INT | 非空，默认 0 |
| default_preview_source | VARCHAR(30) | 可空 |
| binding_count | INT | 非空，默认 0 |
| created_by | VARCHAR(100) | 非空 |
| updated_by | VARCHAR(100) | 非空 |
| created_at | TIMESTAMP | 非空 |
| updated_at | TIMESTAMP | 非空 |

### 5.3 `template_versions` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| template_id | BIGINT | 非空，索引 |
| version_no | INT | 非空 |
| layout_schema | TEXT | 非空 |
| block_schema | TEXT | 非空 |
| seo_schema | TEXT | 可空 |
| style_schema | TEXT | 可空 |
| change_log | VARCHAR(1000) | 可空 |
| created_by | VARCHAR(100) | 非空 |
| created_at | TIMESTAMP | 非空 |

### 5.4 `template_bindings` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| site_id | BIGINT | 非空，索引 |
| template_id | BIGINT | 非空，索引 |
| template_version_id | BIGINT | 可空 |
| target_type | VARCHAR(30) | 非空 |
| target_id | BIGINT | 非空 |
| binding_slot | VARCHAR(50) | 非空 |
| status | VARCHAR(20) | 非空 |
| created_by | VARCHAR(100) | 非空 |
| updated_by | VARCHAR(100) | 非空 |
| created_at | TIMESTAMP | 非空 |
| updated_at | TIMESTAMP | 非空 |

### 5.5 索引与唯一约束建议

建议索引如下：

- 唯一索引：`uk_templates_site_code(site_id, code)`
- 普通索引：`idx_templates_site_type(site_id, type)`
- 普通索引：`idx_templates_site_status(site_id, status)`
- 唯一索引：`uk_template_versions_template_version(template_id, version_no)`
- 唯一索引：`uk_template_bindings_target_slot(site_id, target_type, target_id, binding_slot, status)`，其中 `status=active` 时需保证唯一
- 普通索引：`idx_template_bindings_template(template_id)`
- 普通索引：`idx_template_bindings_site_target(site_id, target_type, target_id)`

### 5.6 关系约束建议

- `templates.site_id` 外键指向 `sites.id`
- `template_versions.template_id` 外键指向 `templates.id`
- `template_bindings.template_id` 外键指向 `templates.id`
- `template_bindings.template_version_id` 可以为空；若为空，表示默认读取模板当前生效版本
- `target_id` 首期不强制数据库外键，避免过早耦合 `columns`、`topics` 等尚未完全落地的表

## 6. Java 模型设计

### 6.1 包结构建议

建议新增以下文件：

- `src/main/java/gov/cms/admin/entity/Template.java`
- `src/main/java/gov/cms/admin/entity/TemplateVersion.java`
- `src/main/java/gov/cms/admin/entity/TemplateBinding.java`
- `src/main/java/gov/cms/admin/repository/TemplateRepository.java`
- `src/main/java/gov/cms/admin/repository/TemplateVersionRepository.java`
- `src/main/java/gov/cms/admin/repository/TemplateBindingRepository.java`
- `src/main/java/gov/cms/admin/service/TemplateService.java`
- `src/main/java/gov/cms/admin/controller/TemplateController.java`
- `src/main/java/gov/cms/admin/dto/TemplatePreviewRequest.java`
- `src/main/java/gov/cms/admin/dto/TemplateBindingRequest.java`
- `src/main/java/gov/cms/admin/dto/TemplateVersionRollbackRequest.java`
- `src/main/java/gov/cms/admin/dto/TemplateImpactResponse.java`

### 6.2 实体设计建议

`Template`、`TemplateVersion`、`TemplateBinding` 建议沿用当前项目的 JPA 风格：

- `@Entity`
- `@Table(name = "templates")` / `@Table(name = "template_versions")` / `@Table(name = "template_bindings")`
- 使用 `Long id` 自增主键
- 使用 `@PrePersist` / `@PreUpdate` 维护时间字段
- 结构化 Schema 首期以 `String` 文本字段持久化，由应用层解析为 JSON

### 6.3 Repository 建议

#### `TemplateRepository`

建议提供以下查询能力：

- `findBySiteIdOrderByUpdatedAtDesc(Long siteId)`
- `findByIdAndSiteId(Long id, Long siteId)`
- `existsBySiteIdAndCodeIgnoreCase(Long siteId, String code)`
- `existsBySiteIdAndCodeIgnoreCaseAndIdNot(Long siteId, String code, Long id)`
- `findBySiteIdAndTypeAndStatus(Long siteId, String type, String status)`

#### `TemplateVersionRepository`

建议提供以下查询能力：

- `findByTemplateIdOrderByVersionNoDesc(Long templateId)`
- `findByTemplateIdAndVersionNo(Long templateId, Integer versionNo)`
- `findTopByTemplateIdOrderByVersionNoDesc(Long templateId)`

#### `TemplateBindingRepository`

建议提供以下查询能力：

- `findBySiteIdAndTargetTypeAndTargetIdAndStatus(Long siteId, String targetType, Long targetId, String status)`
- `findByTemplateIdAndStatus(Long templateId, String status)`
- `existsBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(...)`
- `countByTemplateIdAndStatus(Long templateId, String status)`

### 6.4 Service 设计建议

`TemplateService` 建议承担以下职责：

- 模板主对象创建、更新、停用
- 版本快照保存与版本号递增
- 版本回滚
- 绑定关系创建、替换、失效
- 模板结构校验与安全校验
- 预览上下文组装
- 影响范围统计

不建议把 Schema 校验、绑定冲突校验和版本逻辑分散到 Controller 中。

## 7. API 清单

### 7.1 设计原则

- 路由风格与现有 `SiteController` 保持一致
- 首期采用同步 REST 接口
- 返回值优先使用实体对象或轻量 DTO
- 错误码通过 HTTP 状态码表达：`400 / 403 / 404 / 409`

### 7.2 接口列表

#### 1）获取模板列表

- 方法：`GET /api/templates`
- 权限：`template:manage:view`
- 参数：`siteId`、`type`、`status`、`keyword`
- 返回：`List<Template>`

#### 2）获取模板详情

- 方法：`GET /api/templates/{id}`
- 权限：`template:manage:view`
- 参数：路径参数 `id`，查询参数 `siteId`
- 返回：`Template`

#### 3）新增模板

- 方法：`POST /api/templates`
- 权限：`template:manage:create`
- 请求体：模板创建 DTO
- 返回：`201 Created + Template`
- 核心校验：站点存在、编码唯一、类型合法、初始版本快照合法

#### 4）更新模板

- 方法：`PUT /api/templates/{id}`
- 权限：`template:manage:update`
- 请求体：模板更新 DTO
- 返回：`200 OK + Template`
- 说明：更新模板元信息，并可选择创建新版本快照

#### 5）停用模板

- 方法：`PUT /api/templates/{id}/status`
- 权限：`template:manage:update`
- 请求体：状态更新 DTO
- 返回：`200 OK + Template`
- 说明：被有效绑定的模板不能直接停用，需先解绑或替换

#### 6）获取模板版本列表

- 方法：`GET /api/templates/{id}/versions`
- 权限：`template:manage:view`
- 参数：`siteId`
- 返回：`List<TemplateVersion>`

#### 7）创建模板新版本

- 方法：`POST /api/templates/{id}/versions`
- 权限：`template:manage:update`
- 请求体：版本保存 DTO
- 返回：`201 Created + TemplateVersion`

#### 8）回滚模板版本

- 方法：`POST /api/templates/{id}/rollback`
- 权限：`template:manage:update`
- 请求体：`TemplateVersionRollbackRequest`
- 返回：`200 OK + Template`
- 说明：回滚后更新 `currentVersionId`，并记录新的版本操作日志

#### 9）绑定模板

- 方法：`POST /api/templates/{id}/bindings`
- 权限：`template:manage:bind`
- 请求体：`TemplateBindingRequest`
- 返回：`201 Created + TemplateBinding`
- 说明：若目标槽位已有有效绑定，需先显式替换或返回冲突

#### 10）查询模板绑定关系

- 方法：`GET /api/templates/{id}/bindings`
- 权限：`template:manage:view`
- 参数：`siteId`、`targetType`、`status`
- 返回：`List<TemplateBinding>`

#### 11）解除模板绑定

- 方法：`DELETE /api/templates/bindings/{bindingId}`
- 权限：`template:manage:bind`
- 参数：`siteId`
- 返回：`204 No Content`

#### 12）模板预览

- 方法：`POST /api/templates/{id}/preview`
- 权限：`template:manage:preview`
- 请求体：`TemplatePreviewRequest`
- 返回：预览结果 DTO
- 说明：可按样例数据、栏目对象、内容对象、专题对象预览

#### 13）获取模板影响范围

- 方法：`GET /api/templates/{id}/impact`
- 权限：`template:manage:view`
- 参数：`siteId`
- 返回：`TemplateImpactResponse`
- 说明：返回模板绑定对象数量、页面类型数量、预估重建范围摘要

### 7.3 创建接口 DTO 建议

建议 `POST /api/templates` 使用独立 DTO，而不是直接复用 Entity。

建议字段：

- `siteId`
- `name`
- `code`
- `type`
- `status`
- `description`
- `layoutSchema`
- `blockSchema`
- `seoSchema`
- `styleSchema`
- `changeLog`
- `defaultPreviewSource`

## 8. 校验规则与安全限制

### 8.1 基础校验

- `siteId` 必填
- `name` 必填，长度 `1-100`
- `code` 必填，长度 `1-100`
- `type` 必填，且必须属于预定义模板类型
- `layoutSchema` 必填
- `blockSchema` 必填
- `status` 必填，必须为 `draft / active / disabled`

### 8.2 绑定校验

- 模板类型必须和绑定槽位匹配
- 模板与绑定目标必须属于同一站点
- 同一目标对象同一槽位只能存在一个 `active` 绑定
- `disabled` 模板不可新绑定
- `draft` 模板不可进入正式发布绑定

### 8.3 Schema 安全校验

首期模板 Schema 需进行以下受控校验：

- 禁止出现脚本执行字段
- 禁止直接配置任意外部资源 URL
- 只允许白名单数据键位
- 区块类型必须属于系统允许集合
- SEO 字段长度、数量需受限，避免异常内容注入

### 8.4 回滚校验

- 回滚目标版本必须属于当前模板
- 回滚后若模板已绑定，需进入影响范围提示
- 回滚到历史版本不允许绕过状态校验

## 9. 权限编码建议

建议新增以下权限编码：

- `template:manage:view`
- `template:manage:create`
- `template:manage:update`
- `template:manage:bind`
- `template:manage:preview`
- `template:manage:delete`

首期若希望控制复杂度，可先不开放物理删除，保留编码但在实现上仅支持停用。

## 10. 前端页面结构建议

后台模板管理页面建议拆为 4 个视图区：

- 模板列表区：筛选、搜索、状态、绑定数量
- 模板编辑区：基础信息、布局 Schema、区块 Schema、SEO Schema
- 版本区：版本列表、版本说明、回滚入口
- 绑定与影响区：绑定对象、影响范围、预览入口

建议页面文件命名：

- `frontend/src/views/Templates.vue`

建议前端服务文件：

- `frontend/src/api/templates.js`

## 11. 实施顺序建议

建议按以下顺序开发：

1. 建表与基础实体
2. 模板主对象 CRUD
3. 模板版本保存与列表查询
4. 模板绑定管理
5. 模板预览接口骨架
6. 模板影响范围接口
7. 后台页面联调
8. 控制器测试、服务测试、前端冒烟

## 12. 测试要点

### 12.1 服务层

- 正常创建模板成功
- 重复 `code` 创建失败
- 非法模板类型创建失败
- 创建新版本后版本号递增正确
- 已绑定模板直接停用失败
- 绑定槽位冲突时创建绑定失败
- 非法 Schema 被拦截

### 12.2 控制器层

- 无查看权限返回 `403`
- 有权限时模板列表查询成功
- 有权限时模板创建成功
- 重复编码时返回 `409`
- 绑定冲突时返回 `409`
- 预览参数非法时返回 `400`

### 12.3 UI 冒烟

- 打开模板管理页成功
- 新增模板成功
- 保存新版本成功
- 查看历史版本成功
- 绑定栏目模板成功
- 预览模板成功
- 停用受绑定模板时提示正确

## 13. 与后续模块的接口约束

- 栏目管理模块需消费 `column_list` 与 `column_detail_default` 两类模板绑定能力
- 内容生命周期模块需消费 `content_detail` 模板解析结果，仅允许 `published` 内容进入正式渲染
- 发布中心需消费模板版本与模板绑定数据，用于计算受影响页面
- 门户渲染引擎需严格基于已发布数据与模板版本快照输出页面，不直接读取草稿模板定义

## 14. 验收标准

- 可按站点维度维护模板列表、模板详情和模板状态
- 模板编码唯一性、模板类型合法性、绑定槽位合法性校验生效
- 模板版本可保存、可查询、可回滚
- 模板绑定关系可创建、查询、解除，并能正确拦截冲突
- 模板预览接口具备受控输入与受控输出能力
- 数据模型、接口命名和权限风格与现有项目保持一致
