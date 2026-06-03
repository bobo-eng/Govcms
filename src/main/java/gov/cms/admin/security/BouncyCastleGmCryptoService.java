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
            // TODO: 当前使用固定全零 IV，业务启用 SM4 前必须改为随机 IV 并随密文存储/传输
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
            // TODO: 当前使用固定全零 IV，业务启用 SM4 前必须改为随机 IV 并随密文存储/传输
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
