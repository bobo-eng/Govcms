# GovCMS Admin

一个基于 `Spring Boot 3.2 + Vue 3` 的 GovCMS 管理后台原型项目，当前已实现登录认证、角色权限、动态菜单、按钮级权限控制、仪表盘和文章管理等核心后台能力。

## 技术栈

- 后端：Java 17、Spring Boot 3.2、Spring Security、JWT、Spring Data JPA
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue、Axios
- 数据库：MySQL 8
- 构建：Maven、npm

## 目录结构

```text
.
├─ src/                  # Spring Boot 后端
├─ frontend/             # Vue 3 前端
├─ system_design.json    # 架构设计文档
├─ requirement_analysis.json
├─ task_plan.md
└─ progress.md
```

## 配置文件

项目已拆分为两套 Spring Profile 配置：

- `src/main/resources/application.yml`：公共配置，默认 Profile 为 `local`
- `src/main/resources/application-local.yml`：本机开发环境
- `src/main/resources/application-test.yml`：线上测试环境

当前数据库配置如下：

- `local`：数据库 `govcms`，用户名 `root`，密码 `123456`
- `test`：保留项目原有测试环境配置
- 后端端口：`8080`
- 前端端口：`4869`

## 本地启动

### 1. 准备环境

- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+
- MySQL 8+

### 2. 创建数据库

先在本机 MySQL 中创建数据库：

```sql
CREATE DATABASE govcms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如需修改数据库连接，请分别编辑：

- `src/main/resources/application-local.yml`
- `src/main/resources/application-test.yml`

### 3. 启动后端

本地开发默认使用 `local` Profile，在项目根目录执行：

```bash
mvn spring-boot:run
```

如需切换到测试环境配置，可显式指定：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

也可以使用环境变量：

```bash
SPRING_PROFILES_ACTIVE=test
mvn spring-boot:run
```

后端启动成功后默认监听：

```text
http://localhost:8080
```

### 4. 启动前端

在另一个终端执行：

```bash
cd frontend
npm install
npm run dev
```

前端开发地址：

```text
http://localhost:4869
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

## 默认管理员

应用首次启动时，如果数据库中不存在 `admin` 用户，系统会自动创建默认管理员：

- 用户名：`admin`
- 密码：`admin123`
- 邮箱：`admin@govcms.local`

建议首次登录后立即修改默认密码。

## 当前已实现模块

- 登录认证与 JWT 鉴权
- 用户管理
- 角色管理
- 权限管理
- 菜单管理
- 按钮级权限控制
- 仪表盘
- 文章管理

## 待补齐模块

- 租户管理
- 审核工作流
- 站点管理
- 媒体管理
- 测试与部署脚本

## 常见问题

### 登录页显示演示账号，但无法登录

请确认后端已正常启动，并且数据库已自动初始化。默认管理员会在首次启动时自动创建；如果数据库中已经存在同名用户，则不会覆盖原有密码。

### 页面请求失败

请确认：

1. 后端运行在 `8080`
2. 前端运行在 `4869`
3. 当前使用的 Spring Profile 数据库连接配置正确
4. 浏览器请求的 `/api` 已被前端代理到后端
