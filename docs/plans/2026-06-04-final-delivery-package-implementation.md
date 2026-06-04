# GovCMS 最终交付包实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐信创交付基线最后一项功能（SM3 摘要校验），引入 Redis 缓存和应用层限流，扩展健康检查，同步文档状态。

**Architecture:** SM3 摘要通过 `DigestOutputStream` 边写边算，复用现有 `GmCryptoService`；Redis 缓存基于 Spring Cache + Lettuce；限流基于 `Bucket4j` + Redis 分布式令牌桶；健康检查扩展 Spring Boot Actuator 自定义 `HealthIndicator`。

**Tech Stack:** Java 17, Spring Boot 3.2, Dameng DM8, Redis 6+, BouncyCastle SM3, Bucket4j 8.x, Spring Boot Actuator

---

## Task 1: 添加 Maven 依赖

**Files:**
- Modify: `pom.xml`

**Step 1: 添加 spring-boot-starter-cache**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**Step 2: 添加 spring-boot-starter-actuator**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Step 3: 添加 Bucket4j**

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

**Step 4: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add pom.xml
git commit -m "deps: add cache, actuator, bucket4j"
```

---

## Task 2: PublishArtifact 实体新增 sm3Digest + 数据库迁移

**Files:**
- Modify: `src/main/java/gov/cms/admin/entity/PublishArtifact.java`
- Create: `scripts/db-migration/001_add_sm3_digest.sql`

**Step 1: 添加字段**

```java
@Column(length = 64)
private String sm3Digest;
```

添加 getter/setter：

```java
public String getSm3Digest() { return sm3Digest; }
public void setSm3Digest(String sm3Digest) { this.sm3Digest = sm3Digest; }
```

**Step 2: 创建迁移脚本**

```sql
-- scripts/db-migration/001_add_sm3_digest.sql (DM8 / MySQL 通用)
ALTER TABLE publish_artifacts ADD COLUMN sm3_digest VARCHAR(64);
```

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/entity/PublishArtifact.java scripts/db-migration/
git commit -m "feat(sm3): add sm3_digest to PublishArtifact"
```

---

## Task 3: GmCryptoService 新增 createSm3Digest 方法

**Files:**
- Modify: `src/main/java/gov/cms/admin/security/GmCryptoService.java`
- Modify: `src/main/java/gov/cms/admin/security/BouncyCastleGmCryptoService.java`

**Step 1: 接口新增方法**

```java
java.security.MessageDigest createSm3Digest();
```

**Step 2: 实现类新增方法**

```java
@Override
public MessageDigest createSm3Digest() {
    try {
        return MessageDigest.getInstance("SM3", "BC");
    } catch (Exception e) {
        throw new RuntimeException("SM3 摘要初始化失败", e);
    }
}
```

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/security/GmCryptoService.java \
          src/main/java/gov/cms/admin/security/BouncyCastleGmCryptoService.java
git commit -m "feat(sm3): expose createSm3Digest for streaming digest"
```

---

## Task 4: PublishExecutor 边写边算 SM3

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/PublishExecutor.java`

**Step 1: 注入 GmCryptoService 和 gm.crypto.enabled**

构造函数新增：

```java
private final GmCryptoService gmCryptoService;
private final boolean gmCryptoEnabled;

public PublishExecutor(...,
                       GmCryptoService gmCryptoService,
                       @Value("${gm.crypto.enabled:true}") boolean gmCryptoEnabled) {
    // ... existing assignments ...
    this.gmCryptoService = gmCryptoService;
    this.gmCryptoEnabled = gmCryptoEnabled;
}
```

**Step 2: 修改 executeJob 中的文件写入逻辑**

找到 `Files.writeString(outputPath, ...)` 这一段，替换为：

```java
byte[] htmlBytes = Optional.ofNullable(renderResult.getRenderedHtml())
        .orElse("").getBytes(StandardCharsets.UTF_8);
Files.createDirectories(outputPath.getParent());
String sm3Hex = null;
if (gmCryptoEnabled) {
    MessageDigest md = gmCryptoService.createSm3Digest();
    try (java.security.DigestOutputStream dos =
             new java.security.DigestOutputStream(Files.newOutputStream(outputPath), md)) {
        dos.write(htmlBytes);
    }
    sm3Hex = java.util.HexFormat.of().formatHex(md.digest());
    Path sm3Path = outputPath.resolveSibling(outputPath.getFileName().toString() + ".sm3");
    Files.writeString(sm3Path, sm3Hex, StandardCharsets.UTF_8);
} else {
    Files.write(outputPath, htmlBytes);
}
```

**Step 3: artifact 设置 sm3Digest**

```java
artifact.setSm3Digest(sm3Hex);
```

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/service/PublishExecutor.java
git commit -m "feat(sm3): DigestOutputStream streaming digest and .sm3 sidecar"
```

---

## Task 5: Sm3Cli 命令行工具

**Files:**
- Create: `src/main/java/gov/cms/admin/util/Sm3Cli.java`

**Step 1: 实现工具类**

```java
package gov.cms.admin.util;

import gov.cms.admin.security.GmCryptoService;
import gov.cms.admin.security.BouncyCastleGmCryptoService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class Sm3Cli {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java gov.cms.admin.util.Sm3Cli <file>");
            System.exit(1);
        }
        Path path = Path.of(args[0]);
        if (!Files.exists(path)) {
            System.err.println("File not found: " + path);
            System.exit(2);
        }
        GmCryptoService crypto = new BouncyCastleGmCryptoService();
        byte[] data = Files.readAllBytes(path);
        byte[] digest = crypto.sm3Digest(data);
        System.out.println(HexFormat.of().formatHex(digest));
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/gov/cms/admin/util/Sm3Cli.java
git commit -m "feat(sm3): add Sm3Cli for backup script integration"
```

---

## Task 6: PublishController 校验 API

**Files:**
- Create: `src/main/java/gov/cms/admin/dto/ArtifactVerifyResponse.java`
- Modify: `src/main/java/gov/cms/admin/controller/PublishController.java`
- Modify: `src/main/java/gov/cms/admin/service/PublishService.java`

**Step 1: 创建 DTO**

```java
package gov.cms.admin.dto;

public record ArtifactVerifyResponse(
    Long artifactId,
    String status,   // VALID, INVALID, UNKNOWN
    String expected,
    String actual
) {}
```

**Step 2: Controller 新增端点**

注入 `GmCryptoService` 和 `@Value("${gm.crypto.enabled:true}")`：

```java
private final GmCryptoService gmCryptoService;
private final boolean gmCryptoEnabled;

public PublishController(PublishService publishService,
                         GmCryptoService gmCryptoService,
                         @Value("${gm.crypto.enabled:true}") boolean gmCryptoEnabled) {
    this.publishService = publishService;
    this.gmCryptoService = gmCryptoService;
    this.gmCryptoEnabled = gmCryptoEnabled;
}
```

新增 endpoint：

```java
@GetMapping("/artifacts/{id}/verify")
@PreAuthorize("hasAuthority('publish:center:view')")
public ResponseEntity<ArtifactVerifyResponse> verifyArtifact(@PathVariable Long id) {
    PublishArtifact artifact = publishService.getArtifact(id);
    if (artifact == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    String expected = artifact.getSm3Digest();
    if (expected == null || expected.isBlank()) {
        return ResponseEntity.ok(new ArtifactVerifyResponse(id, "UNKNOWN", null, null));
    }
    if (!gmCryptoEnabled) {
        return ResponseEntity.ok(new ArtifactVerifyResponse(id, "UNKNOWN", expected, null));
    }
    Path path = Paths.get(publishService.resolveArtifactPath(artifact));
    if (!Files.exists(path)) {
        return ResponseEntity.ok(new ArtifactVerifyResponse(id, "INVALID", expected, "FILE_MISSING"));
    }
    try {
        byte[] data = Files.readAllBytes(path);
        byte[] digest = gmCryptoService.sm3Digest(data);
        String actual = java.util.HexFormat.of().formatHex(digest);
        String status = expected.equalsIgnoreCase(actual) ? "VALID" : "INVALID";
        return ResponseEntity.ok(new ArtifactVerifyResponse(id, status, expected, actual));
    } catch (Exception e) {
        return ResponseEntity.ok(new ArtifactVerifyResponse(id, "INVALID", expected, e.getMessage()));
    }
}
```

**Step 3: PublishService 新增 getArtifact / resolveArtifactPath**

```java
public PublishArtifact getArtifact(Long id) {
    return publishArtifactRepository.findById(id).orElse(null);
}

public String resolveArtifactPath(PublishArtifact artifact) {
    return Paths.get(publishStoragePath, artifact.getOutputPath()).toString();
}
```

**Step 4: Commit**

```bash
git add src/main/java/gov/cms/admin/dto/ArtifactVerifyResponse.java \
          src/main/java/gov/cms/admin/controller/PublishController.java \
          src/main/java/gov/cms/admin/service/PublishService.java
git commit -m "feat(sm3): artifact verify API"
```

---

## Task 7: SM3 测试

**Files:**
- Create: `src/test/java/gov/cms/admin/service/PublishExecutorSm3Test.java`
- Create: `src/test/java/gov/cms/admin/util/Sm3CliTest.java`

**Step 1: Sm3Cli 单元测试**

```java
package gov.cms.admin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class Sm3CliTest {
    @Test
    @DisplayName("Sm3Cli produces 64-char hex digest for file")
    void sm3cli_file_producesHex() throws Exception {
        Path temp = Files.createTempFile("test", ".txt");
        Files.writeString(temp, "hello");
        // main 方法输出到 stdout，可通过 System.setOut 捕获
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(out));
        Sm3Cli.main(new String[]{temp.toString()});
        String result = out.toString().trim();
        assertThat(result).hasSize(64);
        assertThat(result).matches("[0-9a-f]+");
        Files.deleteIfExists(temp);
    }
}
```

**Step 2: PublishExecutor SM3 集成测试**

参照现有 `PublishServiceTest` 风格，mock `GmCryptoService.createSm3Digest()` 返回真实 `MessageDigest.getInstance("SM3", "BC")`，执行发布后断言 `artifact.getSm3Digest()` 非空。

**Step 3: 运行测试**

Run: `mvn test -Dtest=Sm3CliTest,PublishExecutorSm3Test`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/test/java/gov/cms/admin/service/PublishExecutorSm3Test.java \
          src/test/java/gov/cms/admin/util/Sm3CliTest.java
git commit -m "test(sm3): add Sm3 and verify tests"
```

---

## Task 8: Redis Cache 配置

**Files:**
- Modify: `src/main/java/gov/cms/admin/GovcmsAdminApplication.java`
- Modify: `src/main/resources/application-prod.yml`

**Step 1: 启用缓存注解**

```java
@EnableCaching
@SpringBootApplication
public class GovcmsAdminApplication { ... }
```

**Step 2: application-prod.yml 追加缓存配置**

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5min
      cache-null-values: false
```

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/GovcmsAdminApplication.java \
          src/main/resources/application-prod.yml
git commit -m "feat(cache): enable Spring Cache with Redis"
```

---

## Task 9: CategoryService 站点树缓存

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/CategoryService.java`

**Step 1: 新增无参数缓存方法**

```java
@Cacheable(value = "categoryTree", key = "#siteId")
@Transactional(readOnly = true)
public List<CategoryTreeNode> getTreeBySiteId(Long siteId) {
    return getCategoryTree(siteId, null, null);
}
```

**Step 2: 在写操作上清缓存**

在以下方法上添加 `@CacheEvict(value = "categoryTree", key = "#request.siteId")` 或 `#siteId`：
- `createCategory`
- `updateCategory`
- `moveCategory`
- `updateStatus`
- `deleteCategory`

对于 `deleteCategory(Long id, Long siteId)`，key = "#siteId"。
对于 `updateSort`，如果排序不影响树结构，可以不加；但为了简单，统一 evict。

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/CategoryService.java
git commit -m "feat(cache): category tree Redis cache"
```

---

## Task 10: UserService 权限缓存

**Files:**
- Modify: `src/main/java/gov/cms/admin/service/UserService.java`

**Step 1: 新增 getPermissionCodes**

```java
@Cacheable(value = "userPermissions", key = "#userId")
public Set<String> getPermissionCodes(Long userId) {
    User user = getUserById(userId);
    if (user.getRoles() == null || user.getRoles().isEmpty()) {
        return Set.of();
    }
    return user.getRoles().stream()
            .flatMap(role -> role.getPermissions() != null
                    ? role.getPermissions().stream()
                    : java.util.stream.Stream.empty())
            .map(gov.cms.admin.entity.Permission::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
}
```

**Step 2: 角色变更时清缓存**

```java
@CacheEvict(value = "userPermissions", key = "#id")
@Transactional
public User assignRoles(Long id, Set<Long> roleIds) { ... }
```

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/service/UserService.java
git commit -m "feat(cache): user permission Redis cache"
```

---

## Task 11: 应用层限流

**Files:**
- Create: `src/main/java/gov/cms/admin/config/RateLimitProperties.java`
- Create: `src/main/java/gov/cms/admin/config/RateLimitConfig.java`
- Create: `src/main/java/gov/cms/admin/security/RateLimitFilter.java`
- Modify: `src/main/resources/application-prod.yml`

**Step 1: 配置属性类**

```java
package gov.cms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
@Component
public class RateLimitProperties {
    private boolean enabled = true;
    private List<Rule> rules = List.of();

    public static class Rule {
        private String path;
        private long capacity;
        private long refill;
        private String period; // SECONDS, MINUTES
        // getters/setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public long getCapacity() { return capacity; }
        public void setCapacity(long capacity) { this.capacity = capacity; }
        public long getRefill() { return refill; }
        public void setRefill(long refill) { this.refill = refill; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
    }

    // getters/setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }
}
```

**Step 2: Bucket4j + Lettuce 配置**

```java
package gov.cms.admin.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RedisClient redisClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port);
        if (!password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }
        return RedisClient.create(builder.build());
    }

    @Bean
    public ProxyManager<String> proxyManager(RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
```

**Step 3: RateLimitFilter**

```java
package gov.cms.admin.security;

import gov.cms.admin.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ProxyManager<String> proxyManager;

    public RateLimitFilter(RateLimitProperties properties, ProxyManager<String> proxyManager) {
        this.properties = properties;
        this.proxyManager = proxyManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        RateLimitProperties.Rule matched = properties.getRules().stream()
                .filter(r -> uri.startsWith(r.getPath()))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = uri + ":" + request.getRemoteAddr();
        Duration refillPeriod = "SECONDS".equalsIgnoreCase(matched.getPeriod())
                ? Duration.ofSeconds(1)
                : Duration.ofMinutes(1);
        Bandwidth limit = Bandwidth.classic(matched.getCapacity(),
                Refill.intervally(matched.getRefill(), refillPeriod));
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit)
                .build();
        Bucket bucket = proxyManager.builder()
                .build(key, () -> configuration);
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(refillPeriod.getSeconds()));
            response.getWriter().write("Too Many Requests");
        }
    }
}
```

**Step 4: application-prod.yml 追加限流配置**

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
      - path: "/api/publish/"
        capacity: 10
        refill: 1
        period: MINUTES
```

**Step 5: Commit**

```bash
git add src/main/java/gov/cms/admin/config/RateLimitProperties.java \
          src/main/java/gov/cms/admin/config/RateLimitConfig.java \
          src/main/java/gov/cms/admin/security/RateLimitFilter.java \
          src/main/resources/application-prod.yml
git commit -m "feat(rate-limit): Bucket4j + Redis token bucket"
```

---

## Task 12: 健康检查

**Files:**
- Create: `src/main/java/gov/cms/admin/health/DataSourceHealthIndicator.java`
- Create: `src/main/java/gov/cms/admin/health/RedisHealthIndicator.java`
- Create: `src/main/java/gov/cms/admin/health/HibernateSearchHealthIndicator.java`
- Create: `src/main/java/gov/cms/admin/health/QuartzHealthIndicator.java`
- Modify: `src/main/resources/application-prod.yml`

**Step 1: DataSourceHealthIndicator**

```java
package gov.cms.admin.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class DataSourceHealthIndicator implements HealthIndicator {
    private final DataSource dataSource;
    public DataSourceHealthIndicator(DataSource dataSource) { this.dataSource = dataSource; }
    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

**Step 2: RedisHealthIndicator**

```java
package gov.cms.admin.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {
    private final RedisTemplate<String, String> redisTemplate;
    public RedisHealthIndicator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    @Override
    public Health health() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

**Step 3: HibernateSearchHealthIndicator**

```java
package gov.cms.admin.health;

import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class HibernateSearchHealthIndicator implements HealthIndicator {
    private final SearchMapping searchMapping;
    public HibernateSearchHealthIndicator(SearchMapping searchMapping) {
        this.searchMapping = searchMapping;
    }
    @Override
    public Health health() {
        try {
            searchMapping.allIndexedEntities();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

**Step 4: QuartzHealthIndicator**

```java
package gov.cms.admin.health;

import org.quartz.Scheduler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class QuartzHealthIndicator implements HealthIndicator {
    private final Scheduler scheduler;
    public QuartzHealthIndicator(Scheduler scheduler) { this.scheduler = scheduler; }
    @Override
    public Health health() {
        try {
            if (scheduler.isStarted() && !scheduler.isInStandbyMode()) {
                return Health.up().build();
            }
            return Health.down().withDetail("state", "standby or not started").build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

**Step 5: application-prod.yml 暴露端点**

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

**Step 6: Commit**

```bash
git add src/main/java/gov/cms/admin/health/ \
          src/main/resources/application-prod.yml
git commit -m "feat(health): custom indicators for DB, Redis, Search, Quartz"
```

---

## Task 13: 文档同步

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/02-current-state-matrix.md`
- Modify: `docs/05-xinchuang-gm-delivery.md`
- Modify: `docs/06-roadmap-and-acceptance.md`
- Modify: `docs/README.md`

**Step 1: CLAUDE.md**

在 Tech Stack 和 Architecture Notes 中补充：
- SM3 摘要校验（边写边算 + .sm3 旁路文件）
- Redis 缓存（categoryTree, userPermissions）
- Bucket4j 限流
- Spring Boot Actuator 健康检查

在 Known Limitations 中更新：移除已完成的项。

**Step 2: docs/02-current-state-matrix.md**

标记 Redis 缓存、限流、健康检查为“已引入”。

**Step 3: docs/05-xinchuang-gm-delivery.md**

更新当前差距：SM3 已完成。

**Step 4: docs/06-roadmap-and-acceptance.md**

M5 标记为已完成或接近完成。

**Step 5: docs/README.md**

补充 `deployment-guide.md` 链接（如果有）。

**Step 6: Commit**

```bash
git add CLAUDE.md docs/
git commit -m "docs: sync final delivery package state"
```

---

## Task 14: 全量编译与测试

**Files:**
- N/A

**Step 1: 编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 2: 运行测试**

Run: `mvn test -q`
Expected: BUILD SUCCESS

**Step 3: 最终 commit（如有遗漏）**

```bash
git add -A
git commit -m "chore: final delivery package ready" || echo "Nothing to commit"
```

---

## 执行选项

**Plan complete and saved to `docs/plans/2026-06-04-final-delivery-package-implementation.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open a new session with `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
