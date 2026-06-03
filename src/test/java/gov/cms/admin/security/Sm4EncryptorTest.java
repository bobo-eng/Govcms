package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Sm4EncryptorTest {

    private Sm4Encryptor encryptor;

    @BeforeEach
    void setUp() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties gmCryptoProperties = new GmCryptoProperties();
        gmCryptoProperties.getSm4().setKeyHex("0123456789abcdef0123456789abcdef");
        encryptor = new Sm4Encryptor(gmCryptoService, gmCryptoProperties);
    }

    @Test
    void encrypt_andDecrypt_shouldRestoreOriginal() {
        String original = "test@example.com";
        String encrypted = encryptor.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String restored = encryptor.decrypt(encrypted);
        assertEquals(original, restored);
    }

    @Test
    void encrypt_withNullOrBlank_shouldReturnAsIs() {
        assertNull(encryptor.encrypt(null));
        assertEquals("", encryptor.encrypt(""));
    }

    @Test
    void decrypt_withPlaintext_shouldReturnAsIs() {
        String plaintext = "legacy-plaintext-email";
        String result = encryptor.decrypt(plaintext);
        assertEquals(plaintext, result);
    }

    @Test
    void encrypt_withUnicode_shouldWork() {
        String original = "测试中文@example.com";
        String encrypted = encryptor.encrypt(original);
        String restored = encryptor.decrypt(encrypted);
        assertEquals(original, restored);
    }
}
