# Findings: GovCMS 现状与差距总结

## 1. 当前代码已经具备的基础

从当前仓库可见的控制器、页面与测试来看，项目已经形成一套后台 MVP 原型基座：

### 后端控制器

- `AuthController`
- `DashboardController`
- `UserController`
- `RoleController`
- `PermissionController`
- `MenuController`
- `ArticleController`
- `SiteController`
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
- `Media.vue`

### 已有测试

- `DashboardControllerTest`
- `SiteControllerTest`
- `SiteServiceTest`
- `MediaControllerTest`
- `MediaServiceTest`

## 2. 当前代码的准确定位

当前代码可以准确归位为：

- 后台 MVP 原型基座
- 具备登录、权限、内容、站点、媒体的基本管理能力
- 具备本地启动、自测和基础演示能力

当前代码不能被描述为：

- 已完成 CMS 建站平台
- 已完成门户静态化发布系统
- 已完成信创正式交付
- 已完成国密安全落地

## 3. 当前核心差距

### 产品层差距

- 缺少栏目体系
- 缺少专题体系
- 缺少模板体系
- 缺少门户页面体系
- 缺少审核中心和发布中心

### 工程层差距

- 内容模型仍是简单文章模型
- 站点模型尚未升级为建站模型
- 媒体模型尚未升级为资产中心模型
- 缺少发布作业、发布产物、增量发布依赖计算

### 安全与交付差距

- 当前数据库仍是 MySQL，不是正式目标的 KingbaseES
- 当前部署方式仍以本地 Spring Boot 运行为主，不是 TongWeb 正式口径
- 当前认证仍是普通 JWT 签名，不是国密签名方案
- 当前没有国密加解密、签名验签、密钥管理抽象

## 4. 当前最合理的实施顺序

1. 先统一文档和目标边界
2. 再建立 CMS 平台共享领域模型
3. 之后做后台标准版
4. 再做门户标准版与静态化发布
5. 最后完成信创环境联调和正式交付验证

## 5. 当前结论

项目并不是失败或偏航，而是此前使用了“原型建设口径”和“正式建设方案口径”两套叙述方式。现在已经完成重新对齐，后续应严格以 `docs/` 下的新文档体系指导实现。
