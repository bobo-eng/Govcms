# M5 交付准备设计文档：达梦数据库适配 + SM4 数据保护 + 交付 Artifacts

> **日期**: 2026-06-03
> **目标**: 完成 M5「正式交付与环境联调」阶段的数据库信创适配、国密数据保护、部署脚本与手册三大能力。

---

## 一、达梦数据库适配

### 1.1 背景

当前系统使用 MySQL 8，目标生产环境为达梦 DM8（信创要求）。达梦是 Oracle 兼容的关系型数据库，JPA/Hibernate 支持良好，但驱动和方言不在 Maven Central，需本地安装。

### 1.2 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| JDBC Driver | `DmJdbcDriver11.jar` | Java 17 环境，位于 `D:/dmdbms/drivers/jdbc/` |
| Hibernate Dialect | `DmDialect-for-hibernate6.4.jar` | Spring Boot 3.2 使用 Hibernate 6.4，完美匹配 |
| 方言类名 | `org.hibernate.dialect.DmDialect` | DM 方言包中的标准类 |

### 1.3 实施要点

**依赖管理：**
- 通过 `mvn install:install-file` 将两个 jar 安装到本地 Maven 仓库
- `pom.xml` 中以 `system` scope 或标准 `compile` scope 引用

**配置分离：**
- 新增 `application-dm.yml`，与 `application-local.yml`、`application-test.yml` 并列
- 数据库密码走环境变量 `${DM_PASSWORD:}`，不硬编码
- `application-dm.yml` 加入 `.gitignore`，防止 credentials 入仓

**JPA 兼容性关注点：**
- `ddl-auto`: 使用 `validate`（生产环境不自动建表），开发阶段可临时用 `update`
- `columnDefinition = "TEXT"`：DM 支持 `CLOB`，Hibernate 会自动映射，通常无需修改
- `AUTO_INCREMENT`：JPA `@GeneratedValue(strategy = GenerationType.IDENTITY)` 在 DM 方言下会自动适配为 `IDENTITY`
- 索引长度：MySQL 中 `varchar(500)` 加索引没问题，DM 中可能需要限制长度或调整索引类型，需测试验证
- `LocalDateTime`：DM JDBC Driver 8+ 已支持 JSR-310，无需额外转换器

**验证策略：**
- 以 `-Dspring.profiles.active=dm` 运行全量测试
- 首次启动时观察 Hibernate `validate` 输出，检查 schema 差异
- 所有 144 个现有测试必须在 DM 上通过

---

## 二、SM4 敏感数据加密

### 2.1 背景

`GmCryptoService` 已具备 `sm4Encrypt/sm4Decrypt` 能力，但尚未在业务中使用。M5 验收要求「国密数据保护可用」，需对 PII 字段实施字段级透明加密。

### 2.2 加密范围（第一轮）

| 实体 | 字段 | 理由 | 是否参与查询 |
|------|------|------|-------------|
| `User` | `email` | PII，最典型 | 否（当前无 `WHERE email = ?`） |

**排除的字段：**
- `User.username`：登录认证高频查询，加密会破坏索引和查询
- `User.password`：已由 Spring Security bcrypt 哈希，无需二次加密
- 所有参与 `LIKE`、`=`、`IN` 查询的业务字段

### 2.3 技术方案

**JPA AttributeConverter：**
- 新增 `Sm4FieldConverter implements AttributeConverter<String, String>`
- `@Convert(converter = Sm4FieldConverter.class)` 标注在实体字段上
- 自动在 `convertToDatabaseColumn` 时加密，`convertToEntityAttribute` 时解密

**密钥管理：**
- `gm.crypto.sm4.key-hex` 配置在 `application.yml`
- 通过环境变量 `GM_SM4_KEY` 注入，与 SM2 密钥管理方式一致
- 密钥长度：16 字节（128 位），hex 编码后 32 字符

**存量数据兼容（读时兼容）：**
- `convertToEntityAttribute` 时先尝试 SM4 解密
- 若解密失败（如 `BadPaddingException`），视为存量明文数据，直接返回原文
- 避免首次上线时必须写迁移脚本批量加密
- 新写入的数据自动加密，存量数据在后续修改时自然升级

**加密细节：**
- 算法：`SM4/CBC/PKCS5Padding`
- IV：固定 16 字节零向量（与 `BouncyCastleGmCryptoService` 现有实现一致）
- 密文存储：Base64 编码后存入数据库，保持 `String` 类型

---

## 三、交付 Artifacts

### 3.1 目标

补齐 M5 要求的「源码、脚本、配置、手册」，使项目可独立部署、回滚和运维。

### 3.2 交付物清单

| 文件 | 用途 |
|------|------|
| `scripts/deploy.sh` | 一键启动、停止、状态检查 |
| `scripts/backup.sh` | 数据库 + 存储目录备份 |
| `scripts/rollback.sh` | 按版本回滚 |
| `nginx.conf` | 反向代理 `/api` 到后端，门户静态文件直接由 Nginx 提供 |
| `application-prod.yml` | 生产环境配置模板（连接池、日志、CORS 关闭、会话超时） |
| `docs/deployment-guide.md` | 完整部署手册：环境准备 → 安装 DM → 部署后端 → 部署前端 → Nginx → 验证 |

### 3.3 Nginx 配置要点

```
server {
    listen 80;
    server_name localhost;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /preview/ {
        proxy_pass http://127.0.0.1:8080/preview/;
    }

    location / {
        root /var/www/govcms-portal;
        try_files $uri $uri/ /index.html;
    }
}
```

### 3.4 生产配置要点

- `spring.jpa.hibernate.ddl-auto: validate`
- `server.servlet.session.timeout: 30m`
- 日志级别：`ERROR` 为主，`gov.cms.admin` 包可开 `WARN`
- CORS：关闭开放配置，改为具体域名
- 文件上传限制：根据实际需求调整

---

## 四、实施顺序

1. **达梦数据库适配** — 先确保基座能在信创数据库上跑通
2. **SM4 数据加密** — 在 DM 基座上叠加国密数据保护
3. **交付 Artifacts** — 最后打包脚本和文档

---

## 五、验收标准

- [ ] `mvn test -Dspring.profiles.active=dm` 全量 144 测试通过
- [ ] 应用在 DM 数据库上成功启动，Hibernate `validate` 无 schema 错误
- [ ] `User.email` 在数据库中存储为 SM4 密文（Base64 格式）
- [ ] 存量明文 `email` 读取时不抛异常，正常展示
- [ ] `scripts/deploy.sh` 可一键启停应用
- [ ] `docs/deployment-guide.md` 能让新运维人员独立完成部署
