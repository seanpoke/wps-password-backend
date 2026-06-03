package com.docauth.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * ECC椭圆曲线加密工具类
 * 使用ECIES方案（Elliptic Curve Integrated Encryption Scheme）
 * 基于BouncyCastle实现
 */
public class EccUtil {

    private static final String ALGORITHM = "EC";
    private static final String CURVE_NAME = "secp256r1"; // NIST P-256曲线
    private static final String PROVIDER = "BC";

    // AES加密相关常量
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";

    static {
        // 注册BouncyCastle Provider
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 生成ECC公私钥对
     *
     * @return 包含公钥和私钥的Map，key为"publicKey"和"privateKey"
     * @throws Exception 异常
     */
    public static Map<String, String> generateKeyPair() throws Exception {
        // 使用ECGenParameterSpec指定曲线
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(CURVE_NAME);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
        keyPairGenerator.initialize(ecSpec, new SecureRandom());

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        // 转换为Base64编码
        String publicKeyStr = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String privateKeyStr = Base64.getEncoder().encodeToString(privateKey.getEncoded());

        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("publicKey", publicKeyStr);
        keyMap.put("privateKey", privateKeyStr);

        return keyMap;
    }

    /**
     * 使用公钥加密数据（ECIES方案）
     * 流程：
     * 1. 生成临时ECC密钥对
     * 2. 通过ECDH计算出共享密钥
     * 3. 使用共享密钥派生AES密钥
     * 4. 使用AES加密明文
     * 5. 返回：临时公钥 + IV + 密文
     *
     * @param data         原始数据
     * @param publicKeyStr Base64编码的公钥
     * @return Base64编码的加密数据（格式：临时公钥|IV|密文）
     * @throws Exception 异常
     */
    public static String encrypt(String data, String publicKeyStr) throws Exception {
        // 1. 解析公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER);
        PublicKey publicKey = keyFactory.generatePublic(
                new java.security.spec.X509EncodedKeySpec(publicKeyBytes)
        );

        // 2. 生成临时密钥对
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(CURVE_NAME);
        KeyPairGenerator tempKeyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
        tempKeyPairGenerator.initialize(ecSpec, new SecureRandom());
        KeyPair tempKeyPair = tempKeyPairGenerator.generateKeyPair();

        // 3. ECDH密钥协商 - 计算共享密钥
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", PROVIDER);
        keyAgreement.init(tempKeyPair.getPrivate());
        keyAgreement.doPhase(publicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        // 4. 从共享密钥派生AES密钥（使用SHA-256哈希）
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] aesKeyBytes = sha256.digest(sharedSecret);

        // 5. 生成随机IV
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[16]; // AES块大小为16字节
        random.nextBytes(iv);

        // 6. AES加密
        Cipher aesCipher = Cipher.getInstance(AES_ALGORITHM);
        SecretKeySpec aesKeySpec = new SecretKeySpec(aesKeyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, ivSpec);

        byte[] encryptedData = aesCipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // 7. 组合结果：临时公钥 + IV + 密文
        byte[] tempPublicKeyBytes = tempKeyPair.getPublic().getEncoded();

        // 格式：[临时公钥长度(4字节)][临时公钥][IV(16字节)][密文]
        byte[] result = new byte[4 + tempPublicKeyBytes.length + iv.length + encryptedData.length];

        // 写入临时公钥长度
        result[0] = (byte) ((tempPublicKeyBytes.length >> 24) & 0xFF);
        result[1] = (byte) ((tempPublicKeyBytes.length >> 16) & 0xFF);
        result[2] = (byte) ((tempPublicKeyBytes.length >> 8) & 0xFF);
        result[3] = (byte) (tempPublicKeyBytes.length & 0xFF);

        // 写入临时公钥
        System.arraycopy(tempPublicKeyBytes, 0, result, 4, tempPublicKeyBytes.length);

        // 写入IV
        System.arraycopy(iv, 0, result, 4 + tempPublicKeyBytes.length, iv.length);

        // 写入密文
        System.arraycopy(encryptedData, 0, result, 4 + tempPublicKeyBytes.length + iv.length, encryptedData.length);

        return Base64.getEncoder().encodeToString(result);
    }

    /**
     * 使用私钥解密数据（ECIES方案）
     * 流程：
     * 1. 解析加密数据，提取临时公钥、IV、密文
     * 2. 通过ECDH计算出共享密钥
     * 3. 使用共享密钥派生AES密钥
     * 4. 使用AES解密密文
     *
     * @param encryptedDataBase64 Base64编码的加密数据
     * @param privateKeyStr       Base64编码的私钥
     * @return 解密后的原始数据
     * @throws Exception 异常
     */
    public static String decrypt(String encryptedDataBase64, String privateKeyStr) throws Exception {
        // 1. 解码加密数据
        byte[] encryptedData = Base64.getDecoder().decode(encryptedDataBase64);

        // 2. 校验数据长度（至少需要4字节的公钥长度头 + 公钥数据 + IV + 密文）
        if (encryptedData.length < 4) {
            throw new IllegalArgumentException("加密数据格式错误：数据长度不足");
        }

        // 3. 解析临时公钥长度
        int publicKeyLength = ((encryptedData[0] & 0xFF) << 24) |
                ((encryptedData[1] & 0xFF) << 16) |
                ((encryptedData[2] & 0xFF) << 8) |
                (encryptedData[3] & 0xFF);

        // 4. 校验公钥长度的合理性
        if (publicKeyLength <= 0 || publicKeyLength > encryptedData.length - 4 - 16) {
            throw new IllegalArgumentException("加密数据格式错误：公钥长度无效");
        }

        // 5. 提取临时公钥
        byte[] tempPublicKeyBytes = new byte[publicKeyLength];
        System.arraycopy(encryptedData, 4, tempPublicKeyBytes, 0, publicKeyLength);

        // 6. 提取IV
        byte[] iv = new byte[16];
        System.arraycopy(encryptedData, 4 + publicKeyLength, iv, 0, iv.length);

        // 7. 提取密文
        byte[] ciphertext = new byte[encryptedData.length - 4 - publicKeyLength - iv.length];
        System.arraycopy(encryptedData, 4 + publicKeyLength + iv.length, ciphertext, 0, ciphertext.length);

        // 8. 解析私钥
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER);
        PrivateKey privateKey = keyFactory.generatePrivate(
                new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
        );

        // 9. 解析临时公钥
        PublicKey tempPublicKey = keyFactory.generatePublic(
                new java.security.spec.X509EncodedKeySpec(tempPublicKeyBytes)
        );

        // 10. ECDH密钥协商 - 计算共享密钥
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", PROVIDER);
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(tempPublicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        // 11. 从共享密钥派生AES密钥（使用SHA-256哈希）
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] aesKeyBytes = sha256.digest(sharedSecret);

        // 12. AES解密
        Cipher aesCipher = Cipher.getInstance(AES_ALGORITHM);
        SecretKeySpec aesKeySpec = new SecretKeySpec(aesKeyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKeySpec, ivSpec);

        byte[] decryptedBytes = aesCipher.doFinal(ciphertext);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) throws Exception {
        // 生成密钥对
        Map<String, String> keyPair = EccUtil.generateKeyPair();
        String publicKey = keyPair.get("publicKey");
        String privateKey = keyPair.get("privateKey");

        System.out.println("========== ECC加密测试 ==========");
        System.out.println("公钥：" + publicKey);
        System.out.println("公钥长度：" + publicKey.length());
        System.out.println("私钥长度：" + privateKey.length());
        System.out.println("私钥：" + privateKey);
        System.out.println();

        // 测试数据
        String originalData = "sidnficksn12";
        System.out.println("原始数据：" + originalData);
        System.out.println("原始数据长度：" + originalData.length());
        System.out.println();

        // 加密
        String encryptedData = EccUtil.encrypt(originalData, publicKey);
        System.out.println("加密数据：" + encryptedData);
        System.out.println("加密数据长度：" + encryptedData.length());
        System.out.println();

        // 解密
        String decryptedData = EccUtil.decrypt(encryptedData, privateKey);
        System.out.println("解密数据：" + decryptedData);
        System.out.println("解密是否成功：" + originalData.equals(decryptedData));
        System.out.println();

        // 对比RSA
        System.out.println("========== 与RSA对比 ==========");
        System.out.println("ECC密文长度：" + encryptedData.length() + " 字符");
        System.out.println("RSA(2048)密文长度：约344 字符");
        System.out.println("RSA(1024)密文长度：约172 字符");
        System.out.println("ECC优势：密文更短，安全性相当或更高");
    }
}
