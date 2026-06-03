# DM 数据库适配 + SM4 数据保护 + 交付 Artifacts Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将数据库从 MySQL 迁移到达梦 DM8，对 User PII 字段实施 SM4 透明加密，补齐生产部署脚本与手册，完成 M5 交付准备。

**Architecture:** 本地安装 DM JDBC Driver 和 Hibernate 方言包，新增 `application-dm.yml` 配置；通过 JPA `AttributeConverter` 实现 SM4 字段级透明加密，附带启动时存量数据迁移；最后产出 `nginx.conf`、`scripts/` 和部署手册。

**Tech Stack:** Java 17, Spring Boot 3.2, DM8, Hibernate 6.4, BouncyCastle SM4, Nginx, Bash

---

## 前置准备

以下两条命令只需执行一次，将 DM 驱动和方言包安装到本地 Maven 仓库。

```bash
mvn install:install-file \
  -Dfile=D:/dmdbms/drivers/jdbc/DmJdbcDriver11.jar \
  -DgroupId=com.dameng \
  -DartifactId=DmJdbcDriver \
  -Dversion=8.1.3 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=D:/dmdbms/drivers/jdbc/dialect/DmDialect-for-hibernate6.4.jar \
  -DgroupId=com.dameng \
  -DartifactId=DmDialect \
  -Dversion=6.4.0 \
  -Dpackaging=jar
```

Expected: 两条命令均输出 `BUILD SUCCESS`。

---

### Task 1: 添加 DM 依赖到 pom.xml

**Files:**
- Modify: `pom.xml:95` (在 `</dependencies>` 之前)

**Step 1: 插入 DM 依赖**

在 `pom.xml` 中 `spring-boot-starter-test` 依赖之后添加：

```xml
        <dependency>
            <groupId>com.dameng</groupId>
            <artifactId>DmJdbcDriver</artifactId>
            <version>8.1.3</version>
        </dependency>

        <dependency>
            <groupId>com.dameng</groupId>
            <artifactId>DmDialect</artifactId>
            <version>6.4.0</version>
        </dependency>
```

**Step 2: 编译验证依赖解析**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: add Dameng DM8 JDBC driver and Hibernate dialect dependencies"
```

---

### Task 2: 创建 application-dm.yml

**Files:**
- Create: `src/main/resources/application-dm.yml`

**Step 1: 编写配置**

```yaml
spring:
  datasource:
    url: jdbc:dm://localhost:5236/GOVCMS1
    username: GOVCMS1
    password: ${DM_PASSWORD:}
    driver-class-name: dm.jdbc.driver.DmDriver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.DmDialect
        format_sql: true
```

**Step 2: Commit**

```bash
git add src/main/resources/application-dm.yml
git commit -m "config: add application-dm.yml for Dameng database"
```

---

### Task 3: 将 application-dm.yml 加入 .gitignore

**Files:**
- Modify: `.gitignore:38`

**Step 1: 追加规则**

在 `.gitignore` 末尾添加：

```gitignore
# Dameng local credentials
application-dm.yml
```

**Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: ignore application-dm.yml to prevent credential leak"
```

---

### Task 4: 在 application.yml 中移除硬编码方言

**Files:**
- Modify: `src/main/resources/application.yml:17`

**Step 1: 删除 MySQLDialect 行**

删除或注释掉：
```yaml
        dialect: org.hibernate.dialect.MySQLDialect
```

Spring Boot 会根据 `driver-class-name` 自动推断方言，`application.yml` 中不需要硬编码。各 profile 自己在需要时指定。

**Step 2: 编译验证**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: remove hardcoded MySQLDialect from base application.yml"
```

---

### Task 5: 运行全量测试验证 DM 兼容性

**Files:**
- N/A (验证任务)

**Step 1: 设置环境变量并运行测试**

```bash
export DM_PASSWORD="Qhxckj.com1"
/c/apache-maven/bin/mvn.cmd test -Dspring.profiles.active=dm -q
```

Expected: `BUILD SUCCESS`，144 tests, 0 failures。

**Step 2: 如遇到 schema 兼容性问题，修复后重新测试**

常见问题及处理：
- `TEXT` 类型映射异常：检查 `SearchIndexEntry.searchText` 的 `@Column(columnDefinition = "TEXT")`，DM 下 Hibernate 应自动映射为 `CLOB`，如报错改为 `@Column(length = 2147483647)` 或 `@Lob`
- `LocalDateTime` 映射异常：升级 DM JDBC Driver 11 已支持 JSR-310，通常无问题
- 索引长度超限：DM 对 `varchar(500)` 加唯一索引可能超限，如报错将 `User.email` 的 `length` 改为 254

**Step 3: Commit（如无变更则跳过）**

```bash
git add -A
git commit -m "fix: resolve DM schema compatibility issues"
```

---

### Task 6: 为 GmCryptoProperties 添加 SM4 配置

**Files:**
- Modify: `src/main/java/gov/cms/admin/config/GmCryptoProperties.java`

**Step 1: 添加 Sm4 内部类**

在 `GmCryptoProperties.java` 中，将文件内容替换为：

```java
package gov.cms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gm.crypto")
public class GmCryptoProperties {

    private boolean enabled = true;

    private Sm2 sm2 = new Sm2();

    private Sm4 sm4 = new Sm4();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Sm2 getSm2() {
        return sm2;
    }

    public void setSm2(Sm2 sm2) {
        this.sm2 = sm2;
    }

    public Sm4 getSm4() {
        return sm4;
    }

    public void setSm4(Sm4 sm4) {
        this.sm4 = sm4;
    }

    public static class Sm2 {
        private String privateKeyHex;
        private String publicKeyHex;

        public String getPrivateKeyHex() {
            return privateKeyHex;
        }

        public void setPrivateKeyHex(String privateKeyHex) {
            this.privateKeyHex = privateKeyHex;
        }

        public String getPublicKeyHex() {
            return publicKeyHex;
        }

        public void setPublicKeyHex(String publicKeyHex) {
            this.publicKeyHex = publicKeyHex;
        }
    }

    public static class Sm4 {
        private String keyHex;

        public String getKeyHex() {
            return keyHex;
        }

        public void setKeyHex(String keyHex) {
            this.keyHex = keyHex;
        }
    }
}
```

**Step 2: 编译验证**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/config/GmCryptoProperties.java
git commit -m "feat: add SM4 key configuration to GmCryptoProperties"
```

---

### Task 7: 更新 application.yml 添加 SM4 密钥占位符

**Files:**
- Modify: `src/main/resources/application.yml:69-80`

**Step 1: 在 gm.crypto 块中添加 sm4**

将 `application.yml` 中：
```yaml
gm:
  crypto:
    enabled: true
    sm2:
      private-key-hex: ${GM_SM2_PRIVATE_KEY:}
      public-key-hex: ${GM_SM2_PUBLIC_KEY:}
```

替换为：
```yaml
gm:
  crypto:
    enabled: true
    sm2:
      private-key-hex: ${GM_SM2_PRIVATE_KEY:}
      public-key-hex: ${GM_SM2_PUBLIC_KEY:}
    sm4:
      key-hex: ${GM_SM4_KEY:}
```

**Step 2: 编译验证配置绑定**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add SM4 key placeholder to application.yml"
```

---

### Task 8: 创建 Sm4FieldConverter

**Files:**
- Create: `src/main/java/gov/cms/admin/security/Sm4FieldConverter.java`

**Step 1: 编写 AttributeConverter**

```java
package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@Converter(autoApply = false)
public class Sm4FieldConverter implements AttributeConverter<String, String> {

    private static GmCryptoService gmCryptoService;
    private static GmCryptoProperties gmCryptoProperties;

    public Sm4FieldConverter(GmCryptoService gmCryptoService, GmCryptoProperties gmCryptoProperties) {
        Sm4FieldConverter.gmCryptoService = gmCryptoService;
        Sm4FieldConverter.gmCryptoProperties = gmCryptoProperties;
    }

    public Sm4FieldConverter() {
        // JPA requires default no-arg constructor
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        String keyHex = resolveKeyHex();
        if (keyHex == null || keyHex.isBlank()) {
            return attribute;
        }
        try {
            byte[] encrypted = gmCryptoService.sm4Encrypt(
                    attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    hexToBytes(keyHex)
            );
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("SM4 加密失败", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        String keyHex = resolveKeyHex();
        if (keyHex == null || keyHex.isBlank()) {
            return dbData;
        }
        try {
            byte[] decrypted = gmCryptoService.sm4Decrypt(
                    Base64.getDecoder().decode(dbData),
                    hexToBytes(keyHex)
            );
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 读时兼容：解密失败视为存量明文数据
            return dbData;
        }
    }

    private static String resolveKeyHex() {
        if (gmCryptoProperties != null && gmCryptoProperties.getSm4() != null) {
            return gmCryptoProperties.getSm4().getKeyHex();
        }
        return null;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
```

**Step 2: 编译验证**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/security/Sm4FieldConverter.java
git commit -m "feat: add Sm4FieldConverter for transparent field-level encryption"
```

---

### Task 9: 创建 Sm4DataMigration 启动迁移 Bean

**Files:**
- Create: `src/main/java/gov/cms/admin/config/Sm4DataMigration.java`

**Step 1: 编写迁移 Bean**

```java
package gov.cms.admin.config;

import gov.cms.admin.entity.User;
import gov.cms.admin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Sm4DataMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Sm4DataMigration.class);

    private final UserRepository userRepository;
    private final GmCryptoProperties gmCryptoProperties;

    public Sm4DataMigration(UserRepository userRepository, GmCryptoProperties gmCryptoProperties) {
        this.userRepository = userRepository;
        this.gmCryptoProperties = gmCryptoProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (gmCryptoProperties.getSm4() == null
                || gmCryptoProperties.getSm4().getKeyHex() == null
                || gmCryptoProperties.getSm4().getKeyHex().isBlank()) {
            log.info("SM4 key not configured, skipping data migration");
            return;
        }

        var users = userRepository.findAll();
        int migrated = 0;
        for (User user : users) {
            boolean changed = false;
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                changed = true;
            }
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                migrated++;
            }
        }

        if (migrated > 0) {
            log.info("SM4 data migration completed: {} users processed", migrated);
        }
    }
}
```

**Step 2: 编译验证**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/config/Sm4DataMigration.java
git commit -m "feat: add Sm4DataMigration to encrypt existing user fields on startup"
```

---

### Task 10: 在 User 实体上应用 @Convert

**Files:**
- Modify: `src/main/java/gov/cms/admin/entity/User.java`

**Step 1: 添加 import 和注解**

在 `User.java` 顶部添加 import：
```java
import gov.cms.admin.security.Sm4FieldConverter;
import jakarta.persistence.Convert;
```

修改字段注解：

```java
    @Convert(converter = Sm4FieldConverter.class)
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Convert(converter = Sm4FieldConverter.class)
    @Column(length = 100)
    private String fullName;
```

**Step 2: 编译验证**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/entity/User.java
git commit -m "feat: apply SM4 encryption to User.email and User.fullName"
```

---

### Task 11: 修复 UserRepository.searchUsers（移除 email LIKE）

**Files:**
- Modify: `src/main/java/gov/cms/admin/repository/UserRepository.java:21-28`

**Step 1: 修改查询**

将 `searchUsers` 方法中的 `@Query` 替换为：

```java
    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:enabled IS NULL OR u.enabled = :enabled)
            """)
    Page<User> searchUsers(@Param("keyword") String keyword, @Param("enabled") Boolean enabled, Pageable pageable);
```

**说明**：由于 `email` 已加密，`LIKE` 查询无法命中。保留 `username` 的 `LIKE` 搜索即可满足管理后台的基本需求。

**Step 2: 编译验证**

Run: `/c/apache-maven/bin/mvn.cmd compile -q`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/repository/UserRepository.java
git commit -m "fix: remove email from keyword search due to SM4 encryption"
```

---

### Task 12: 更新前端 Users.vue 搜索提示

**Files:**
- Modify: `frontend/src/views/Users.vue:380`

**Step 1: 修改 placeholder**

将：
```html
<input v-model="searchKeyword" class="admin-search-input" placeholder="按用户名、姓名或邮箱搜索" @keyup.enter="handleSearch" />
```

替换为：
```html
<input v-model="searchKeyword" class="admin-search-input" placeholder="按用户名搜索" @keyup.enter="handleSearch" />
```

**Step 2: Commit**

```bash
git add frontend/src/views/Users.vue
git commit -m "fix: update user search placeholder to reflect encrypted fields"
```

---

### Task 13: 编写 Sm4FieldConverter 单元测试

**Files:**
- Create: `src/test/java/gov/cms/admin/security/Sm4FieldConverterTest.java`

**Step 1: 编写测试**

```java
package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Sm4FieldConverterTest {

    private Sm4FieldConverter converter;

    @BeforeEach
    void setUp() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties gmCryptoProperties = new GmCryptoProperties();
        gmCryptoProperties.getSm4().setKeyHex("0123456789abcdef0123456789abcdef");
        converter = new Sm4FieldConverter(gmCryptoService, gmCryptoProperties);
    }

    @Test
    void convertToDatabaseColumn_andBack_shouldRestoreOriginal() {
        String original = "test@example.com";
        String dbValue = converter.convertToDatabaseColumn(original);
        assertNotNull(dbValue);
        assertNotEquals(original, dbValue);

        String restored = converter.convertToEntityAttribute(dbValue);
        assertEquals(original, restored);
    }

    @Test
    void convertToDatabaseColumn_withNull_shouldReturnNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToDatabaseColumn(""));
    }

    @Test
    void convertToEntityAttribute_withPlaintext_shouldReturnAsIs() {
        String plaintext = "legacy-plaintext-email";
        String result = converter.convertToEntityAttribute(plaintext);
        assertEquals(plaintext, result);
    }

    @Test
    void convertToDatabaseColumn_withUnicode_shouldWork() {
        String original = "测试中文@example.com";
        String dbValue = converter.convertToDatabaseColumn(original);
        String restored = converter.convertToEntityAttribute(dbValue);
        assertEquals(original, restored);
    }
}
```

**Step 2: 运行测试**

Run: `/c/apache-maven/bin/mvn.cmd test -Dtest=Sm4FieldConverterTest -q`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

**Step 3: Commit**

```bash
git add src/test/java/gov/cms/admin/security/Sm4FieldConverterTest.java
git commit -m "test: add unit tests for Sm4FieldConverter encryption roundtrip"
```

---

### Task 14: 运行全量测试（DM + SM4）

**Files:**
- N/A (验证任务)

**Step 1: 运行全部测试**

```bash
export DM_PASSWORD="Qhxckj.com1"
export GM_SM4_KEY="0123456789abcdef0123456789abcdef"
/c/apache-maven/bin/mvn.cmd test -Dspring.profiles.active=dm -q
```

Expected: `BUILD SUCCESS`，148+ tests, 0 failures。

**Step 2: Commit（如无变更则跳过）**

---

### Task 15: 创建 application-prod.yml

**Files:**
- Create: `src/main/resources/application-prod.yml`

**Step 1: 编写生产配置**

```yaml
spring:
  datasource:
    url: ${PROD_DB_URL:jdbc:dm://localhost:5236/GOVCMS1}
    username: ${PROD_DB_USER:GOVCMS1}
    password: ${PROD_DB_PASSWORD:}
    driver-class-name: dm.jdbc.driver.DmDriver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.DmDialect
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never
    properties:
      org:
        quartz:
          scheduler:
            instanceName: govcms-scheduler
            instanceId: AUTO
          jobStore:
            class: org.quartz.impl.jdbcjobstore.JobStoreTX
            driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
            tablePrefix: QRTZ_
            useProperties: false
            isClustered: true
          threadPool:
            class: org.quartz.simpl.SimpleThreadPool
            threadCount: 10

server:
  port: 8080
  servlet:
    session:
      timeout: 30m

logging:
  level:
    root: WARN
    gov.cms: INFO
    org.springframework.security: WARN

jwt:
  expiration: 86400000

gm:
  crypto:
    enabled: true
    sm2:
      private-key-hex: ${GM_SM2_PRIVATE_KEY:}
      public-key-hex: ${GM_SM2_PUBLIC_KEY:}
    sm4:
      key-hex: ${GM_SM4_KEY:}

app:
  media:
    storage-path: /var/govcms/storage/media
  publish:
    storage-path: /var/govcms/storage/publish
```

**Step 2: Commit**

```bash
git add src/main/resources/application-prod.yml
git commit -m "config: add production profile template with DM and connection pooling"
```

---

### Task 16: 创建 nginx.conf

**Files:**
- Create: `nginx.conf`

**Step 1: 编写配置**

```nginx
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;

    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;

    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/xml application/json application/javascript application/rss+xml application/atom+xml image/svg+xml;

    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;

    upstream govcms_backend {
        server 127.0.0.1:8080;
        keepalive 32;
    }

    server {
        listen 80;
        server_name _;
        client_max_body_size 50M;

        location / {
            root /var/www/govcms-admin;
            try_files $uri $uri/ /index.html;
            expires 1h;
            add_header Cache-Control "public, must-revalidate";
        }

        location /api/ {
            limit_req zone=api burst=20 nodelay;
            proxy_pass http://govcms_backend/api/;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 300s;
        }

        location /preview/ {
            proxy_pass http://govcms_backend/preview/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_read_timeout 300s;
        }

        location /portal/ {
            alias /var/govcms/storage/publish/;
            try_files $uri $uri/ =404;
            expires 1d;
            add_header Cache-Control "public, must-revalidate";
        }

        location /health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }
    }
}
```

**Step 2: Commit**

```bash
git add nginx.conf
git commit -m "chore: add nginx.conf for reverse proxy and static file serving"
```

---

### Task 17: 创建 scripts/deploy.sh

**Files:**
- Create: `scripts/deploy.sh`

**Step 1: 编写脚本**

```bash
#!/bin/bash
set -e

APP_NAME="govcms-admin"
APP_JAR="target/govcms-admin-0.0.1-SNAPSHOT.jar"
APP_HOME="/opt/govcms"
LOG_DIR="/var/log/govcms"
PID_FILE="/var/run/govcms.pid"

export SPRING_PROFILES_ACTIVE=prod
export PROD_DB_PASSWORD="${PROD_DB_PASSWORD:-}"
export GM_SM2_PRIVATE_KEY="${GM_SM2_PRIVATE_KEY:-}"
export GM_SM2_PUBLIC_KEY="${GM_SM2_PUBLIC_KEY:-}"
export GM_SM4_KEY="${GM_SM4_KEY:-}"

start() {
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is already running (PID: $(cat $PID_FILE))"
        exit 1
    fi

    echo "Starting $APP_NAME..."
    nohup java -jar "$APP_HOME/$APP_JAR" \
        --spring.profiles.active=prod \
        > "$LOG_DIR/stdout.log" 2>&1 &
    echo $! > "$PID_FILE"
    echo "$APP_NAME started with PID $(cat $PID_FILE)"
}

stop() {
    if [ ! -f "$PID_FILE" ] || ! kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is not running"
        exit 1
    fi

    echo "Stopping $APP_NAME..."
    kill $(cat "$PID_FILE")
    rm -f "$PID_FILE"
    echo "$APP_NAME stopped"
}

status() {
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is running (PID: $(cat $PID_FILE))"
    else
        echo "$APP_NAME is not running"
    fi
}

case "${1:-}" in
    start) start ;;
    stop) stop ;;
    restart) stop; sleep 2; start ;;
    status) status ;;
    *) echo "Usage: $0 {start|stop|restart|status}"; exit 1 ;;
esac
```

**Step 2: Commit**

```bash
git add scripts/deploy.sh
git commit -m "chore: add deploy.sh for application lifecycle management"
```

---

### Task 18: 创建 scripts/backup.sh

**Files:**
- Create: `scripts/backup.sh`

**Step 1: 编写脚本**

```bash
#!/bin/bash
set -e

BACKUP_BASE="/backup/govcms"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$BACKUP_BASE/$TIMESTAMP"
DB_USER="${PROD_DB_USER:-GOVCMS1}"
DB_PASSWORD="${PROD_DB_PASSWORD:-}"
DB_HOST="${PROD_DB_HOST:-localhost}"
DB_PORT="${PROD_DB_PORT:-5236}"
DB_NAME="${PROD_DB_NAME:-GOVCMS1}"
STORAGE_DIR="/var/govcms/storage"
RETENTION_DAYS=30

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting backup to $BACKUP_DIR"

echo "Backing up database..."
disql "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT" -e "BACKUP DATABASE FULL TO '$BACKUP_DIR/db.bak' COMPRESSED;"

echo "Backing up storage..."
tar czf "$BACKUP_DIR/storage.tar.gz" -C "$STORAGE_DIR" .

echo "Backing up application jar..."
cp /opt/govcms/govcms-admin-0.0.1-SNAPSHOT.jar "$BACKUP_DIR/"

echo "[$(date)] Backup completed: $BACKUP_DIR"

find "$BACKUP_BASE" -maxdepth 1 -type d -mtime +$RETENTION_DAYS -exec rm -rf {} \; 2>/dev/null || true
echo "Cleaned up backups older than $RETENTION_DAYS days"
```

**Step 2: Commit**

```bash
git add scripts/backup.sh
git commit -m "chore: add backup.sh for database and storage backup"
```

---

### Task 19: 创建 docs/deployment-guide.md

**Files:**
- Create: `docs/deployment-guide.md`

**Step 1: 编写手册**

```markdown
# GovCMS 部署手册

## 环境要求

- 操作系统：麒麟/统信/其他国产 OS
- JDK：OpenJDK 17
- 数据库：达梦 DM8
- 缓存：Redis 6+
- Web 服务器：Nginx 1.20+

## 1. 数据库准备

### 1.1 安装达梦 DM8

参照达梦官方安装手册完成数据库安装。

### 1.2 创建数据库和用户

```sql
CREATE DATABASE GOVCMS1;
CREATE USER GOVCMS1 IDENTIFIED BY 'YourSecurePassword';
GRANT DBA TO GOVCMS1;
```

### 1.3 初始化 Quartz 表

应用首次启动时会自动创建 Quartz 表（`initialize-schema: always` 仅在首次启用）。

## 2. 应用部署

### 2.1 目录结构

```
/opt/govcms/
├── govcms-admin-0.0.1-SNAPSHOT.jar
└── application-prod.yml (可选，如需要覆盖默认配置)

/var/govcms/storage/
├── media/
└── publish/

/var/www/govcms-admin/
└── (前端构建产物)
```

### 2.2 环境变量

编辑 `/opt/govcms/env`：

```bash
export PROD_DB_URL=jdbc:dm://localhost:5236/GOVCMS1
export PROD_DB_USER=GOVCMS1
export PROD_DB_PASSWORD=YourSecurePassword
export GM_SM2_PRIVATE_KEY=your_sm2_private_key_hex
export GM_SM2_PUBLIC_KEY=your_sm2_public_key_hex
export GM_SM4_KEY=your_32_char_hex_key
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

### 2.3 启动应用

```bash
chmod +x scripts/deploy.sh
source /opt/govcms/env
scripts/deploy.sh start
```

验证：
```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/auth/login -X POST -d '{"username":"admin","password":"admin123"}' -H "Content-Type: application/json"
```

## 3. Nginx 配置

```bash
cp nginx.conf /etc/nginx/nginx.conf
nginx -t
systemctl reload nginx
```

## 4. 前端部署

```bash
cd frontend
npm install
npm run build
rm -rf /var/www/govcms-admin/*
cp -r dist/* /var/www/govcms-admin/
```

## 5. 备份

```bash
chmod +x scripts/backup.sh
scripts/backup.sh
```

## 6. 回滚

1. 停止应用：`scripts/deploy.sh stop`
2. 恢复数据库：使用达梦备份工具还原
3. 恢复存储：`tar xzf /backup/govcms/YYYYMMDD_HHMMSS/storage.tar.gz -C /var/govcms/storage`
4. 恢复 jar：`cp /backup/govcms/YYYYMMDD_HHMMSS/govcms-admin-0.0.1-SNAPSHOT.jar /opt/govcms/`
5. 启动应用：`scripts/deploy.sh start`

## 7. 国密密钥生成

如需重新生成 SM2 密钥对：

```java
// 在应用中临时执行
@Autowired GmCryptoService gmCryptoService;
KeyPair kp = gmCryptoService.generateSm2KeyPair();
System.out.println("Private: " + bytesToHex(kp.getPrivate().getEncoded()));
System.out.println("Public: " + bytesToHex(kp.getPublic().getEncoded()));
```

SM4 密钥：随机生成 16 字节（32 字符 hex）。
```

**Step 2: Commit**

```bash
git add docs/deployment-guide.md
git commit -m "docs: add deployment guide for production environment"
```

---

### Task 20: 最终全量验证

**Files:**
- N/A (验证任务)

**Step 1: 编译生产包**

```bash
/c/apache-maven/bin/mvn.cmd package -DskipTests -q
```

Expected: `BUILD SUCCESS`

**Step 2: 运行全量测试**

```bash
export DM_PASSWORD="Qhxckj.com1"
export GM_SM4_KEY="0123456789abcdef0123456789abcdef"
/c/apache-maven/bin/mvn.cmd test -Dspring.profiles.active=dm -q
```

Expected: `BUILD SUCCESS`, 148+ tests, 0 failures。

**Step 3: 前端类型检查**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: 无类型错误。

**Step 4: 最终 Commit**

```bash
git add -A
git commit -m "feat: complete DM database migration, SM4 encryption, and delivery artifacts"
```

---

## 实施完成后检查清单

- [ ] `mvn test -Dspring.profiles.active=dm` 全量通过
- [ ] DM 驱动 `DmJdbcDriver11.jar` 和方言 `DmDialect-for-hibernate6.4.jar` 已安装到本地 Maven
- [ ] `application-dm.yml` 已加入 `.gitignore`
- [ ] `User.email` 和 `User.fullName` 在 DM 数据库中存储为 Base64 密文
- [ ] `Sm4DataMigration` 在启动时自动加密存量数据（日志中可见 `SM4 data migration completed`）
- [ ] 存量明文 `email` 读取时正常返回（读时兼容生效）
- [ ] `scripts/deploy.sh` 可正常启停应用
- [ ] `nginx.conf` 语法检查通过（`nginx -t`）
- [ ] `docs/deployment-guide.md` 包含完整的部署、备份、回滚步骤
