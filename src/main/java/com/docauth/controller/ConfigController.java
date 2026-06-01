package com.docauth.controller;

import com.docauth.dto.*;
import com.docauth.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/config")
@Tag(name = "系统配置管理", description = "LDAP 配置管理接口")
public class ConfigController {

    @Autowired
    private ConfigService configService;

    /**
     * 获取 LDAP 配置信息
     */
    @GetMapping("/ldap")
    @Operation(summary = "获取 LDAP 配置", description = "获取当前 LDAP 配置信息(不返回密码)")
    public ApiResponse<Map<String, Object>> getLdapConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("url", configService.getLdapUrl());
        config.put("base", configService.getLdapBase());
        config.put("username", configService.getLdapUsername());
        config.put("trees", configService.getLdapTrees());

        return ApiResponse.success(config);
    }

    /**
     * 更新 LDAP 配置
     */
    @PutMapping("/ldap")
    @Operation(summary = "更新 LDAP 配置", description = "更新 LDAP 配置并自动重新加载")
    public ApiResponse<String> updateLdapConfig(@RequestBody Map<String, String> configs) {
        try {
            for (Map.Entry<String, String> entry : configs.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 更新配置(直接使用 key,不需要 ldap. 前缀)
                configService.updateConfig(key, value);
            }

            return ApiResponse.success("配置更新成功");
        } catch (Exception e) {
            log.error("更新配置失败: {}", e.getMessage(), e);
            return ApiResponse.error("配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 刷新配置
     */
    @GetMapping("/refresh")
    @Operation(summary = "刷新配置", description = "从数据库重新加载所有配置")
    public ApiResponse<String> refreshConfig() {
        try {
            configService.refreshConfig();
            return ApiResponse.success("配置刷新成功");
        } catch (Exception e) {
            log.error("刷新配置失败: {}", e.getMessage(), e);
            return ApiResponse.error("配置刷新失败: " + e.getMessage());
        }
    }

    /**
     * 使用公钥加密字符串
     */
    @PostMapping("/encrypt")
    @Operation(summary = "公钥加密", description = "使用系统公钥对传入的字符串进行ECC加密")
    public ApiResponse<EncryptResponse> encrypt(@RequestBody EncryptRequest request) {
        // 校验参数
        if (request.getText() == null || request.getText().isEmpty()) {
            return ApiResponse.error(400, "参数错误：text不能为空");
        }

        try {
            // 调用Service处理加密业务逻辑
            EncryptResponse result = configService.encryptText(request.getText(), request.getKeyVersion());
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("加密失败: {}", e.getMessage());
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            log.error("加密异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "加密失败：系统异常");
        }
    }

    /**
     * 获取最新的密钥版本和公钥
     */
    @GetMapping("/latest-key")
    @Operation(summary = "获取最新密钥信息", description = "获取当前优先级最高的密钥版本和公钥")
    public ApiResponse<KeyInfoResponse> getLatestKeyInfo() {
        try {
            // 调用Service获取最新密钥信息
            KeyInfoResponse result = configService.getLatestKeyInfo();
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("获取密钥信息失败: {}", e.getMessage());
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            log.error("获取密钥信息异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取密钥信息失败：系统异常");
        }
    }

    /**
     * 使用私钥解密字符串
     */
    @PostMapping("/decrypt")
    @Operation(summary = "私钥解密", description = "使用系统私钥对传入的密文进行ECC解密")
    public ApiResponse<DecryptResponse> decrypt(@RequestBody DecryptRequest request) {
        // 校验参数
        if (request.getEncryptedText() == null || request.getEncryptedText().isEmpty()) {
            return ApiResponse.error(400, "参数错误：encryptedText不能为空");
        }

        try {
            // 调用Service处理解密业务逻辑
            DecryptResponse result = configService.decryptText(
                    request.getEncryptedText(),
                    request.getKeyVersion()
            );
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            log.warn("解密失败: {}", e.getMessage());
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            log.error("解密异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "解密失败：系统异常");
        }
    }
}
