package com.docauth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LDAP 定时全量同步调度器
 *
 * <p>动态调度：每次执行后由 {@link #nextExecutionTime(TriggerContext)} 依据
 * {@link ConfigService#getSyncTimes()} 现算下一触发时刻，故修改同步周期后下一轮即生效，无需重启。</p>
 *
 * <p>总开关 {@link ConfigService#isSyncEnabled()} 在每次 tick 内判定；未配置周期（syncTimes 为空）则不调度。</p>
 */
@Slf4j
@Component
public class LdapSyncScheduler {

    @Autowired
    private LdapSyncService ldapSyncService;
    @Autowired
    private ConfigService configService;

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    /** 未配置同步周期时的配置轮询间隔（分钟），保持调度存活以便配置变更后自动生效 */
    private static final long CONFIG_POLL_MINUTES = 5;

    @PostConstruct
    public void init() {
        // 确保配置已加载（syncTimes/syncEnabled 内存值可用）
        try {
            configService.loadLdapConfig();
        } catch (Exception e) {
            log.warn("[ldapSyncScheduler] 启动时加载 LDAP 配置失败：{}", e.getMessage());
        }
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ldap-sync-");
        scheduler.initialize();
        scheduler.schedule(this::tick, this::nextExecutionTime);
        log.info("[ldapSyncScheduler] 定时全量同步调度已启动");
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
    }

    private void tick() {
        try {
            if (!configService.isSyncEnabled()) {
                log.info("[ldapSyncScheduler] 自动同步已禁用(syncEnabled=false)，跳过本次");
                return;
            }
            if (parseSyncTimes(configService.getSyncTimes()).isEmpty()) {
                // 未配置周期：仅轮询，不执行同步（等待配置生效后下一轮才真正同步）
                return;
            }
            ldapSyncService.fullSync();
        } catch (Exception e) {
            log.error("[ldapSyncScheduler] 定时全量同步执行异常", e);
        }
    }

    private Instant nextExecutionTime(TriggerContext ctx) {
        List<LocalTime> times = parseSyncTimes(configService.getSyncTimes());
        if (times.isEmpty()) {
            // 未配置周期：返回未来轮询点，保持调度存活以便配置变更后自动生效（无需重启）
            return LocalDateTime.now().plusMinutes(CONFIG_POLL_MINUTES)
                    .atZone(ZoneId.systemDefault()).toInstant();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime candidate = null;
        for (LocalTime t : times) {
            LocalDateTime today = now.with(t);
            LocalDateTime next = today.isAfter(now) ? today : today.plusDays(1);
            if (candidate == null || next.isBefore(candidate)) {
                candidate = next;
            }
        }
        if (candidate == null) {
            return null;
        }
        return candidate.atZone(ZoneId.systemDefault()).toInstant();
    }

    private List<LocalTime> parseSyncTimes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<LocalTime> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("H:m");
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                result.add(LocalTime.parse(s, fmt));
            } catch (Exception e) {
                log.warn("[ldapSyncScheduler] 无法解析同步时间：{}", s);
            }
        }
        return result;
    }
}
