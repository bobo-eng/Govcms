package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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
                    attribute.getBytes(StandardCharsets.UTF_8),
                    hexToBytes(keyHex)
            );
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("SM4 encryption failed", e);
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
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Read-time compatibility: treat as existing plaintext if decryption fails
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
