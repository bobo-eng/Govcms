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
