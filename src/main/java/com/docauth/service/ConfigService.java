package com.docauth.service;

import com.docauth.entity.DocConfig;
import com.docauth.repository.DocConfigRepository;
import com.docauth.util.RsaUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
public class ConfigService {

    @Autowired
    private DocConfigRepository docConfigRepository;

    // 配置类型常量
    public static final String CONFIG_TYPE_LDAP = "ldap-config";
    public static final String CONFIG_TYPE_SYS = "sys-config";
    public static final String CONFIG_TYPE_CACHE = "cache-config";
    public static final String CONFIG_TYPE_SECRET = "sceret-config";

    // LDAP配置键常量
    public static final String LDAP_URL = "url";
    public static final String LDAP_USERNAME = "username";
    public static final String LDAP_PASSWORD = "pswword";  // 注意: SQL 中是 pswword
    public static final String LDAP_BASE_DN = "baseDn";
    public static final String LDAP_SUB_TREE = "subTree";
    
    // 系统配置键常量
    public static final String SYS_NO_TOKEN_URL = "no-token-url";
    
    // 缓存配置键常量
    public static final String CACHE_EXPIRE = "expire";
    
    // 密钥配置键常量
    public static final String SECRET_PUBLIC_KEY = "public-key";
    public static final String SECRET_PRIVATE_KEY = "private-key";

    // 缓存配置值
    private String ldapUrl;
    private String ldapBase;
    private String ldapUsername;
    private String ldapPassword;
    private List<String> ldapTrees;
    private List<String> noTokenUrls;
    private Long cacheExpireMinutes; // 缓存过期时间(分钟)
    
    // 密钥配置值
    private String publicKey;
    private String privateKey;

    /**
     * 从数据库加载 LDAP 配置
     */
    public void loadLdapConfig() {
        try {
            ldapUrl = getConfigValue(LDAP_URL);
            ldapBase = getConfigValue(LDAP_BASE_DN);
            ldapUsername = getConfigValue(LDAP_USERNAME);
            ldapPassword = getConfigValue(LDAP_PASSWORD);
            
            // 查询所有 subTree 配置(多条记录)
            List<DocConfig> subTreeConfigs = docConfigRepository.findByTypeAndKey(CONFIG_TYPE_LDAP, LDAP_SUB_TREE);
            
            // 提取所有 subTree 值
            if (!subTreeConfigs.isEmpty()) {
                ldapTrees = subTreeConfigs.stream()
                        .map(DocConfig::getValue)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            } else {
                ldapTrees = List.of();
            }
            
            log.info("LDAP 配置加载成功 - URL: {}, Base: {}, Trees: {}", 
                    ldapUrl, ldapBase, ldapTrees);
        } catch (Exception e) {
            log.error("加载 LDAP 配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("加载 LDAP 配置失败", e);
        }
    }
    
    /**
     * 从数据库加载系统配置
     */
    public void loadSysConfig() {
        try {
            // 查询所有 no-token-url 配置(多条记录)
            List<DocConfig> noTokenUrlConfigs = docConfigRepository.findByTypeAndKey(CONFIG_TYPE_SYS, SYS_NO_TOKEN_URL);
            
            // 提取所有 URL 值
            if (!noTokenUrlConfigs.isEmpty()) {
                noTokenUrls = noTokenUrlConfigs.stream()
                        .map(DocConfig::getValue)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            } else {
                noTokenUrls = List.of();
            }
            
            log.info("系统配置加载成功 - No Token URLs: {}", noTokenUrls);
        } catch (Exception e) {
            log.error("加载系统配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("加载系统配置失败", e);
        }
    }
    
    /**
     * 从数据库加载缓存配置
     */
    public void loadCacheConfig() {
        try {
            String expireValue = docConfigRepository.findFirstByTypeAndKey(CONFIG_TYPE_CACHE, CACHE_EXPIRE)
                    .map(DocConfig::getValue)
                    .orElse("360"); // 默认360分钟
            
            cacheExpireMinutes = Long.parseLong(expireValue);
            log.info("缓存配置加载成功 - 过期时间: {} 分钟", cacheExpireMinutes);
        } catch (Exception e) {
            log.error("加载缓存配置失败: {}", e.getMessage(), e);
            // 使用默认值
            cacheExpireMinutes = 360L;
        }
    }
    
    /**
     * 从数据库加载密钥配置
     */
    public void loadSecretConfig() {
        try {
            publicKey = getConfigValueWithSecret(SECRET_PUBLIC_KEY);
            privateKey = getConfigValueWithSecret(SECRET_PRIVATE_KEY);
            log.info("密钥配置加载成功");
        } catch (Exception e) {
            log.error("加载密钥配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("加载密钥配置失败", e);
        }
    }

    /**
     * 获取配置值(单个值)
     *
     * @param key 配置键
     * @return 配置值
     */
    private String getConfigValue(String key) {
        return docConfigRepository.findFirstByTypeAndKey(CONFIG_TYPE_LDAP, key)
                .map(DocConfig::getValue)
                .orElse(null);
    }
    
    /**
     * 获取密钥配置值(单个值)
     *
     * @param key 配置键
     * @return 配置值
     */
    private String getConfigValueWithSecret(String key) {
        return docConfigRepository.findFirstByTypeAndKey(CONFIG_TYPE_SECRET, key)
                .map(DocConfig::getValue)
                .orElse(null);
    }

    /**
     * 获取 LDAP URL
     */
    public String getLdapUrl() {
        return ldapUrl;
    }

    /**
     * 获取 LDAP Base DN
     */
    public String getLdapBase() {
        return ldapBase;
    }

    /**
     * 获取 LDAP 用户名
     */
    public String getLdapUsername() {
        return ldapUsername;
    }

    /**
     * 获取 LDAP 密码
     */
    public String getLdapPassword() {
        return ldapPassword;
    }

    /**
     * 获取 LDAP 子树列表
     */
    public List<String> getLdapTrees() {
        return ldapTrees;
    }
    
    /**
     * 获取无需 Token 验证的 URL 列表
     */
    public List<String> getNoTokenUrls() {
        return noTokenUrls;
    }
    
    /**
     * 获取缓存过期时间(分钟)
     */
    public Long getCacheExpireMinutes() {
        return cacheExpireMinutes != null ? cacheExpireMinutes : 360L;
    }
    
    /**
     * 获取公钥
     */
    public String getPublicKey() {
        return publicKey;
    }
    
    /**
     * 获取私钥
     */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * 更新配置
     *
     * @param key   配置键
     * @param value 配置值
     */
    public void updateConfig(String key, String value) {
        docConfigRepository.findFirstByTypeAndKey(CONFIG_TYPE_LDAP, key).ifPresentOrElse(
                config -> {
                    config.setValue(value);
                    docConfigRepository.save(config);
                    log.info("配置更新成功: {} = {}", key, value);
                    
                    // 如果是 LDAP 配置,重新加载
                    loadLdapConfig();
                },
                () -> log.warn("配置项不存在: {}", key)
        );
    }

    /**
     * 刷新所有配置
     */
    public void refreshConfig() {
        log.info("手动刷新配置...");
        loadLdapConfig();
        loadSysConfig();
        loadCacheConfig();
        loadSecretConfig();
    }

    /**
     * 使用公钥加密文本的业务逻辑
     *
     * @param text 待加密的原始文本
     * @return 加密结果Map，包含encryptedText、originalLength、encryptedLength
     * @throws RuntimeException 加密失败时抛出
     */
    public Map<String, String> encryptText(String text) {
        // 从数据库获取公钥
        String publicKey = getPublicKey();
        if (publicKey == null || publicKey.isEmpty()) {
            throw new RuntimeException("系统配置错误：未找到公钥配置");
        }

        try {
            // 使用公钥加密
            String encryptedText = RsaUtil.encrypt(text, publicKey);

            // 构建响应
            Map<String, String> result = new HashMap<>();
            result.put("encryptedText", encryptedText);
            result.put("originalLength", String.valueOf(text.length()));
            result.put("encryptedLength", String.valueOf(encryptedText.length()));

            log.info("加密成功 - 原始长度: {}, 加密后长度: {}", text.length(), encryptedText.length());
            return result;
        } catch (Exception e) {
            log.error("加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }
}
