# 国密 JWT 签名链路 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 引入基于 BouncyCastle 的国密服务抽象层（SM2/SM3/SM4），将 JWT 签名从 HMAC-SHA256 切换为 SM2withSM3，并移除 jjwt 依赖。

**Architecture:** 创建 `GmCryptoService` 接口与 `BouncyCastleGmCryptoService` 实现，封装 SM2 签名验签、SM3 摘要、SM4 对称加密能力。`JwtUtil` 完全自研 JWT 格式处理（Base64URL + Jackson），使用 SM2/SM3 做签名，不再依赖 jjwt 库。

**Tech Stack:** Java 17, Spring Boot 3.2, BouncyCastle 1.78 (bcprov-jdk18on)

---

### Task 1: 添加 BouncyCastle 依赖

**Files:**
- Modify: `pom.xml`

**Step 1: 在 dependencies 中添加 BouncyCastle**

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
```

添加到 `pom.xml` 的 `<dependencies>` 节点内，位置放在 `spring-boot-starter-security` 之后即可。

**Step 2: 编译验证依赖下载**

Run: `mvn compile`
Expected: BUILD SUCCESS，无错误。

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: add BouncyCastle dependency for GM crypto"
```

---

### Task 2: 创建 GmCryptoService 接口

**Files:**
- Create: `src/main/java/gov/cms/admin/security/GmCryptoService.java`

**Step 1: 编写接口**

```java
package gov.cms.admin.security;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public interface GmCryptoService {
    byte[] sm2Sign(byte[] data, PrivateKey privateKey);
    boolean sm2Verify(byte[] data, byte[] signature, PublicKey publicKey);
    byte[] sm3Digest(byte[] data);
    byte[] sm4Encrypt(byte[] data, byte[] key);
    byte[] sm4Decrypt(byte[] data, byte[] key);
    KeyPair generateSm2KeyPair();
    PrivateKey loadSm2PrivateKey(String hex);
    PublicKey loadSm2PublicKey(String hex);
}
```

**Step 2: 编译验证**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/security/GmCryptoService.java
git commit -m "feat: add GmCryptoService interface for SM2/SM3/SM4"
```

---

### Task 3: 创建 BouncyCastleGmCryptoService 实现

**Files:**
- Create: `src/main/java/gov/cms/admin/security/BouncyCastleGmCryptoService.java`

**Step 1: 编写实现类**

```java
package gov.cms.admin.security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

@Service
public class BouncyCastleGmCryptoService implements GmCryptoService {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public byte[] sm2Sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SM3withSM2", "BC");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException("SM2 签名失败", e);
        }
    }

    @Override
    public boolean sm2Verify(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SM3withSM2", "BC");
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException("SM2 验签失败", e);
        }
    }

    @Override
    public byte[] sm3Digest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SM3", "BC");
            return md.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("SM3 摘要失败", e);
        }
    }

    @Override
    public byte[] sm4Encrypt(byte[] data, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", "BC");
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("SM4 加密失败", e);
        }
    }

    @Override
    public byte[] sm4Decrypt(byte[] data, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", "BC");
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("SM4 解密失败", e);
        }
    }

    @Override
    public KeyPair generateSm2KeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
            ECGenParameterSpec spec = new ECGenParameterSpec("sm2p256v1");
            generator.initialize(spec, new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("SM2 密钥对生成失败", e);
        }
    }

    @Override
    public PrivateKey loadSm2PrivateKey(String hex) {
        try {
            byte[] encoded = HexFormat.of().parseHex(hex);
            KeyFactory factory = KeyFactory.getInstance("EC", "BC");
            return factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new RuntimeException("加载 SM2 私钥失败", e);
        }
    }

    @Override
    public PublicKey loadSm2PublicKey(String hex) {
        try {
            byte[] encoded = HexFormat.of().parseHex(hex);
            KeyFactory factory = KeyFactory.getInstance("EC", "BC");
            return factory.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new RuntimeException("加载 SM2 公钥失败", e);
        }
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/security/BouncyCastleGmCryptoService.java
git commit -m "feat: implement BouncyCastleGmCryptoService with SM2/SM3/SM4"
```

---

### Task 4: 创建 GmCryptoProperties 配置类

**Files:**
- Create: `src/main/java/gov/cms/admin/config/GmCryptoProperties.java`

**Step 1: 编写配置类**

```java
package gov.cms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gm.crypto")
public class GmCryptoProperties {

    private boolean enabled = true;

    private Sm2 sm2 = new Sm2();

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
}
```

**Step 2: 编译验证**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/config/GmCryptoProperties.java
git commit -m "feat: add GmCryptoProperties configuration for SM2 keys"
```

---

### Task 5: 重构 JwtUtil 使用 SM2/SM3 签名

**Files:**
- Modify: `src/main/java/gov/cms/admin/security/JwtUtil.java`

**Step 1: 完全重写 JwtUtil**

```java
package gov.cms.admin.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.GmCryptoProperties;
import gov.cms.admin.config.JwtProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final GmCryptoService gmCryptoService;
    private final GmCryptoProperties gmCryptoProperties;
    private final JwtProperties jwtProperties;
    private final KeyPair signingKeyPair;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtil(GmCryptoService gmCryptoService,
                   GmCryptoProperties gmCryptoProperties,
                   JwtProperties jwtProperties) {
        this.gmCryptoService = gmCryptoService;
        this.gmCryptoProperties = gmCryptoProperties;
        this.jwtProperties = jwtProperties;
        this.signingKeyPair = resolveKeyPair();
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(jwtProperties.getExpiration());

        Map<String, Object> header = Map.of("alg", "SM2", "typ", "JWT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", username);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiration.getEpochSecond());

        String encodedHeader = base64UrlEncode(toJson(header));
        String encodedPayload = base64UrlEncode(toJson(payload));
        String signingInput = encodedHeader + "." + encodedPayload;

        byte[] signature = gmCryptoService.sm2Sign(
                signingInput.getBytes(StandardCharsets.UTF_8),
                signingKeyPair.getPrivate()
        );

        return signingInput + "." + base64UrlEncode(signature);
    }

    public String extractUsername(String token) {
        Map<String, Object> payload = parsePayload(token);
        Object sub = payload.get("sub");
        if (sub == null) {
            throw new IllegalArgumentException("JWT 缺少 sub 字段");
        }
        return sub.toString();
    }

    public Date extractExpiration(String token) {
        Map<String, Object> payload = parsePayload(token);
        Object exp = payload.get("exp");
        if (exp == null) {
            throw new IllegalArgumentException("JWT 缺少 exp 字段");
        }
        long expSeconds = ((Number) exp).longValue();
        return new Date(expSeconds * 1000);
    }

    public boolean validateToken(String token) {
        try {
            parsePayload(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateToken(String token, String username) {
        try {
            String tokenUsername = extractUsername(token);
            return username.equals(tokenUsername) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Map<String, Object> parsePayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT 格式无效");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] signature = base64UrlDecode(parts[2]);

        boolean valid = gmCryptoService.sm2Verify(
                signingInput.getBytes(StandardCharsets.UTF_8),
                signature,
                signingKeyPair.getPublic()
        );

        if (!valid) {
            throw new IllegalArgumentException("JWT 签名无效");
        }

        String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        return fromJson(payloadJson);
    }

    private KeyPair resolveKeyPair() {
        String privateKeyHex = gmCryptoProperties.getSm2().getPrivateKeyHex();
        String publicKeyHex = gmCryptoProperties.getSm2().getPublicKeyHex();

        if (privateKeyHex != null && !privateKeyHex.isBlank()
                && publicKeyHex != null && !publicKeyHex.isBlank()) {
            PrivateKey privateKey = gmCryptoService.loadSm2PrivateKey(privateKeyHex);
            PublicKey publicKey = gmCryptoService.loadSm2PublicKey(publicKeyHex);
            return new KeyPair(publicKey, privateKey);
        }

        return gmCryptoService.generateSm2KeyPair();
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String base64UrlEncode(String data) {
        return base64UrlEncode(data.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private byte[] toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsBytes(map);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile`
Expected: BUILD SUCCESS。注意此时 jjwt 依赖仍然存在，`JwtAuthenticationFilter` 编译通过因为它不直接依赖 jjwt。

**Step 3: Commit**

```bash
git add src/main/java/gov/cms/admin/security/JwtUtil.java
git commit -m "feat: refactor JwtUtil to SM2/SM3 signature, remove jjwt usage"
```

---

### Task 6: 更新 application.yml 国密配置

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: 修改 application.yml**

将原来的：
```yaml
jwt:
  secret: govcms-super-secret-jwt-key-please-change-in-production-2026
  expiration: 86400000
```

替换为：
```yaml
jwt:
  # secret 已废弃：国密 JWT 使用 SM2 密钥对签名，不再使用 HMAC secret
  expiration: 86400000

gm:
  crypto:
    enabled: true
    sm2:
      private-key-hex: ${GM_SM2_PRIVATE_KEY:}
      public-key-hex: ${GM_SM2_PUBLIC_KEY:}
```

**Step 2: 编译验证 Spring Boot 配置绑定**

Run: `mvn compile`
Expected: BUILD SUCCESS。`GmCryptoProperties` 和 `JwtProperties` 都能正常绑定。

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add GM crypto config and deprecate jwt.secret"
```

---

### Task 7: 移除 jjwt 依赖

**Files:**
- Modify: `pom.xml`

**Step 1: 删除 jjwt 的三个依赖**

移除以下内容：
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>

<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

**Step 2: 编译验证无 jjwt 残留引用**

Run: `mvn compile`
Expected: BUILD SUCCESS。如果报错 `package io.jsonwebtoken does not exist`，说明还有文件没改干净，需要检查并修复。

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: remove jjwt dependencies, fully switched to GM crypto"
```

---

### Task 8: 编写 GmCryptoService 单元测试

**Files:**
- Create: `src/test/java/gov/cms/admin/security/BouncyCastleGmCryptoServiceTest.java`

**Step 1: 编写测试**

```java
package gov.cms.admin.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class BouncyCastleGmCryptoServiceTest {

    private BouncyCastleGmCryptoService gmCryptoService;

    @BeforeEach
    void setUp() {
        gmCryptoService = new BouncyCastleGmCryptoService();
    }

    @Test
    void sm2SignAndVerify_shouldSucceed() {
        KeyPair keyPair = gmCryptoService.generateSm2KeyPair();
        byte[] data = "test data".getBytes();

        byte[] signature = gmCryptoService.sm2Sign(data, keyPair.getPrivate());
        boolean valid = gmCryptoService.sm2Verify(data, signature, keyPair.getPublic());

        assertTrue(valid);
    }

    @Test
    void sm2Verify_withTamperedData_shouldFail() {
        KeyPair keyPair = gmCryptoService.generateSm2KeyPair();
        byte[] data = "test data".getBytes();
        byte[] signature = gmCryptoService.sm2Sign(data, keyPair.getPrivate());

        byte[] tamperedData = "tampered data".getBytes();
        boolean valid = gmCryptoService.sm2Verify(tamperedData, signature, keyPair.getPublic());

        assertFalse(valid);
    }

    @Test
    void sm3Digest_shouldBeConsistent() {
        byte[] data = "hello sm3".getBytes();
        byte[] digest1 = gmCryptoService.sm3Digest(data);
        byte[] digest2 = gmCryptoService.sm3Digest(data);

        assertEquals(32, digest1.length);
        assertArrayEquals(digest1, digest2);
    }

    @Test
    void sm4EncryptAndDecrypt_shouldRestoreOriginalData() {
        byte[] key = "0123456789abcdef".getBytes();
        byte[] data = "sensitive data".getBytes();

        byte[] encrypted = gmCryptoService.sm4Encrypt(data, key);
        byte[] decrypted = gmCryptoService.sm4Decrypt(encrypted, key);

        assertArrayEquals(data, decrypted);
    }

    @Test
    void loadSm2Keys_shouldRestoreKeyPair() {
        KeyPair original = gmCryptoService.generateSm2KeyPair();
        String privateHex = bytesToHex(original.getPrivate().getEncoded());
        String publicHex = bytesToHex(original.getPublic().getEncoded());

        var loadedPrivate = gmCryptoService.loadSm2PrivateKey(privateHex);
        var loadedPublic = gmCryptoService.loadSm2PublicKey(publicHex);

        byte[] data = "test".getBytes();
        byte[] signature = gmCryptoService.sm2Sign(data, loadedPrivate);
        boolean valid = gmCryptoService.sm2Verify(data, signature, loadedPublic);

        assertTrue(valid);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
```

**Step 2: 运行测试**

Run: `mvn test -Dtest=BouncyCastleGmCryptoServiceTest`
Expected: Tests run: 5, Failures: 0, Errors: 0

**Step 3: Commit**

```bash
git add src/test/java/gov/cms/admin/security/BouncyCastleGmCryptoServiceTest.java
git commit -m "test: add unit tests for BouncyCastleGmCryptoService"
```

---

### Task 9: 编写 JwtUtil 单元测试

**Files:**
- Create: `src/test/java/gov/cms/admin/security/JwtUtilTest.java`

**Step 1: 编写测试**

```java
package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import gov.cms.admin.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties gmCryptoProperties = new GmCryptoProperties();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpiration(3600000L);

        jwtUtil = new JwtUtil(gmCryptoService, gmCryptoProperties, jwtProperties);
    }

    @Test
    void generateToken_andExtractUsername_shouldMatch() {
        String token = jwtUtil.generateToken("admin");
        String username = jwtUtil.extractUsername(token);
        assertEquals("admin", username);
    }

    @Test
    void validateToken_withValidToken_shouldReturnTrue() {
        String token = jwtUtil.generateToken("admin");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_withValidTokenAndUsername_shouldReturnTrue() {
        String token = jwtUtil.generateToken("admin");
        assertTrue(jwtUtil.validateToken(token, "admin"));
    }

    @Test
    void validateToken_withWrongUsername_shouldReturnFalse() {
        String token = jwtUtil.generateToken("admin");
        assertFalse(jwtUtil.validateToken(token, "other"));
    }

    @Test
    void validateToken_withTamperedToken_shouldReturnFalse() {
        String token = jwtUtil.generateToken("admin");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";
        assertFalse(jwtUtil.validateToken(tampered));
    }
}
```

**Step 2: 运行测试**

Run: `mvn test -Dtest=JwtUtilTest`
Expected: Tests run: 5, Failures: 0, Errors: 0

**Step 3: Commit**

```bash
git add src/test/java/gov/cms/admin/security/JwtUtilTest.java
git commit -m "test: add unit tests for SM2-based JwtUtil"
```

---

### Task 10: 全量测试验证与最终提交

**Files:**
- N/A (验证任务)

**Step 1: 运行全部单元测试**

Run: `mvn test`
Expected: BUILD SUCCESS，所有原有测试 + 新增测试均通过。

**Step 2: 编译生产包验证**

Run: `mvn package -DskipTests`
Expected: BUILD SUCCESS，无编译错误。

**Step 3: 最终 Commit（如有多条零散 commit 可合并）**

```bash
# 如果此前已按任务逐条 commit，此处无需额外操作
# 如需合并为一条 feature commit：
# git reset --soft HEAD~9
# git commit -m "feat: replace HMAC-JWT with SM2/SM3 GM crypto signature"
```

---

## 实施完成后检查清单

- [ ] `mvn test` 全量通过
- [ ] `JwtUtil` 不再引用 `io.jsonwebtoken` 包
- [ ] `pom.xml` 中 jjwt 依赖已移除
- [ ] `application.yml` 包含 `gm.crypto` 配置节
- [ ] 新 token 可通过 `Bearer <token>` 正常鉴权（启动后登录验证）
- [ ] 旧 HMAC token 已失效（预期行为，需重新登录）

---

**Plan complete and saved to `docs/plans/2026-06-02-gm-crypto-jwt-implementation.md`.**

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints.

**Which approach?**
