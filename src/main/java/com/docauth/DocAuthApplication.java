package com.docauth;

import com.docauth.service.ConfigService;
import com.docauth.service.PasswordLogWriterService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class DocAuthApplication implements CommandLineRunner {

    @Autowired
    private ConfigService configService;

    @Autowired
    private PasswordLogWriterService passwordLogWriterService;

    public static void main(String[] args) {
        SpringApplication.run(DocAuthApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 应用启动时加载所有配置
        configService.loadLdapConfig();
        configService.loadSysConfig();
        configService.loadCacheConfig();
        configService.loadSecretConfig();
        configService.loadRedisTokenConfig();

        log.info("[DocAuthApplication] 应用启动完成");
    }

    /**
     * 应用关闭时的清理工作
     */
    @PreDestroy
    public void cleanup() {
        log.info("[DocAuthApplication] 应用正在关闭，执行清理工作...");
        passwordLogWriterService.shutdown();
    }
}
