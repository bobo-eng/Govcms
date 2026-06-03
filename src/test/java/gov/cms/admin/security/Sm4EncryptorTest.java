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

    @Test
    void encrypt_withMissingKey_shouldThrow() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties props = new GmCryptoProperties();
        props.getSm4().setKeyHex(null);
        Sm4Encryptor noKeyEncryptor = new Sm4Encryptor(gmCryptoService, props);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> noKeyEncryptor.encrypt("test@example.com"));
        assertTrue(ex.getMessage().contains("key-hex is not configured"));
    }

    @Test
    void decrypt_withMissingKey_shouldThrow() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties props = new GmCryptoProperties();
        props.getSm4().setKeyHex(null);
        Sm4Encryptor noKeyEncryptor = new Sm4Encryptor(gmCryptoService, props);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> noKeyEncryptor.decrypt("dGVzdA=="));
        assertTrue(ex.getMessage().contains("key-hex is not configured"));
    }

    @Test
    void encrypt_whenDisabled_shouldReturnPlaintext() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties props = new GmCryptoProperties();
        props.setEnabled(false);
        Sm4Encryptor disabledEncryptor = new Sm4Encryptor(gmCryptoService, props);

        String original = "test@example.com";
        assertEquals(original, disabledEncryptor.encrypt(original));
    }

    @Test
    void decrypt_withCorruptedBase64_shouldThrow() {
        // Valid Base64 but not valid SM4 ciphertext -> decryption should fail
        String corrupted = "dGVzdA=="; // "test" in Base64, not SM4 ciphertext

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> encryptor.decrypt(corrupted));
        assertTrue(ex.getMessage().contains("SM4 decryption failed"));
    }

    @Test
    void decrypt_withInvalidBase64_shouldReturnAsIs() {
        String invalid = "not-valid-base64!!!";
        assertEquals(invalid, encryptor.decrypt(invalid));
    }
}
