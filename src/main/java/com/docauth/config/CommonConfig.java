package com.docauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 通用配置：提供 BCrypt 密码编码器（本地外部用户密码存储）与默认 @Scheduled 调度线程池
 */
@Configuration
public class CommonConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 默认 @Scheduled 调度器线程池（覆盖 Spring Boot 默认的单线程调度器）。
     * 多个 @Scheduled 任务可并行执行，不再互相串行排队。
     * 注意：LDAP 同步调度器（LdapSyncScheduler）使用自己独立的 ThreadPoolTaskScheduler，不受此 bean 影响。
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
