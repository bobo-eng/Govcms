# GovCMS 最终交付包设计

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐信创交付基线最后一项功能（SM3 摘要校验），同步文档与实际代码状态，引入 Redis 缓存和应用层限流提升生产就绪度。

**Architecture:** SM3 摘要通过 `DigestOutputStream` 边写边算；Redis 缓存基于 Spring Cache + Lettuce 复用现有连接；限流基于 `Bucket4j` + `spring-boot-starter-cache` 构建令牌桶；健康检查扩展 Spring Boot Actuator 自定义 `HealthIndicator`。

**Tech Stack:** Java 17, Spring Boot 3.2, Dameng DM8, Redis 6+, BouncyCastle SM3, Bucket4j 8.x, Spring Boot Actuator

---

## 背景

截至本设计，GovCMS 已完成以下全部功能：
- DM8 数据库迁移 + SM4 字段加密
- JWT SM2 签名 + SM4 透明加密
- Hibernate Search + Redis 搜索建议
- Quartz 异步多环境发布 + 审批流
- 审计日志 UI + 完整后台体验统一化
- 部署脚本、Nginx 配置、运维手册

TongWeb 适配因缺少测试环境推迟。

## 设计范围

### 1. 文档全面同步
- 更新 `CLAUDE.md`：移除已完成的限制项，补充 Search/Suggestion/Quartz 架构
- 更新 `docs/02-current-state-matrix.md`：信创和国密标记为已部分完成
- 更新 `docs/05-xinchuang-gm-delivery.md`：当前代码差距仅剩 SM3 摘要校验
- 更新 `docs/06-roadmap-and-acceptance.md`：M2/M3/M4 已完成，M5 进行中
- 更新 `docs/README.md`：补充 `deployment-guide.md`

### 2. SM3 摘要校验（信创基线 3.4）

#### 2.1 数据模型

`PublishArtifact` 新增字段：
```java
@Column(length = 64)
private String sm3Digest;
```

`application-prod.yml` `ddl-auto: validate`，需配套数据库迁移脚本：
```sql
-- scripts/db-migration/001_add_sm3_digest.sql (DM8)
ALTER TABLE publish_artifacts ADD COLUMN sm3_digest VARCHAR(64);
```

#### 2.2 摘要生成

`PublishExecutor` 在 `writeArtifact()` 中使用 `DigestOutputStream` 边写边算：
```java
MessageDigest md = MessageDigest.getInstance("SM3", "BC");
try (DigestOutputStream dos = new DigestOutputStream(out, md)) {
    // 写入内容到 dos
}
String digest = HexFormat.of().formatHex(md.digest());
artifact.setSm3Digest(digest);
```

降级：`gm.crypto.enabled=false` 时跳过摘要（`sm3Digest = null`）。

#### 2.3 旁路摘要文件

同时生成 `.sm3` 文件：
```
./storage/publish/2024-01-01/index.html
./storage/publish/2024-01-01/index.html.sm3  <-- 内容为 hex 摘要
```

便于离线校验。

#### 2.4 备份包摘要

`scripts/backup.sh` 在 `tar` 和 `disql BACKUP` 后追加：
```bash
# 生成 storage.tar.gz 的 SM3
java -cp "$APP_HOME/govcms-admin-*.jar" gov.cms.admin.util.Sm3Cli "$BACKUP_DIR/storage.tar.gz" > "$BACKUP_DIR/storage.tar.gz.sm3"
# db.bak 的 SM3 类似
```

需要新增 `Sm3Cli` 命令行工具类。

#### 2.5 校验 API

`PublishController`：
```java
@GetMapping("/artifacts/{id}/verify")
public ResponseEntity<ArtifactVerifyResponse> verifyArtifact(@PathVariable Long id);
```

返回状态：`VALID`（匹配）、`INVALID`（不匹配/文件篡改）、`UNKNOWN`（旧数据无摘要）。

### 3. Redis 缓存

复用现有 Redis 配置（`spring.data.redis.*`）。

#### 3.1 站点树缓存

`CategoryService`：
```java
@Cacheable(value = "categoryTree", key = "#siteId")
public List<CategoryTreeNode> getTreeBySiteId(Long siteId) { ... }

@CacheEvict(value = "categoryTree", key = "#siteId")
public void saveCategory(Category category) { ... }
```

TTL：5 分钟。

#### 3.2 用户权限缓存

`UserService`：
```java
@Cacheable(value = "userPermissions", key = "#userId")
public Set<String> getPermissionCodes(Long userId) { ... }
```

用户角色变更时 `@CacheEvict`。

#### 3.3 配置

`application-prod.yml`：
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5min
      cache-null-values: false
```

### 4. 应用层限流

基于 `Bucket4j` 4.x（无 Spring Boot Starter，手动配置）。

#### 4.1 限流规则

| 端点 | 容量 | 速率 | 说明 |
|------|------|------|------|
| POST /api/auth/login | 5 | 1/分钟 | 防爆破 |
| POST /api/portal/search | 60 | 1/秒 | 防爬虫 |
| POST /api/publish/** | 10 | 1/分钟 | 防并发发布 |

#### 4.2 实现

自定义 `RateLimitFilter`（`OncePerRequestFilter`）：
- 按 `request.getRequestURI()` + IP 维度分桶
- 使用 `ProxyManager<String, RemoteBucketState>` + Redis 分布式存储
- 超限返回 `429 Too Many Requests`

#### 4.3 配置

```yaml
app:
  rate-limit:
    enabled: true
    rules:
      - path: "/api/auth/login"
        capacity: 5
        refill: 1
        period: MINUTES
      - path: "/api/portal/search"
        capacity: 60
        refill: 1
        period: SECONDS
```

### 5. 依赖健康检查

引入 `spring-boot-starter-actuator`，配置暴露端点。

#### 5.1 自定义 HealthIndicator

- `DataSourceHealthIndicator`：执行 `SELECT 1` 验证 DM8 连接
- `RedisHealthIndicator`：执行 `redisTemplate.ping()`
- `HibernateSearchHealthIndicator`：验证 Lucene 索引目录可读写
- `QuartzHealthIndicator`：验证 Scheduler 运行状态

#### 5.2 端点暴露

`application-prod.yml`：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when_authorized
```

Nginx 配置增加 `/actuator/**` 只允许内网访问。

## 数据流

### SM3 摘要生成

```
PublishService.createPublishJob()
  -> PublishExecutor.execute()
     -> Thymeleaf 渲染 HTML
     -> DigestOutputStream -> 写入文件系统
     -> 完成 -> artifact.setSm3Digest(hex)
     -> 可选：写入 .sm3 旁路文件
  -> PublishService.save(artifact)
```

### Redis 缓存

```
UserService.getPermissionCodes(userId)
  -> @Cacheable("userPermissions", userId)
     -> Redis miss -> 查 DB -> 写入 Redis
     -> Redis hit -> 直接返回
```

### 限流

```
HTTP Request
  -> RateLimitFilter.doFilterInternal()
     -> Bucket4j 尝试消费令牌
        -> 有余量 -> 放行
        -> 不足 -> 429 + Retry-After 头
```

## 错误处理

- SM3 摘要：`GmCryptoService.sm3Digest()` 异常 -> 降级跳过（如果启用），或抛异常（如果未启用但配置要求）
- Redis 缓存：Redis 不可用 -> 自动降级到 DB（Spring Cache 默认行为）
- 限流：配置解析失败 -> 禁用限流，打 WARN 日志
- 健康检查：组件异常 -> DOWN 状态，不影响应用启动

## 测试策略

| 组件 | 测试类型 | 验证点 |
|------|---------|--------|
| SM3 摘要 | 单元测试 | roundtrip、篡改检测、空文件 |
| SM3 摘要 | 集成测试 | `PublishServiceTest` 验证 artifact 生成后 sm3Digest 非空 |
| Redis 缓存 | 单元测试 | Cacheable 注解生效、Evict 失效 |
| 限流 | 集成测试 | MockMvc 高频请求返回 429 |
| 健康检查 | 单元测试 | UP/DOWN 状态切换 |
| 文档 | 人工检查 | 所有文档与实际代码口径一致 |

## 安全考虑

- `.sm3` 旁路文件不应暴露到 Nginx 静态目录，防止摘要被篡改后伪造验证
- `/actuator/health` 不暴露敏感信息，仅返回组件 UP/DOWN
- 限流按 IP 维度，避免单用户耗尽全局配额

## 已知限制

- TongWeb 适配推迟（无测试环境）
- `backup.sh` 的 SM3 计算依赖 JVM，如果生产环境没有 JDK 只有 JRE，需要额外处理
