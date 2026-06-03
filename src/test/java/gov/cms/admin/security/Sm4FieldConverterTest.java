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
    void convertToDatabaseColumn_withNullOrBlank_shouldReturnAsIs() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
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
