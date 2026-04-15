package com.docauth.util;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RsaUtil {
    
    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    
    /**
     * 生成RSA公私钥对
     * @return 包含公钥和私钥的数组，index 0为公钥，index 1为私钥
     * @throws Exception 异常
     */
    public static String[] generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
        keyPairGenerator.initialize(KEY_SIZE);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        
        String publicKeyStr = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String privateKeyStr = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        
        return new String[]{publicKeyStr, privateKeyStr};
    }
    
    /**
     * 使用私钥解密
     * @param encryptedData 加密数据
     * @param privateKeyStr 私钥
     * @return 解密后的数据
     * @throws Exception 异常
     */
    public static String decrypt(String encryptedData, String privateKeyStr) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);
        
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }
    
    /**
     * 使用公钥加密
     * @param data 原始数据
     * @param publicKeyStr 公钥
     * @return 加密后的数据
     * @throws Exception 异常
     */
    public static String encrypt(String data, String publicKeyStr) throws Exception {
        byte[] dataBytes = data.getBytes();
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
        
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        
        byte[] encryptedBytes = cipher.doFinal(dataBytes);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}