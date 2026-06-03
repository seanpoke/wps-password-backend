package com.docauth.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 密码日志文件写入服务
 * 基于logback异步非阻塞的高性能文件写入方案（v2.3）
 */
@Slf4j
@Service
public class PasswordLogWriterService {

    /**
     * 日志消息对象
     */
    @Data
    public static class LogMessage {
        private String uid;
        private String path;
        private String beforePassword;
        private String afterPassword;
        private List<String> possiblePasswordList;
        private String platform;
        private String createBy;
        private String keyVersion;  // 密钥版本号
        // 注意：不再需要 timestamp 字段，由 Logback 自动添加
    }

    // 专门的密码审计 logger
    private static final org.slf4j.Logger PASSWORD_LOGGER = org.slf4j.LoggerFactory.getLogger("PASSWORD_AUDIT_LOGGER");

    @PostConstruct
    public void init() {
        log.info("[PasswordLogWriterService] 密码日志写入服务已启动，使用logback异步非阻塞方式");
    }

    /**
     * 将日志消息通过logback异步记录（绝对非阻塞）
     *
     * @param message 日志消息
     */
    public void offerLog(LogMessage message) {
        try {
            // 格式化日志内容（不包含时间戳，交给 Logback 处理）
            String formattedLog = formatLogLine(message);
            
            // 使用专门的logger记录密码审计日志
            PASSWORD_LOGGER.info(formattedLog);
            
            log.debug("[PasswordLogWriterService] 密码审计日志已提交到logback: uid={}", message.getUid());
        } catch (Exception e) {
            log.error("[PasswordLogWriterService] 提交密码审计日志失败: uid={}, error={}", 
                    message.getUid(), e.getMessage(), e);
        }
    }

    /**
     * 格式化日志行（不包含时间戳）
     * 格式：uid | path | beforePassword | afterPassword | possiblePassword | platform | createBy | keyVersion
     * 
     * 注意：时间戳由 Logback 的 Pattern 自动添加，利用 CachingDateFormatter 优化性能
     *
     * @param message 日志消息
     * @return 格式化后的日志字符串
     */
    private String formatLogLine(LogMessage message) {
        StringBuilder sb = new StringBuilder();
        
        // uid
        sb.append(message.getUid() != null ? message.getUid() : "");
        
        // path
        sb.append(" | ").append(message.getPath() != null ? message.getPath() : "");
        
        // beforePassword
        sb.append(" | ").append(message.getBeforePassword() != null ? message.getBeforePassword() : "");
        
        // afterPassword
        sb.append(" | ").append(message.getAfterPassword() != null ? message.getAfterPassword() : "");
        
        // possiblePassword（列表转为逗号分隔的字符串）
        if (message.getPossiblePasswordList() != null && !message.getPossiblePasswordList().isEmpty()) {
            sb.append(" | ");
            for (int i = 0; i < message.getPossiblePasswordList().size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(message.getPossiblePasswordList().get(i));
            }
        } else {
            sb.append(" | ");
        }
        
        // platform
        sb.append(" | ").append(message.getPlatform() != null ? message.getPlatform() : "");
        
        // createBy
        sb.append(" | ").append(message.getCreateBy() != null ? message.getCreateBy() : "");
        
        // keyVersion
        sb.append(" | ").append(message.getKeyVersion() != null ? message.getKeyVersion() : "");
        
        return sb.toString();
    }

    /**
     * 优雅关闭（在应用关闭时调用）
     */
    public void shutdown() {
        log.info("[PasswordLogWriterService] 开始关闭日志写入服务...");
        log.info("[PasswordLogWriterService] 日志写入服务已关闭（logback会自动处理剩余日志）");
    }
}
