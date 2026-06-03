package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import gov.cms.admin.config.SpringContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Sm4FieldConverterTest {

    private Sm4FieldConverter converter;
    private Sm4Encryptor encryptor;

    @BeforeEach
    void setUp() throws Exception {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties gmCryptoProperties = new GmCryptoProperties();
        gmCryptoProperties.getSm4().setKeyHex("0123456789abcdef0123456789abcdef");
        encryptor = new Sm4Encryptor(gmCryptoService, gmCryptoProperties);

        ApplicationContext mockContext = mock(ApplicationContext.class);
        when(mockContext.getBean(Sm4Encryptor.class)).thenReturn(encryptor);

        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, mockContext);

        converter = new Sm4FieldConverter();
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

    @Test
    void convertToDatabaseColumn_withoutSpringContext_shouldThrow() throws Exception {
        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, null);

        Sm4FieldConverter freshConverter = new Sm4FieldConverter();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> freshConverter.convertToDatabaseColumn("test@example.com"));
        assertTrue(ex.getMessage().contains("Sm4Encryptor not available"));
    }
}
