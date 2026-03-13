# Findings: GovCMS 现状与差距总结

## 1. 当前代码已经具备的基础

从当前仓库可见的控制器、页面与测试来看，项目已经从纯后台 MVP 原型，演进为“后台基座 + 栏目 / 模板闭环”的可演示状态。

### 后端控制器

- `AuthController`
- `DashboardController`
- `UserController`
- `RoleController`
- `PermissionController`
- `MenuController`
- `ArticleController`
- `SiteController`
- `CategoryController`
- `TemplateController`
- `MediaController`

### 前端页面

- `Login.vue`
- `Dashboard.vue`
- `Users.vue`
- `Roles.vue`
- `Permissions.vue`
- `Menus.vue`
- `Content.vue`
- `Sites.vue`
- `Categories.vue`
- `Templates.vue`
- `Media.vue`

### 已有测试

- `DashboardControllerTest`
- `SiteControllerTest`
- `SiteServiceTest`
- `CategoryControllerTest`
- `CategoryServiceTest`
- `TemplateControllerTest`
- `TemplateServiceTest`
- `MediaControllerTest`
- `MediaServiceTest`

## 2. 当前代码的准确定位

当前代码可以准确归位为：

- 后台 MVP 原型基座
- 已具备栏目管理与模板管理的后台闭环
- 已具备模板版本、绑定、影响范围和“渲染准备快照”预览能力
- 可作为后续门户真实渲染与静态发布的输入侧基座

当前代码仍不能被描述为：

- 已完成 CMS 全量建站平台
- 已完成门户静态化发布系统
- 已完成信创正式交付
- 已完成国密安全落地

## 3. 当前核心差距

### 产品层差距

- 缺少专题体系与专题模板前端入口
- 缺少导航、发布中心、审核中心
- 缺少门户页面真实渲染与对外发布体系

### 工程层差距

- 模板预览已具备契约准备态，但尚未生成真实门户 HTML
- 缺少 `RenderContextAssembler` / `PortalRenderService` 一类正式渲染装配能力
- 缺少发布作业、发布产物、增量发布依赖计算
- 站点建站模型仍未扩展到导航、专题、发布流水线全链路

### 安全与交付差距

- 当前数据库仍是 MySQL，不是正式目标的 KingbaseES
- 当前部署方式仍以本地 Spring Boot 运行为主，不是 TongWeb 正式口径
- 当前认证仍是普通 JWT 签名，不是国密签名方案
- 当前没有国密加解密、签名验签、密钥管理抽象

## 4. 当前最合理的实施顺序

1. 继续补齐 CMS 平台共享对象：专题、导航、发布作业
2. 将模板快照预览升级为正式渲染上下文装配
3. 之后做门户标准版与静态化发布
4. 最后完成信创环境联调和正式交付验证

## 5. 当前结论

项目已经不再只是“内容 / 站点 / 媒体”的后台原型，模板模块与栏目模板联动已经形成可用闭环；但它仍处在后台收口与门户渲染准备阶段，后续应继续严格以 `docs/` 下的主文档体系推进实现。
