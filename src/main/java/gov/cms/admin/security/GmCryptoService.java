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
