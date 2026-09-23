package com.docauth.service;

import com.docauth.dto.DecryptResponse;
import com.docauth.dto.EncryptResponse;
import com.docauth.dto.KeyInfoResponse;
import com.docauth.dto.LdapConfigDto;
import com.docauth.entity.ConfigSecretKey;
import com.docauth.entity.DocConfig;
import com.docauth.repository.ConfigSecretKeyRepository;
import com.docauth.repository.DocConfigRepository;
import com.docauth.util.EccUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
public class ConfigService {

    // 配置类型常量
    public static final String CONFIG_TYPE_LDAP = "ldap-config";
    public static final String CONFIG_TYPE_SYS = "sys-config";
    public static final String CONFIG_TYPE_CACHE = "cache-config";
    public static final String CONFIG_TYPE_REDIS = "redis-config";
    // LDAP配置键常量
    public static final String LDAP_URL = "url";
    public static final String LDAP_USERNAME = "username";
    public static final String LDAP_PASSWORD = "pswword";  // 注意: SQL 中是 pswword
    public static final String LDAP_BASE_DN = "baseDn";
    public static final String LDAP_SUB_TREE = "subTree";
    /** 定时全量同步触发时刻（逗号分隔，如 "10:00,20:00"） */
    public static final String LDAP_SYNC_TIMES = "syncTimes";
    /** 定时同步总开关（"true"/"false"，默认 true） */
    public static final String LDAP_SYNC_ENABLED = "syncEnabled";
    // 系统配置键常量
    public static final String SYS_NO_TOKEN_URL = "no-token-url";
    // 缓存配置键常量
    public static final String CACHE_EXPIRE = "expire";
    // Redis配置键常量
    public static final String REDIS_TOKEN_EXPIRE = "token-expire";
    @Autowired
    private DocConfigRepository docConfigRepository;
    @Autowired
    private ConfigSecretKeyRepository configSecretKeyRepository;
    // 缓存配置值
    private String ldapUrl;
    private String ldapBase;
    private String ldapUsername;
    private String ldapPassword;
    private List<String> ldapTrees;
    private String syncTimes;
    private Boolean syncEnabled;
    private List<String> noTokenUrls;
    private Long cacheExpireMinutes; // 缓存过期时间(分钟)
    private Long redisTokenExpireMinutes; // Redis Token过期时间(分钟)

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

            // 定时同步配置
            syncTimes = getConfigValue(LDAP_SYNC_TIMES);
            String enabled = getConfigValue(LDAP_SYNC_ENABLED);
            syncEnabled = !"false".equalsIgnoreCase(enabled); // 默认开启

            log.info("LDAP 配置加载成功 - URL: {}, Base: {}, Trees: {}, SyncTimes: {}, SyncEnabled: {}",
                    ldapUrl, ldapBase, ldapTrees, syncTimes, syncEnabled);
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
     * 从数据库加载Redis Token过期时间配置
     */
    public void loadRedisTokenConfig() {
        try {
            String expireValue = docConfigRepository.findFirstByTypeAndKey(CONFIG_TYPE_REDIS, REDIS_TOKEN_EXPIRE)
                    .map(DocConfig::getValue)
                    .orElse(null);

            if (StringUtils.hasText(expireValue)) {
                redisTokenExpireMinutes = Long.parseLong(expireValue);
                log.info("Redis Token配置加载成功 - 过期时间: {} 分钟", redisTokenExpireMinutes);
            } else {
                // 如果数据库中未找到该配置或值为空，使用默认值72小时（4320分钟）
                redisTokenExpireMinutes = 4320L;
                log.warn("Redis Token配置未找到或为空，使用默认值: {} 分钟（72小时）", redisTokenExpireMinutes);
            }
        } catch (Exception e) {
            log.error("加载Redis Token配置失败: {}", e.getMessage(), e);
            // 使用默认值72小时（4320分钟）
            redisTokenExpireMinutes = 4320L;
        }
    }

    /**
     * 从数据库加载密钥配置（从config_secret_key表获取优先级最高的密钥）
     */
    public void loadSecretConfig() {
        try {
            // 查询order_num最大的配置密钥（优先级最高）
            ConfigSecretKey latestKey = configSecretKeyRepository.findFirstByOrderByOrderNumDesc()
                    .orElse(null);

            if (latestKey != null) {
                log.info("密钥配置加载成功，keyVersion: {}, orderNum: {}",
                        latestKey.getKeyVersion(), latestKey.getOrderNum());
            } else {
                log.warn("未找到任何配置密钥，请确保config_secret_key表中有数据");
            }
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
     * 获取定时同步触发时刻字符串（逗号分隔，如 "10:00,20:00"；未配置返回 null）
     */
    public String getSyncTimes() {
        return syncTimes;
    }

    /**
     * 定时同步是否开启（默认 true）
     */
    public boolean isSyncEnabled() {
        return syncEnabled != null && syncEnabled;
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
     * 获取Redis Token过期时间(分钟)
     */
    public Long getRedisTokenExpireMinutes() {
        return redisTokenExpireMinutes != null ? redisTokenExpireMinutes : 4320L;
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
     * 结构化保存 LDAP 配置（含连接信息、subTree 多值、定时同步周期与开关）。
     * <ul>
     *   <li>url/baseDn/username：非空即覆盖。</li>
     *   <li>password：仅当非空（且非纯空白）时覆盖现有密码；前端留空表示不修改。</li>
     *   <li>trees（subTree 多值）：整组替换——删除该 key 下全部旧记录后重新插入。</li>
     *   <li>syncTimes/syncEnabled：非空即覆盖（syncEnabled 存 "true"/"false"）。</li>
     * </ul>
     * 保存结束后重新加载内存配置。
     */
    public void saveLdapConfig(LdapConfigDto dto) {
        if (dto.getUrl() != null && !dto.getUrl().isBlank()) {
            setConfigValue(LDAP_URL, dto.getUrl());
        }
        if (dto.getBaseDn() != null && !dto.getBaseDn().isBlank()) {
            setConfigValue(LDAP_BASE_DN, dto.getBaseDn());
        }
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            setConfigValue(LDAP_USERNAME, dto.getUsername());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            setConfigValue(LDAP_PASSWORD, dto.getPassword());
        }
        if (dto.getTrees() != null) {
            replaceSubTrees(dto.getTrees());
        }
        if (dto.getSyncTimes() != null) {
            setConfigValue(LDAP_SYNC_TIMES, dto.getSyncTimes());
        }
        if (dto.getSyncEnabled() != null) {
            setConfigValue(LDAP_SYNC_ENABLED, String.valueOf(dto.getSyncEnabled()));
        }
        loadLdapConfig();
        log.info("[ConfigService] LDAP 配置已保存并重新加载");
    }

    /** 新增或更新单条配置（不存在则插入）。 */
    private void setConfigValue(String key, String value) {
        docConfigRepository.findFirstByTypeAndKey(CONFIG_TYPE_LDAP, key).ifPresentOrElse(
                config -> {
                    config.setValue(value);
                    docConfigRepository.save(config);
                },
                () -> {
                    DocConfig c = new DocConfig();
                    c.setType(CONFIG_TYPE_LDAP);
                    c.setKey(key);
                    c.setValue(value);
                    docConfigRepository.save(c);
                }
        );
    }

    /** subTree 整组替换：删除该 key 下全部旧记录，再插入非空白新值。 */
    private void replaceSubTrees(List<String> trees) {
        List<DocConfig> existing = docConfigRepository.findByTypeAndKey(CONFIG_TYPE_LDAP, LDAP_SUB_TREE);
        if (existing != null && !existing.isEmpty()) {
            docConfigRepository.deleteAll(existing);
        }
        for (String t : trees) {
            if (t != null && !t.trim().isBlank()) {
                DocConfig c = new DocConfig();
                c.setType(CONFIG_TYPE_LDAP);
                c.setKey(LDAP_SUB_TREE);
                c.setValue(t.trim());
                docConfigRepository.save(c);
            }
        }
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
        loadRedisTokenConfig();
    }

    /**
     * 使用公钥加密文本的业务逻辑（ECC加密）
     *
     * @param text       待加密的原始文本
     * @param keyVersion 密钥版本，默认为"default"
     * @return 加密响应对象，包含encryptedText、originalLength、encryptedLength和keyVersion
     * @throws RuntimeException 加密失败时抛出
     */
    public EncryptResponse encryptText(String text, String keyVersion) {
        // 根据keyVersion获取公钥
        String actualKeyVersion = keyVersion != null && !keyVersion.isEmpty() ? keyVersion : "default";

        ConfigSecretKey configKey;
        if ("latest".equals(actualKeyVersion)) {
            // 如果传入"latest"，获取优先级最高的密钥
            configKey = configSecretKeyRepository.findFirstByOrderByOrderNumDesc()
                    .orElseThrow(() -> new RuntimeException("系统配置错误：未找到配置密钥"));
        } else {
            // 根据keyVersion查询指定版本的密钥
            configKey = configSecretKeyRepository.findByKeyVersion(actualKeyVersion)
                    .orElseThrow(() -> new RuntimeException("系统配置错误：未找到密钥版本: " + actualKeyVersion));
        }

        String publicKey = configKey.getPublicKey();
        if (publicKey == null || publicKey.isEmpty()) {
            throw new RuntimeException("系统配置错误：公钥为空");
        }

        try {
            // 使用ECC公钥加密
            String encryptedText = EccUtil.encrypt(text, publicKey);

            // 构建响应对象
            EncryptResponse response = new EncryptResponse();
            response.setEncryptedText(encryptedText);
            response.setOriginalLength(text.length());
            response.setEncryptedLength(encryptedText.length());
            response.setKeyVersion(configKey.getKeyVersion());

            log.info("ECC加密成功 - keyVersion: {}, 原始长度: {}, 加密后长度: {}",
                    configKey.getKeyVersion(), text.length(), encryptedText.length());
            return response;
        } catch (Exception e) {
            log.error("ECC加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取最新的密钥版本和公钥
     *
     * @return 密钥信息响应对象
     * @throws RuntimeException 未找到密钥配置时抛出
     */
    public KeyInfoResponse getLatestKeyInfo() {
        // 查询order_num最大的配置密钥（优先级最高）
        ConfigSecretKey latestKey = configSecretKeyRepository.findFirstByOrderByOrderNumDesc()
                .orElseThrow(() -> new RuntimeException("系统配置错误：未找到配置密钥"));

        KeyInfoResponse response = new KeyInfoResponse();
        response.setKeyVersion(latestKey.getKeyVersion());
        response.setPublicKey(latestKey.getPublicKey());

        log.info("获取最新密钥信息成功 - keyVersion: {}", latestKey.getKeyVersion());
        return response;
    }

    /**
     * 使用私钥解密文本的业务逻辑（ECC解密）
     *
     * @param encryptedText ECC加密后的密文
     * @param keyVersion    密钥版本，默认为"default"
     * @return 解密响应对象，包含decryptedText和keyVersion
     * @throws RuntimeException 解密失败时抛出
     */
    public DecryptResponse decryptText(String encryptedText, String keyVersion) {
        // 根据keyVersion获取私钥
        String actualKeyVersion = keyVersion != null && !keyVersion.isEmpty() ? keyVersion : "default";

        ConfigSecretKey configKey;
        if ("latest".equals(actualKeyVersion)) {
            // 如果传入"latest"，获取优先级最高的密钥
            configKey = configSecretKeyRepository.findFirstByOrderByOrderNumDesc()
                    .orElseThrow(() -> new RuntimeException("系统配置错误：未找到配置密钥"));
        } else {
            // 根据keyVersion查询指定版本的密钥
            configKey = configSecretKeyRepository.findByKeyVersion(actualKeyVersion)
                    .orElseThrow(() -> new RuntimeException("系统配置错误：未找到密钥版本: " + actualKeyVersion));
        }

        String privateKey = configKey.getPrivateKey();
        if (privateKey == null || privateKey.isEmpty()) {
            throw new RuntimeException("系统配置错误：私钥为空");
        }

        try {
            // 使用ECC私钥解密
            String decryptedText = EccUtil.decrypt(encryptedText, privateKey);

            // 构建响应对象
            DecryptResponse response = new DecryptResponse();
            response.setDecryptedText(decryptedText);
            response.setKeyVersion(configKey.getKeyVersion());

            log.info("ECC解密成功 - keyVersion: {}, 解密后长度: {}",
                    configKey.getKeyVersion(), decryptedText.length());
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ECC解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败: " + e.getMessage(), e);
        }
    }
}
