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

    private static final long JWT_EXPIRATION_REMEMBER = 604800000L; // 7 days

    public String generateToken(String username) {
        return generateToken(username, false);
    }

    public String generateToken(String username, boolean rememberMe) {
        Instant now = Instant.now();
        long expirationMillis = rememberMe ? JWT_EXPIRATION_REMEMBER : jwtProperties.getExpiration();
        Instant expiration = now.plusMillis(expirationMillis);

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
