# GovCMS 内容生命周期技术设计

## 1. 文档信息

- 文档名称：内容生命周期技术设计
- 所属阶段：`L1 CMS 平台共享层`
- 当前状态：V1.0 初稿
- 最近更新：2026-03-11
- 关联产品文档：`docs/09-content-lifecycle-prd.md`
- 目标读者：产品经理、后端工程师、前端工程师、测试工程师、架构师

## 2. 设计目标

本文件将内容生命周期 PRD 下沉为可开发的技术方案，重点明确以下内容：

- 内容状态机与状态流转边界
- 内容主表的生命周期字段扩展建议
- 流转历史记录与审计落库建议
- 后台接口清单与发布前校验规则
- 与栏目、模板、发布中心、门户读取模型的衔接方式

本文件只覆盖内容生命周期自身的技术落地，不展开完整发布引擎和模板渲染实现；相关模块仅定义调用契约。

## 3. 与现状代码衔接

### 3.1 当前现状

当前 `L0` 基座中已经存在内容基础对象：

- `Article`：基础文章实体，包含 `title`、`content`、`summary`、`category`、`author`、`status` 等字段
- `ArticleController`、`ArticleService`：已具备内容基础 CRUD 能力
- 当前内容状态字段 `status` 默认值为 `draft`
- 当前还没有正式的审核、驳回、发布、下线、流转历史模型

### 3.2 本阶段衔接原则

- 首期优先在现有 `Article` 模型基础上扩展生命周期字段，避免一开始就大规模拆出全新的内容主聚合
- 接口风格优先沿用当前项目的 Spring MVC + JPA + `ResponseEntity` 模式
- 生命周期能力与当前内容 CRUD 解耦演进：先补状态机与流转记录，再逐步把栏目归属、模板解析、发布中心接入
- 门户读取模型只能消费 `published` 内容，这条边界必须在数据层和服务层同时体现

### 3.3 首期实施边界

首期内容生命周期只交付以下能力：

- 草稿保存与编辑
- 提交审核
- 审核通过
- 审核驳回
- 发布
- 下线
- 历史流转记录查询
- 发布前校验结果返回

首期暂不实现：

- 多级审批流
- 撤回审核
- 定时发布
- 已发布内容的并行新版本编辑流
- 流程编排引擎

## 4. 领域模型设计

### 4.1 核心实体

首期建议在现有内容域新增或扩展以下对象：

- `Article`：内容主对象，扩展生命周期字段
- `ArticleLifecycleHistory`：状态流转历史对象
- `ArticlePublishCheckResult`：发布前校验结果 DTO

### 4.2 推荐状态枚举

#### 内容状态 `ArticleStatus`

- `draft`：草稿
- `review`：审核中
- `rejected`：已驳回
- `published`：已发布
- `offline`：已下线

#### 生命周期动作 `ArticleLifecycleAction`

- `save_draft`
- `submit_review`
- `approve`
- `reject`
- `publish`
- `offline`

### 4.3 状态机规则

首期仅允许以下合法流转：

- `draft -> review`
- `review -> rejected`
- `review -> published`
- `rejected -> draft`
- `rejected -> review`
- `published -> offline`
- `offline -> review`

首期明确禁止：

- `draft -> published`
- `draft -> offline`
- `review -> draft`
- `offline -> published`

### 4.4 内容主对象字段扩展建议

建议在现有 `articles` 表和 `Article` 实体上补充以下字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| siteId | Long | 否 | 站点 ID，先预留 |
| primaryColumnId | Long | 否 | 主栏目 ID，先预留 |
| status | String(20) | 是 | 生命周期状态 |
| submitterId | Long | 否 | 送审人 ID |
| submittedAt | LocalDateTime | 否 | 送审时间 |
| reviewerId | Long | 否 | 最近审核人 ID |
| reviewedAt | LocalDateTime | 否 | 最近审核时间 |
| rejectionReason | String(1000) | 否 | 最近驳回原因 |
| publisherId | Long | 否 | 发布人 ID |
| publishedAt | LocalDateTime | 否 | 发布时间 |
| offlineOperatorId | Long | 否 | 下线操作人 ID |
| offlineAt | LocalDateTime | 否 | 下线时间 |
| offlineReason | String(1000) | 否 | 下线原因 |
| versionNo | Integer | 是 | 内容版本号 |
| lastStateChangedAt | LocalDateTime | 是 | 最近状态变更时间 |

### 4.5 历史记录对象字段建议

建议新增 `article_lifecycle_histories` 表，核心字段如下：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| articleId | Long | 是 | 内容 ID |
| fromStatus | String(20) | 否 | 变更前状态 |
| toStatus | String(20) | 是 | 变更后状态 |
| action | String(50) | 是 | 生命周期动作 |
| operatorId | Long | 否 | 操作人 ID |
| operatorName | String(100) | 否 | 操作人名称快照 |
| operatorRole | String(100) | 否 | 操作角色快照 |
| reason | String(1000) | 否 | 驳回原因、下线原因等 |
| publishJobId | Long | 否 | 关联发布任务 ID |
| createdAt | LocalDateTime | 是 | 记录时间 |

## 5. 数据库设计

### 5.1 `articles` 表扩展建议

在现有 `articles` 表基础上新增字段：

- `site_id`
- `primary_column_id`
- `submitter_id`
- `submitted_at`
- `reviewer_id`
- `reviewed_at`
- `rejection_reason`
- `publisher_id`
- `published_at`
- `offline_operator_id`
- `offline_at`
- `offline_reason`
- `version_no`
- `last_state_changed_at`

### 5.2 `article_lifecycle_histories` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| article_id | BIGINT | 非空，索引 |
| from_status | VARCHAR(20) | 可空 |
| to_status | VARCHAR(20) | 非空 |
| action | VARCHAR(50) | 非空 |
| operator_id | BIGINT | 可空 |
| operator_name | VARCHAR(100) | 可空 |
| operator_role | VARCHAR(100) | 可空 |
| reason | VARCHAR(1000) | 可空 |
| publish_job_id | BIGINT | 可空 |
| created_at | TIMESTAMP | 非空 |

### 5.3 索引建议

建议索引如下：

- 普通索引：`idx_articles_status(status)`
- 普通索引：`idx_articles_site_status(site_id, status)`
- 普通索引：`idx_articles_primary_column(primary_column_id)`
- 普通索引：`idx_articles_published_at(published_at)`
- 普通索引：`idx_article_histories_article(article_id)`
- 普通索引：`idx_article_histories_created_at(created_at)`

### 5.4 关系约束建议

- `article_lifecycle_histories.article_id` 外键指向 `articles.id`
- `site_id`、`primary_column_id` 首期可以只做逻辑校验，后续待站点与栏目完整落地后再补强数据库外键
- 为兼容 `KingbaseES`，状态字段保持简单字符串枚举存储

## 6. Java 模型设计

### 6.1 包结构建议

建议新增或扩展以下文件：

- `src/main/java/gov/cms/admin/entity/Article.java`（扩展）
- `src/main/java/gov/cms/admin/entity/ArticleLifecycleHistory.java`
- `src/main/java/gov/cms/admin/repository/ArticleRepository.java`（扩展）
- `src/main/java/gov/cms/admin/repository/ArticleLifecycleHistoryRepository.java`
- `src/main/java/gov/cms/admin/service/ArticleService.java`（扩展）
- `src/main/java/gov/cms/admin/controller/ArticleController.java`（扩展）
- `src/main/java/gov/cms/admin/dto/ArticleReviewRequest.java`
- `src/main/java/gov/cms/admin/dto/ArticleRejectRequest.java`
- `src/main/java/gov/cms/admin/dto/ArticlePublishRequest.java`
- `src/main/java/gov/cms/admin/dto/ArticleOfflineRequest.java`
- `src/main/java/gov/cms/admin/dto/ArticlePublishCheckResponse.java`

### 6.2 实体设计建议

#### `Article`

继续沿用当前项目的 JPA 风格：

- `@Entity`
- `@Table(name = "articles")`
- 使用 `Long id` 自增主键
- 使用 `@PrePersist` / `@PreUpdate` 维护时间字段
- `status`、`versionNo`、`lastStateChangedAt` 在实体层保留默认值

#### `ArticleLifecycleHistory`

建议采用单独历史表，避免把所有流转记录塞进主表字段：

- `@Entity`
- `@Table(name = "article_lifecycle_histories")`
- 每次关键状态变化插入一条新记录

### 6.3 Repository 建议

#### `ArticleRepository`

建议补充以下查询能力：

- `findByStatus(String status, Pageable pageable)`
- `findBySiteIdAndStatus(Long siteId, String status, Pageable pageable)`
- `findByIdAndStatus(Long id, String status)`
- `countByPrimaryColumnId(Long primaryColumnId)`

#### `ArticleLifecycleHistoryRepository`

建议提供以下查询能力：

- `findByArticleIdOrderByCreatedAtDesc(Long articleId)`
- `findTopByArticleIdOrderByCreatedAtDesc(Long articleId)`

### 6.4 Service 设计建议

`ArticleService` 生命周期扩展职责建议如下：

- 草稿保存与更新
- 状态机合法性校验
- 送审前校验
- 审核通过 / 驳回处理
- 发布前校验
- 发布成功后状态落库
- 下线处理
- 历史流转记录写入

建议拆分内部私有方法：

- `validateTransition(...)`
- `validateBeforeSubmitReview(...)`
- `validateBeforePublish(...)`
- `recordLifecycleHistory(...)`
- `applyPublishedState(...)`
- `applyOfflineState(...)`

## 7. API 清单

### 7.1 设计原则

- 尽量在现有 `ArticleController` 上增量扩展，减少额外控制器分裂
- 首期采用同步 REST 接口
- 返回值优先使用 `Article` 或轻量 DTO
- 错误码通过 HTTP 状态码表达：`400 / 403 / 404 / 409`

### 7.2 接口列表

#### 1）内容列表查询

- 方法：`GET /api/articles`
- 权限：`content:item:view`
- 参数：`keyword`、`status`、`siteId`、`primaryColumnId`
- 返回：分页 `Article`

#### 2）内容详情查询

- 方法：`GET /api/articles/{id}`
- 权限：`content:item:view`
- 返回：`Article`

#### 3）保存草稿

- 方法：`POST /api/articles`
- 权限：`content:item:create`
- 请求体：内容创建 DTO
- 返回：`201 Created + Article`
- 说明：默认状态为 `draft`

#### 4）更新草稿/驳回内容

- 方法：`PUT /api/articles/{id}`
- 权限：`content:item:update`
- 请求体：内容更新 DTO
- 返回：`200 OK + Article`
- 说明：仅允许 `draft`、`rejected` 状态编辑

#### 5）提交审核

- 方法：`POST /api/articles/{id}/submit-review`
- 权限：`content:item:submit-review`
- 返回：`200 OK + Article`
- 说明：执行送审前校验，通过后状态改为 `review`

#### 6）审核通过

- 方法：`POST /api/articles/{id}/approve`
- 权限：`content:item:review`
- 返回：`200 OK + Article`
- 说明：首期采用“审核通过即进入 `published` 状态”的简化模型，若后续引入发布中心异步执行，可再拆分“审核通过”和“发布完成”两步

#### 7）审核驳回

- 方法：`POST /api/articles/{id}/reject`
- 权限：`content:item:reject`
- 请求体：`ArticleRejectRequest`
- 返回：`200 OK + Article`
- 说明：驳回原因必填

#### 8）发布前校验

- 方法：`GET /api/articles/{id}/publish-check`
- 权限：`content:item:publish`
- 返回：`ArticlePublishCheckResponse`
- 说明：供前端显示“可发布 / 不可发布”和失败原因清单

#### 9）正式发布

- 方法：`POST /api/articles/{id}/publish`
- 权限：`content:item:publish`
- 请求体：`ArticlePublishRequest`
- 返回：`200 OK + Article`
- 说明：若首期采用同步发布，可在接口内完成状态更新与发布任务记录

#### 10）内容下线

- 方法：`POST /api/articles/{id}/offline`
- 权限：`content:item:offline`
- 请求体：`ArticleOfflineRequest`
- 返回：`200 OK + Article`

#### 11）查询流转历史

- 方法：`GET /api/articles/{id}/histories`
- 权限：`content:item:history:view`
- 返回：`List<ArticleLifecycleHistory>`

### 7.3 请求 DTO 建议

#### `ArticleRejectRequest`

- `reason`

#### `ArticlePublishRequest`

- `operatorComment`（可选）
- `triggerMode`（可选，预留）

#### `ArticleOfflineRequest`

- `reason`

## 8. 校验规则

### 8.1 编辑校验

- 仅 `draft`、`rejected` 状态允许编辑
- `review` 状态禁止直接编辑
- `published` 状态首期不允许直接覆盖编辑

### 8.2 送审前校验

至少校验以下内容：

- 标题非空
- 正文非空
- 已归属站点
- 已归属主栏目
- 栏目状态有效
- 可解析有效详情模板
- 关键媒体引用有效

### 8.3 审核校验

- 仅 `review` 状态允许审核通过或驳回
- 驳回原因不能为空
- 审核人建议与提交人不同；首期可先做服务层提示或硬校验

### 8.4 发布校验

- 仅通过审核且满足发布条件的内容允许发布
- 发布失败不得把内容错误标记为 `published`
- 发布成功后需写入 `publishedAt`、`publisherId`

### 8.5 下线校验

- 仅 `published` 状态允许下线
- 下线后必须写入 `offlineAt`、`offlineOperatorId`
- 若提供下线原因，则需写入历史记录

## 9. 权限编码建议

建议沿用 PRD 中的编码：

- `content:item:view`
- `content:item:create`
- `content:item:update`
- `content:item:submit-review`
- `content:item:review`
- `content:item:reject`
- `content:item:publish`
- `content:item:offline`
- `content:item:history:view`

首期若权限控制希望更简化，可临时把 `review`、`reject`、`publish` 赋给同一角色，但编码仍建议完整保留。

## 10. 前端页面结构建议

首期可复用现有内容管理页并逐步增强，不强制新开完全独立页面。

建议前端页面演进为：

- 列表区：状态筛选、关键字段筛选、操作入口
- 编辑区：基础信息、正文、栏目、媒体、预览
- 生命周期操作区：送审、驳回、发布、下线
- 历史记录区：查看流转时间线

建议前端服务文件继续沿用：

- `frontend/src/views/Content.vue`
- `frontend/src/api/articles.js`（若当前未抽离，可在本阶段建立）

## 11. 实施顺序建议

建议按以下顺序开发：

1. 扩展 `articles` 表与 `Article` 实体生命周期字段
2. 新增 `article_lifecycle_histories` 表与实体
3. 在 `ArticleService` 中落地状态机与历史记录
4. 扩展 `ArticleController` 生命周期接口
5. 增加发布前校验接口
6. 前端内容页增加状态操作区与历史记录区
7. 补服务层测试、控制器测试、UI 冒烟

## 12. 测试要点

### 12.1 服务层

- 草稿创建成功
- `draft -> review` 成功
- `review -> reject` 成功且驳回原因落库
- `review -> publish` 成功且发布时间落库
- `published -> offline` 成功
- 非法状态流转被拦截
- 发布失败时不错误写成 `published`

### 12.2 控制器层

- 无查看权限返回 `403`
- 有权限时内容列表查询成功
- 送审成功返回 `200`
- 驳回缺少原因返回 `400`
- 非法流转返回 `409`
- 历史记录查询成功

### 12.3 UI 冒烟

- 草稿保存成功
- 送审成功
- 驳回提示正确
- 发布成功后状态展示正确
- 下线成功后状态展示正确
- 历史记录可查看

## 13. 与后续模块的接口约束

- 栏目管理模块需提供栏目有效性校验能力
- 模板管理模块需提供详情模板解析与模板有效性校验能力
- 发布中心需消费内容状态变更事件，尤其是 `published` 与 `offline`
- 门户读取模型只能查询 `published` 内容，不允许读取其它状态

## 14. 验收标准

- 内容状态机合法流转可正常执行
- 非法流转可被服务层与接口层正确拦截
- 驳回、发布、下线都可留下可追溯历史记录
- 发布前校验接口能返回明确结果与失败原因
- 门户读取边界清晰，仅 `published` 内容可对外可见
- 数据模型、接口命名和权限风格与现有项目保持一致
