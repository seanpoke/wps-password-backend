package com.docauth.util;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RsaUtil {
    
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"; // 使用OAEP填充防止Padding Oracle攻击
    private static final int KEY_SIZE = 2048; // 使用2048位，密文长度约344字符
    
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
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    public static void main(String[] args) throws Exception {
        
            String[] keyPair = RsaUtil.generateKeyPair();
            String publicKey = keyPair[0];
            String privateKey = keyPair[1];

            String data = "sidnficksn12";
            String encryptedData = RsaUtil.encrypt(data, publicKey);
            String decryptedData = RsaUtil.decrypt(encryptedData, privateKey);

            System.out.println("公钥：" + publicKey);
            System.out.println("私钥：" + privateKey);
            System.out.println("原始数据：" + data);
            System.out.println("原始数据长度：" + data.length());
            System.out.println("加密数据：" + encryptedData);
            System.out.println("加密数据长度：" + encryptedData.length());
            System.out.println("解密数据：" + decryptedData);
            System.out.println("解密是否成功：" + data.equals(decryptedData));
            System.out.println("公钥长度：" + publicKey.length());
            System.out.println("私钥长度：" + privateKey.length());

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
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        
        byte[] encryptedBytes = cipher.doFinal(dataBytes);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}