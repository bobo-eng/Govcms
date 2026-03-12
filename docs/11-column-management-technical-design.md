# GovCMS 栏目管理技术设计

## 1. 文档信息

- 文档名称：栏目管理技术设计
- 所属阶段：`L1 CMS 平台共享层`
- 当前状态：V1.0 初稿
- 最近更新：2026-03-11
- 关联产品文档：`docs/07-column-management-prd.md`
- 目标读者：产品经理、后端工程师、前端工程师、测试工程师、架构师

## 2. 设计目标

本文件将栏目管理 PRD 落到可开发的技术层，重点明确以下内容：

- 栏目领域模型与边界
- 数据表与索引设计
- Java 实体、DTO、枚举与服务分层建议
- 后台接口清单与请求约束
- 与站点、模板、内容、发布中心的衔接方式

本文件只覆盖栏目管理自身的技术落地，不展开模板管理、内容生命周期、发布中心的完整实现细节；相关模块仅定义联动契约。

## 3. 与现状代码衔接

### 3.1 当前现状

当前 `L0` 基座中已经存在以下对象：

- `Site`：站点实体，表名为 `sites`
- `Article`：当前内容实体，仍是基础文章模型
- `SiteController`、`ArticleController`：采用 Spring MVC + JPA 的直接控制器风格
- 权限模型：采用 `xxx:yyy:zzz` 风格的 authority 编码

### 3.2 本阶段衔接原则

- 新增栏目能力时，保持与现有 `Site`、`Article`、`Menu`、`Permission` 模块的命名风格一致
- 首期栏目管理不强制立即重构全部 `Article` 字段，但要为后续内容归属栏目预留 `primaryColumnId` 能力
- 接口风格优先沿用当前项目的 REST 风格与 `ResponseEntity` 返回方式
- 数据库设计同时兼容当前开发环境和后续 `KingbaseES` 适配要求

### 3.3 首期实施边界

首期栏目管理只交付以下核心能力：

- 栏目树查询
- 栏目新增、编辑、删除
- 栏目排序调整
- 栏目父级调整
- 栏目状态启停
- 栏目模板绑定字段
- 栏目影响范围预检查

首期暂不实现：

- 栏目版本历史对比页面
- 栏目批量导入导出
- 跨站点栏目复制
- 栏目级复杂审批流

## 4. 领域模型设计

### 4.1 核心实体

首期新增核心实体：`Column`

建议对象语义如下：

- `Column`：站点下的栏目节点，承担信息架构、内容归属、导航展示和页面生成入口四类职责
- `Column` 必须归属于某个 `Site`
- `Column` 可以有父子层级关系
- `Column` 可以绑定列表模板与默认详情模板
- `Column` 的变更需要进入发布影响计算

### 4.2 推荐字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 主键 |
| siteId | Long | 是 | 所属站点 ID |
| parentId | Long | 否 | 父栏目 ID，顶级栏目为空 |
| name | String(100) | 是 | 栏目名称 |
| code | String(100) | 是 | 栏目编码，站点内唯一 |
| type | String(30) | 是 | 栏目类型 |
| slug | String(100) | 是 | 路径段 |
| fullPath | String(255) | 是 | 完整路径 |
| level | Integer | 是 | 层级，从 1 开始 |
| sortOrder | Integer | 是 | 同级排序 |
| status | String(20) | 是 | 状态：enabled / disabled |
| navVisible | Boolean | 是 | 是否显示在导航 |
| breadcrumbVisible | Boolean | 是 | 是否参与面包屑 |
| publicVisible | Boolean | 是 | 是否允许门户访问 |
| listTemplateId | Long | 是 | 栏目页模板 ID |
| detailTemplateId | Long | 否 | 默认详情模板 ID |
| aggregationMode | String(30) | 是 | 内容聚合方式 |
| description | String(1000) | 否 | 栏目描述 |
| seoTitle | String(200) | 否 | SEO 标题 |
| seoKeywords | String(500) | 否 | SEO 关键词 |
| seoDescription | String(1000) | 否 | SEO 描述 |
| createdBy | String(100) | 是 | 创建人 |
| updatedBy | String(100) | 是 | 更新人 |
| createdAt | LocalDateTime | 是 | 创建时间 |
| updatedAt | LocalDateTime | 是 | 更新时间 |

### 4.3 推荐枚举

#### 栏目类型 `ColumnType`

- `channel`：常规栏目列表页
- `single_page`：单页栏目
- `external_link`：外链栏目

#### 栏目状态 `ColumnStatus`

- `enabled`：启用，可进入发布范围
- `disabled`：停用，不可接收新发布内容

#### 聚合方式 `ColumnAggregationMode`

- `manual`：仅展示明确归属该栏目的内容
- `inherit_children`：展示本栏目及子栏目下内容

## 5. 数据库设计

### 5.1 主表设计

建议新增表：`columns`

| 字段 | 数据类型建议 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键，自增 |
| site_id | BIGINT | 非空，索引 |
| parent_id | BIGINT | 可空，索引 |
| name | VARCHAR(100) | 非空 |
| code | VARCHAR(100) | 非空 |
| type | VARCHAR(30) | 非空 |
| slug | VARCHAR(100) | 非空 |
| full_path | VARCHAR(255) | 非空 |
| level | INT | 非空 |
| sort_order | INT | 非空，默认 0 |
| status | VARCHAR(20) | 非空 |
| nav_visible | BOOLEAN | 非空 |
| breadcrumb_visible | BOOLEAN | 非空 |
| public_visible | BOOLEAN | 非空 |
| list_template_id | BIGINT | 非空 |
| detail_template_id | BIGINT | 可空 |
| aggregation_mode | VARCHAR(30) | 非空 |
| description | VARCHAR(1000) | 可空 |
| seo_title | VARCHAR(200) | 可空 |
| seo_keywords | VARCHAR(500) | 可空 |
| seo_description | VARCHAR(1000) | 可空 |
| created_by | VARCHAR(100) | 非空 |
| updated_by | VARCHAR(100) | 非空 |
| created_at | TIMESTAMP | 非空 |
| updated_at | TIMESTAMP | 非空 |

### 5.2 唯一索引与普通索引

建议索引如下：

- 唯一索引：`uk_columns_site_code(site_id, code)`
- 唯一索引：`uk_columns_site_full_path(site_id, full_path)`
- 唯一索引：`uk_columns_parent_name(site_id, parent_id, name)`
- 普通索引：`idx_columns_site_parent(site_id, parent_id)`
- 普通索引：`idx_columns_site_status(site_id, status)`
- 普通索引：`idx_columns_list_template(list_template_id)`

### 5.3 关系约束建议

- `site_id` 外键指向 `sites.id`
- `parent_id` 自关联 `columns.id`
- `list_template_id`、`detail_template_id` 在首期可以只做逻辑校验，不强依赖数据库外键
- 为兼容后续发布中心，`full_path` 必须做唯一约束，避免生成重复门户路径

### 5.4 与内容表的过渡关系

首期栏目模块落地时，不强制同步完成内容重构；但需要预留以下演进点：

- `articles` 后续新增 `site_id`
- `articles` 后续新增 `primary_column_id`
- 当前 `category` 字段后续逐步退场，由栏目归属替代

## 6. Java 模型设计

### 6.1 包结构建议

建议新增以下文件：

- `src/main/java/gov/cms/admin/entity/Column.java`
- `src/main/java/gov/cms/admin/repository/ColumnRepository.java`
- `src/main/java/gov/cms/admin/service/ColumnService.java`
- `src/main/java/gov/cms/admin/controller/ColumnController.java`
- `src/main/java/gov/cms/admin/dto/ColumnTreeNode.java`
- `src/main/java/gov/cms/admin/dto/ColumnMoveRequest.java`
- `src/main/java/gov/cms/admin/dto/ColumnSortRequest.java`
- `src/main/java/gov/cms/admin/dto/ColumnStatusUpdateRequest.java`
- `src/main/java/gov/cms/admin/dto/ColumnImpactResponse.java`

### 6.2 实体设计建议

`Column` 实体建议沿用当前项目的 JPA 风格：

- `@Entity`
- `@Table(name = "columns")`
- 使用 `Long id` 自增主键
- 使用 `@PrePersist` / `@PreUpdate` 维护时间字段
- 先以扁平字段建模，避免首期就引入复杂聚合根与 ORM 级级联

### 6.3 Repository 建议

建议提供以下查询能力：

- `findBySiteIdOrderBySortOrderAscIdAsc(Long siteId)`
- `findByIdAndSiteId(Long id, Long siteId)`
- `existsBySiteIdAndCodeIgnoreCase(Long siteId, String code)`
- `existsBySiteIdAndFullPath(Long siteId, String fullPath)`
- `existsBySiteIdAndParentIdAndNameIgnoreCase(Long siteId, Long parentId, String name)`
- `countByParentId(Long parentId)`
- `countBySiteIdAndParentId(Long siteId, Long parentId)`

### 6.4 Service 设计建议

`ColumnService` 建议承担以下职责：

- 参数归一化与业务校验
- 路径计算与层级计算
- 栏目树构建
- 移动栏目时的循环引用校验
- 删除前依赖校验
- 影响范围预检查输出

不建议在 `Controller` 中直接写树构建或路径重算逻辑。

## 7. API 清单

### 7.1 设计原则

- 路由风格与现有 `SiteController` 保持一致
- 首期仍使用同步 REST 接口
- 返回值优先使用实体对象或轻量 DTO，不引入新的统一响应壳
- 错误码通过 HTTP 状态码表达：`400 / 403 / 404 / 409`

### 7.2 接口列表

#### 1）获取栏目树

- 方法：`GET /api/columns/tree`
- 权限：`column:manage:view`
- 参数：`siteId`、`keyword`、`status`
- 返回：`List<ColumnTreeNode>`
- 说明：后台栏目树主查询接口

#### 2）获取栏目平铺列表

- 方法：`GET /api/columns`
- 权限：`column:manage:view`
- 参数：`siteId`、`parentId`、`keyword`、`status`
- 返回：`List<Column>`
- 说明：供下拉选择、父栏目选择和局部刷新使用

#### 3）获取栏目详情

- 方法：`GET /api/columns/{id}`
- 权限：`column:manage:view`
- 参数：路径参数 `id`，查询参数 `siteId`
- 返回：`Column`

#### 4）新增栏目

- 方法：`POST /api/columns`
- 权限：`column:manage:create`
- 请求体：栏目创建 DTO
- 返回：`201 Created + Column`
- 核心校验：站点存在、父栏目存在且同站点、编码唯一、名称唯一、路径唯一、模板必填

#### 5）更新栏目

- 方法：`PUT /api/columns/{id}`
- 权限：`column:manage:update`
- 请求体：栏目更新 DTO
- 返回：`200 OK + Column`
- 核心校验：不允许跨站点修改、不允许改成循环父子关系、不允许生成重复路径

#### 6）调整排序

- 方法：`PUT /api/columns/{id}/sort`
- 权限：`column:manage:update`
- 请求体：`ColumnSortRequest`
- 返回：`200 OK + Column`
- 请求体建议字段：`siteId`、`sortOrder`

#### 7）移动栏目

- 方法：`PUT /api/columns/{id}/move`
- 权限：`column:manage:update`
- 请求体：`ColumnMoveRequest`
- 返回：`200 OK + ColumnImpactResponse`
- 请求体建议字段：`siteId`、`targetParentId`
- 说明：返回移动后栏目以及受影响范围摘要

#### 8）启停栏目

- 方法：`PUT /api/columns/{id}/status`
- 权限：`column:manage:update`
- 请求体：`ColumnStatusUpdateRequest`
- 返回：`200 OK + Column`

#### 9）删除栏目

- 方法：`DELETE /api/columns/{id}`
- 权限：`column:manage:delete`
- 参数：`siteId`
- 返回：`204 No Content`
- 核心校验：存在子栏目、存在内容归属、存在有效导航引用、存在未处理发布引用时禁止删除

#### 10）获取影响范围预检查

- 方法：`GET /api/columns/{id}/impact`
- 权限：`column:manage:view`
- 参数：`siteId`、`action`
- 返回：`ColumnImpactResponse`
- 说明：供移动、改路径、改模板前弹窗展示影响摘要

#### 11）获取父栏目候选项

- 方法：`GET /api/columns/options/parents`
- 权限：`column:manage:view`
- 参数：`siteId`、`excludeId`
- 返回：`List<Column>` 或轻量选项 DTO
- 说明：用于新建与编辑时选择父栏目

### 7.3 创建接口 DTO 建议

建议 `POST /api/columns` 使用独立 DTO，而不是直接复用 Entity。

建议字段：

- `siteId`
- `parentId`
- `name`
- `code`
- `type`
- `slug`
- `sortOrder`
- `status`
- `navVisible`
- `breadcrumbVisible`
- `publicVisible`
- `listTemplateId`
- `detailTemplateId`
- `aggregationMode`
- `description`
- `seoTitle`
- `seoKeywords`
- `seoDescription`

## 8. 校验规则

### 8.1 基础校验

- `siteId` 必填
- `name` 必填，长度 `1-100`
- `code` 必填，长度 `1-100`，建议只允许字母、数字、中划线、下划线
- `slug` 必填，长度 `1-100`，只允许字母、数字、中划线、下划线
- `type` 必填
- `listTemplateId` 必填
- `sortOrder` 非空，默认 `0`

### 8.2 树结构校验

- 父栏目不存在时禁止保存
- 父栏目与当前栏目必须属于同一站点
- 当前栏目不能移动到自己的子树下
- 层级超过系统允许值时禁止保存；首期建议限制为不超过 `4`

### 8.3 删除校验

- 存在子栏目时返回 `409 Conflict`
- 存在内容归属时返回 `409 Conflict`
- 存在有效导航引用时返回 `409 Conflict`
- 存在未完成的发布影响处理时返回 `409 Conflict`

## 9. 权限编码建议

建议新增以下权限编码：

- `column:manage:view`
- `column:manage:create`
- `column:manage:update`
- `column:manage:delete`

如果后续需要更细控制，可扩展：

- `column:manage:move`
- `column:manage:bind-template`
- `column:manage:impact:view`

首期为减少权限复杂度，建议先只启用四个基础权限编码。

## 10. 前端页面结构建议

后台栏目管理页面建议采用“左树右表单/详情”的双栏布局：

- 左侧：站点切换 + 栏目树
- 右侧：栏目详情、基础信息、模板绑定、SEO、导航配置
- 页面操作：新增、编辑、移动、排序、启停、删除、影响范围查看

建议页面文件命名：

- `frontend/src/views/Columns.vue`

建议前端服务文件：

- `frontend/src/api/columns.js`

## 11. 实施顺序建议

建议按以下顺序开发：

1. 建表与基础实体
2. 查询接口：详情、列表、栏目树
3. 新增/编辑接口
4. 移动/排序/状态调整接口
5. 删除前依赖校验与影响范围接口
6. 后台页面联调
7. 控制器测试、服务测试、前端冒烟

## 12. 测试要点

### 12.1 服务层

- 正常创建顶级栏目
- 正常创建子栏目
- 重复 `code` 创建失败
- 重复 `fullPath` 创建失败
- 移动到子树下失败
- 有子栏目时删除失败

### 12.2 控制器层

- 无查看权限返回 `403`
- 有权限时栏目树查询成功
- 有权限时新增栏目成功
- 重复编码时返回 `409`
- 删除受阻时返回 `409`

### 12.3 UI 冒烟

- 打开栏目页成功
- 切换站点成功
- 新增栏目成功
- 编辑栏目成功
- 移动栏目成功
- 删除受阻提示正确

## 13. 与后续模块的接口约束

- 模板管理模块需提供模板有效性校验能力
- 内容生命周期模块需基于栏目 ID 管理主栏目归属
- 发布中心需消费栏目变更影响摘要
- 门户读取模型需只读取已启用且允许公开访问的栏目

## 14. 验收标准

- 可按站点维度维护稳定栏目树
- 栏目编码、名称、完整路径的唯一性校验生效
- 移动栏目时可阻止循环引用和跨站点移动
- 删除栏目时可正确拦截子栏目和内容依赖
- 后台可正常完成栏目新增、编辑、移动、启停、删除受控操作
- 数据模型、接口命名和权限风格与现有项目保持一致
