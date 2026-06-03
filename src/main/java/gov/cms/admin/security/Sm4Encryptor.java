package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class Sm4Encryptor {

    private final GmCryptoService gmCryptoService;
    private final GmCryptoProperties gmCryptoProperties;

    public Sm4Encryptor(GmCryptoService gmCryptoService, GmCryptoProperties gmCryptoProperties) {
        this.gmCryptoService = gmCryptoService;
        this.gmCryptoProperties = gmCryptoProperties;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (!isEnabled()) {
            return plaintext;
        }
        String keyHex = resolveKeyHex();
        if (keyHex == null || keyHex.isBlank()) {
            throw new IllegalStateException("SM4 encryption is enabled but gm.crypto.sm4.key-hex is not configured");
        }
        try {
            byte[] encrypted = gmCryptoService.sm4Encrypt(
                    plaintext.getBytes(StandardCharsets.UTF_8),
                    hexToBytes(keyHex)
            );
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("SM4 encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }
        if (!isEnabled()) {
            return ciphertext;
        }
        String keyHex = resolveKeyHex();
        if (keyHex == null || keyHex.isBlank()) {
            throw new IllegalStateException("SM4 encryption is enabled but gm.crypto.sm4.key-hex is not configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            byte[] decrypted = gmCryptoService.sm4Decrypt(decoded, hexToBytes(keyHex));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Not valid Base64 -> treat as existing plaintext (read-time compatibility)
            return ciphertext;
        } catch (Exception e) {
            // Valid Base64 but SM4 decryption failed -> possible data corruption
            throw new RuntimeException("SM4 decryption failed", e);
        }
    }

    private boolean isEnabled() {
        return gmCryptoProperties != null && gmCryptoProperties.isEnabled();
    }

    private String resolveKeyHex() {
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
