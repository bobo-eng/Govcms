# GovCMS 发布渲染契约技术设计

## 1. 文档信息

- 文档名称：发布渲染契约技术设计
- 所属阶段：`L1 CMS 平台共享层`
- 当前状态：V1.0 初稿
- 最近更新：2026-03-11
- 关联产品文档：`docs/10-publish-render-contract-prd.md`
- 目标读者：产品经理、后端工程师、前端工程师、测试工程师、架构师、运维工程师

## 2. 设计目标

本文件将发布渲染契约 PRD 下沉为可开发的技术方案，重点明确以下内容：

- 发布任务、影响项、渲染上下文、发布产物的核心模型
- 整站发布与增量发布的执行链路
- 发布前校验、影响范围计算、任务执行、失败重试、回滚的接口边界
- 门户静态产物目录与结果登记方式
- 与站点、栏目、内容、模板、导航、门户读取模型的衔接契约

本文件只覆盖发布中心与渲染契约的技术落地，不展开完整静态站点渲染器实现；渲染器内部模板解释与 HTML 生成细节在后续实现阶段另行细化。

## 3. 与现状代码衔接

### 3.1 当前现状

当前 `L0` 基座中尚未存在正式的发布中心模型，现有代码基础如下：

- `Site`：已具备站点基础管理能力
- `Article`：已具备内容基础 CRUD，后续承接生命周期状态
- `MediaFile`：已具备媒体存储基础能力
- 现有后端采用 Spring MVC + JPA + `ResponseEntity` 风格
- 当前门户静态化、发布任务、回滚、产物登记尚未落地

### 3.2 本阶段衔接原则

- 首期先建立稳定的“发布任务与结果”领域模型，再逐步接入栏目、模板、内容、导航的具体变化源
- 发布中心以服务端统一调度为主，不把影响范围计算放到前端
- 渲染输入必须来自已校验业务对象与受控上下文，避免发布时临时拼装未受控数据
- 发布状态与业务状态变更保持一致，避免“业务已标发布但门户产物失败”的不一致
- 为后续 `KingbaseES`、`TongWeb`、国密产物验签保留扩展字段与流程挂点

### 3.3 首期实施边界

首期发布渲染中心只交付以下能力：

- 发布前校验
- 影响范围计算
- 发布任务创建与执行
- 整站发布与增量发布
- 发布产物登记
- 失败任务重试
- 基于稳定任务的回滚
- 发布结果查询

首期暂不实现：

- 多机分布式发布编排
- 蓝绿发布
- 灰度流量切换
- 多环境联邦同步
- 多租户跨区域发布编排

## 4. 领域模型设计

### 4.1 核心实体

首期建议新增以下核心对象：

- `PublishJob`：发布任务主对象
- `PublishImpactItem`：发布影响项对象
- `PublishArtifact`：发布产物对象
- `PublishRollbackRecord`：回滚记录对象
- `PublishCheckResult`：发布前校验结果 DTO
- `RenderContextSnapshot`：渲染上下文快照 DTO

### 4.2 对象职责

#### `PublishJob`

职责：

- 记录一次发布的站点范围、触发来源、模式、状态与执行摘要
- 统一承接整站发布、增量发布、回滚任务
- 汇总影响项数量、产物数量、执行结果、失败原因

#### `PublishImpactItem`

职责：

- 记录一次发布所影响的页面路径与动作类型
- 为执行阶段提供可枚举的重建清单
- 为前端展示影响范围摘要与数量提供依据

#### `PublishArtifact`

职责：

- 记录实际生成或删除的门户产物
- 记录产物类型、输出路径、版本、摘要信息
- 为回滚、追溯、产物验签提供依据

#### `PublishRollbackRecord`

职责：

- 记录回滚来源任务、目标稳定任务、回滚结果与执行时间
- 为审计和回溯提供记录

### 4.3 推荐枚举

#### 发布模式 `PublishMode`

- `full`：整站发布
- `incremental`：增量发布
- `rollback`：回滚执行

#### 触发对象类型 `PublishTriggerType`

- `site`
- `column`
- `content`
- `topic`
- `template`
- `navigation`

#### 任务状态 `PublishJobStatus`

- `pending`
- `running`
- `success`
- `failed`
- `rolled_back`

#### 影响动作 `PublishImpactAction`

- `create`
- `update`
- `delete`
- `rebuild`

#### 产物类型 `PublishArtifactType`

- `home_page`
- `column_page`
- `content_page`
- `topic_page`
- `error_404_page`
- `search_index`
- `manifest`

### 4.4 推荐字段

#### 发布任务主表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| siteId | Long | 是 | 站点 ID |
| mode | String(20) | 是 | 发布模式 |
| triggerType | String(30) | 是 | 触发对象类型 |
| triggerIds | String(1000) | 否 | 触发对象 ID 列表，JSON 或逗号串 |
| status | String(20) | 是 | 任务状态 |
| checkPassed | Boolean | 是 | 发布前校验是否通过 |
| impactCount | Integer | 是 | 影响项数量 |
| artifactCount | Integer | 是 | 产物数量 |
| outputRoot | String(500) | 否 | 输出根目录 |
| resultSummary | String(1000) | 否 | 执行摘要 |
| failureReason | String(2000) | 否 | 失败原因 |
| operatorId | Long | 否 | 操作人 ID |
| operatorName | String(100) | 否 | 操作人名称 |
| startedAt | LocalDateTime | 否 | 开始时间 |
| finishedAt | LocalDateTime | 否 | 结束时间 |
| createdAt | LocalDateTime | 是 | 创建时间 |
| updatedAt | LocalDateTime | 是 | 更新时间 |

#### 影响项表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| jobId | Long | 是 | 发布任务 ID |
| pageType | String(30) | 是 | 页面类型 |
| objectType | String(30) | 是 | 来源对象类型 |
| objectId | Long | 否 | 来源对象 ID |
| pagePath | String(500) | 是 | 受影响页面路径 |
| action | String(20) | 是 | 影响动作 |
| sortOrder | Integer | 是 | 执行顺序 |
| createdAt | LocalDateTime | 是 | 创建时间 |

#### 产物表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| jobId | Long | 是 | 发布任务 ID |
| artifactType | String(30) | 是 | 产物类型 |
| outputPath | String(500) | 是 | 输出路径 |
| checksum | String(200) | 否 | 摘要值 |
| versionTag | String(100) | 否 | 版本标签 |
| action | String(20) | 是 | create/update/delete |
| fileSize | Long | 否 | 文件大小 |
| createdAt | LocalDateTime | 是 | 创建时间 |

#### 回滚记录表字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| siteId | Long | 是 | 站点 ID |
| sourceJobId | Long | 是 | 来源失败或待回滚任务 |
| targetJobId | Long | 是 | 目标稳定任务 |
| rollbackJobId | Long | 是 | 生成的回滚任务 |
| operatorId | Long | 否 | 操作人 ID |
| reason | String(1000) | 否 | 回滚原因 |
| createdAt | LocalDateTime | 是 | 创建时间 |

## 5. 数据库设计

### 5.1 主表设计

建议新增表：

- `publish_jobs`
- `publish_impact_items`
- `publish_artifacts`
- `publish_rollback_records`

### 5.2 `publish_jobs` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| site_id | BIGINT | 非空，索引 |
| mode | VARCHAR(20) | 非空 |
| trigger_type | VARCHAR(30) | 非空 |
| trigger_ids | VARCHAR(1000) | 可空 |
| status | VARCHAR(20) | 非空 |
| check_passed | BOOLEAN | 非空 |
| impact_count | INT | 非空，默认 0 |
| artifact_count | INT | 非空，默认 0 |
| output_root | VARCHAR(500) | 可空 |
| result_summary | VARCHAR(1000) | 可空 |
| failure_reason | VARCHAR(2000) | 可空 |
| operator_id | BIGINT | 可空 |
| operator_name | VARCHAR(100) | 可空 |
| started_at | TIMESTAMP | 可空 |
| finished_at | TIMESTAMP | 可空 |
| created_at | TIMESTAMP | 非空 |
| updated_at | TIMESTAMP | 非空 |

### 5.3 `publish_impact_items` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| job_id | BIGINT | 非空，索引 |
| page_type | VARCHAR(30) | 非空 |
| object_type | VARCHAR(30) | 非空 |
| object_id | BIGINT | 可空 |
| page_path | VARCHAR(500) | 非空 |
| action | VARCHAR(20) | 非空 |
| sort_order | INT | 非空，默认 0 |
| created_at | TIMESTAMP | 非空 |

### 5.4 `publish_artifacts` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| job_id | BIGINT | 非空，索引 |
| artifact_type | VARCHAR(30) | 非空 |
| output_path | VARCHAR(500) | 非空 |
| checksum | VARCHAR(200) | 可空 |
| version_tag | VARCHAR(100) | 可空 |
| action | VARCHAR(20) | 非空 |
| file_size | BIGINT | 可空 |
| created_at | TIMESTAMP | 非空 |

### 5.5 `publish_rollback_records` 表设计

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| site_id | BIGINT | 非空，索引 |
| source_job_id | BIGINT | 非空 |
| target_job_id | BIGINT | 非空 |
| rollback_job_id | BIGINT | 非空 |
| operator_id | BIGINT | 可空 |
| reason | VARCHAR(1000) | 可空 |
| created_at | TIMESTAMP | 非空 |

### 5.6 索引建议

建议索引如下：

- 普通索引：`idx_publish_jobs_site_status(site_id, status)`
- 普通索引：`idx_publish_jobs_site_mode(site_id, mode)`
- 普通索引：`idx_publish_jobs_created_at(created_at)`
- 普通索引：`idx_publish_impact_items_job(job_id)`
- 普通索引：`idx_publish_impact_items_path(page_path)`
- 普通索引：`idx_publish_artifacts_job(job_id)`
- 普通索引：`idx_publish_artifacts_output(output_path)`
- 普通索引：`idx_publish_rollback_records_site(site_id)`

### 5.7 关系约束建议

- `publish_jobs.site_id` 外键指向 `sites.id`
- `publish_impact_items.job_id` 外键指向 `publish_jobs.id`
- `publish_artifacts.job_id` 外键指向 `publish_jobs.id`
- `publish_rollback_records.rollback_job_id` 外键指向 `publish_jobs.id`
- `trigger_ids` 首期用轻量字符串存储，后续若要强化查询再拆子表

## 6. Java 模型设计

### 6.1 包结构建议

建议新增以下文件：

- `src/main/java/gov/cms/admin/entity/PublishJob.java`
- `src/main/java/gov/cms/admin/entity/PublishImpactItem.java`
- `src/main/java/gov/cms/admin/entity/PublishArtifact.java`
- `src/main/java/gov/cms/admin/entity/PublishRollbackRecord.java`
- `src/main/java/gov/cms/admin/repository/PublishJobRepository.java`
- `src/main/java/gov/cms/admin/repository/PublishImpactItemRepository.java`
- `src/main/java/gov/cms/admin/repository/PublishArtifactRepository.java`
- `src/main/java/gov/cms/admin/repository/PublishRollbackRecordRepository.java`
- `src/main/java/gov/cms/admin/service/PublishService.java`
- `src/main/java/gov/cms/admin/service/PublishImpactCalculator.java`
- `src/main/java/gov/cms/admin/service/RenderContextAssembler.java`
- `src/main/java/gov/cms/admin/service/PortalRenderService.java`
- `src/main/java/gov/cms/admin/controller/PublishController.java`
- `src/main/java/gov/cms/admin/dto/PublishRequest.java`
- `src/main/java/gov/cms/admin/dto/PublishCheckResponse.java`
- `src/main/java/gov/cms/admin/dto/PublishImpactResponse.java`
- `src/main/java/gov/cms/admin/dto/PublishRollbackRequest.java`

### 6.2 实体设计建议

`PublishJob`、`PublishImpactItem`、`PublishArtifact`、`PublishRollbackRecord` 建议沿用当前项目的 JPA 风格：

- `@Entity`
- `@Table(...)`
- 使用 `Long id` 自增主键
- 使用 `@PrePersist` / `@PreUpdate` 维护时间字段
- 以扁平字段建模，避免首期把发布引擎做成过重的工作流框架

### 6.3 Repository 建议

#### `PublishJobRepository`

建议提供以下查询能力：

- `findBySiteIdOrderByCreatedAtDesc(Long siteId)`
- `findByIdAndSiteId(Long id, Long siteId)`
- `findBySiteIdAndStatus(Long siteId, String status, Pageable pageable)`
- `findTopBySiteIdAndStatusOrderByCreatedAtDesc(Long siteId, String status)`

#### `PublishImpactItemRepository`

建议提供以下查询能力：

- `findByJobIdOrderBySortOrderAscIdAsc(Long jobId)`
- `countByJobId(Long jobId)`

#### `PublishArtifactRepository`

建议提供以下查询能力：

- `findByJobIdOrderByIdAsc(Long jobId)`
- `countByJobId(Long jobId)`
- `findByOutputPath(String outputPath)`

#### `PublishRollbackRecordRepository`

建议提供以下查询能力：

- `findBySiteIdOrderByCreatedAtDesc(Long siteId)`
- `findByRollbackJobId(Long rollbackJobId)`

### 6.4 Service 设计建议

#### `PublishService`

建议承担以下职责：

- 发布前校验
- 创建发布任务
- 执行整站发布或增量发布
- 写入影响项与产物记录
- 标记任务成功或失败
- 执行重试与回滚

#### `PublishImpactCalculator`

建议独立承担以下职责：

- 根据触发对象计算受影响页面集合
- 区分 `full` 与 `incremental` 模式
- 输出有序的影响项清单

#### `RenderContextAssembler`

建议独立承担以下职责：

- 装载站点、栏目、内容、模板、导航等受控上下文
- 生成渲染上下文快照
- 过滤未发布或无效数据

#### `PortalRenderService`

建议独立承担以下职责：

- 根据影响项与渲染上下文生成静态产物
- 将产物写入目标目录
- 返回产物清单与失败信息

## 7. API 清单

### 7.1 设计原则

- 发布中心单独设置 `PublishController`
- 首期采用同步 REST 接口，必要时保留后续异步执行扩展位
- 返回值以任务对象和轻量 DTO 为主
- 错误码通过 HTTP 状态码表达：`400 / 403 / 404 / 409`

### 7.2 接口列表

#### 1）发布前校验

- 方法：`POST /api/publish/check`
- 权限：`publish:center:view`
- 请求体：`PublishRequest`
- 返回：`PublishCheckResponse`
- 说明：返回是否可发布及失败原因清单

#### 2）影响范围预计算

- 方法：`POST /api/publish/impact`
- 权限：`publish:center:view`
- 请求体：`PublishRequest`
- 返回：`PublishImpactResponse`
- 说明：返回页面类型、页面数量、路径样例和影响摘要

#### 3）创建并执行发布

- 方法：`POST /api/publish/jobs`
- 权限：`publish:center:execute`
- 请求体：`PublishRequest`
- 返回：`201 Created + PublishJob`
- 说明：首期可采用同步执行，接口返回任务最终状态

#### 4）查询发布任务列表

- 方法：`GET /api/publish/jobs`
- 权限：`publish:center:view`
- 参数：`siteId`、`status`、`mode`
- 返回：分页或列表 `PublishJob`

#### 5）查询发布任务详情

- 方法：`GET /api/publish/jobs/{id}`
- 权限：`publish:center:view`
- 参数：`siteId`
- 返回：`PublishJob`

#### 6）查询发布影响项

- 方法：`GET /api/publish/jobs/{id}/impacts`
- 权限：`publish:center:view`
- 参数：`siteId`
- 返回：`List<PublishImpactItem>`

#### 7）查询发布产物

- 方法：`GET /api/publish/jobs/{id}/artifacts`
- 权限：`publish:center:view`
- 参数：`siteId`
- 返回：`List<PublishArtifact>`

#### 8）重试失败任务

- 方法：`POST /api/publish/jobs/{id}/retry`
- 权限：`publish:center:execute`
- 参数：`siteId`
- 返回：`200 OK + PublishJob`
- 说明：仅 `failed` 任务允许重试

#### 9）回滚任务

- 方法：`POST /api/publish/jobs/{id}/rollback`
- 权限：`publish:center:rollback`
- 请求体：`PublishRollbackRequest`
- 返回：`200 OK + PublishJob`
- 说明：回滚后生成新的 `rollback` 类型任务

### 7.3 请求 DTO 建议

#### `PublishRequest`

建议字段：

- `siteId`
- `mode`
- `triggerType`
- `triggerIds`
- `operatorComment`
- `outputRoot`（可选，受控环境下可忽略前端传值）

#### `PublishRollbackRequest`

建议字段：

- `siteId`
- `targetJobId`
- `reason`

## 8. 校验规则

### 8.1 发布前校验

至少校验以下内容：

- 站点状态有效
- 栏目结构完整且无非法节点
- 内容状态满足发布要求
- 模板状态有效且绑定完整
- 媒体引用可访问
- 输出目录或目标环境配置有效

### 8.2 任务执行校验

- 同一站点首期建议同一时间只允许一个 `running` 任务
- `incremental` 模式必须提供触发对象类型和至少一个触发对象 ID
- `full` 模式不要求具体触发对象 ID，但必须有站点范围

### 8.3 重试校验

- 仅 `failed` 任务允许重试
- 重试时必须复用原始触发范围和原始模式
- 重试前可再次执行发布前校验

### 8.4 回滚校验

- 回滚目标任务必须属于同一站点
- 目标任务必须是稳定可恢复任务，通常为最近一次 `success`
- 回滚操作必须记录原因
- 回滚后必须登记新的任务、影响项和产物记录

## 9. 权限编码建议

建议新增以下权限编码：

- `publish:center:view`
- `publish:center:execute`
- `publish:center:retry`
- `publish:center:rollback`
- `publish:center:artifact:view`

首期若权限控制需要简化，可将 `execute` 与 `retry` 赋予同一角色，但编码仍建议拆开保留。

## 10. 前端页面结构建议

后台发布中心页面建议拆为 4 个主要视图区：

- 发布操作区：整站发布、增量发布、发布前校验
- 影响范围区：展示页面类型、数量、路径样例
- 任务列表区：展示最近发布任务、状态、模式、触发来源
- 结果详情区：展示影响项、产物、失败原因、回滚入口

建议页面文件命名：

- `frontend/src/views/PublishCenter.vue`

建议前端服务文件：

- `frontend/src/api/publish.js`

## 11. 实施顺序建议

建议按以下顺序开发：

1. 建表与基础实体
2. 发布任务与影响项查询接口
3. 发布前校验与影响范围接口
4. 整站发布执行骨架
5. 增量发布执行骨架
6. 产物登记与失败处理
7. 重试与回滚接口
8. 后台页面联调
9. 补服务层测试、控制器测试、UI 冒烟

## 12. 测试要点

### 12.1 服务层

- 整站发布任务创建成功
- 增量发布影响项计算正确
- 发布前校验失败时任务不执行
- 渲染失败时任务标记为 `failed`
- 重试失败任务成功
- 回滚成功并生成回滚任务记录

### 12.2 控制器层

- 无查看权限返回 `403`
- 有权限时影响范围查询成功
- 发布请求参数非法时返回 `400`
- 同站点已有运行中任务时返回 `409`
- 回滚非法目标任务时返回 `409`

### 12.3 UI 冒烟

- 打开发布中心成功
- 发布前校验结果可见
- 整站发布成功
- 单篇内容增量发布成功
- 失败任务可重试
- 回滚入口可执行

## 13. 与后续模块的接口约束

- 栏目管理模块需提供栏目有效性与路径影响计算输入
- 模板管理模块需提供模板有效性、版本快照与绑定读取能力
- 内容生命周期模块需提供 `published`、`offline` 等状态变更输入
- 门户渲染引擎只消费受控渲染上下文，不直接绕过发布中心访问草稿数据
- 国密与交付模块后续可在 `PublishArtifact` 上扩展摘要、签名、验签字段与流程

## 14. 验收标准

- 可创建并执行整站发布与增量发布任务
- 发布前校验、影响范围计算、任务执行、失败重试、回滚链路清晰可用
- 发布结果能记录影响项与产物清单
- 同步执行阶段能保证任务状态与结果一致
- 数据模型、接口命名和权限风格与现有项目保持一致
- 为后续门户静态化实现保留稳定输入输出契约
