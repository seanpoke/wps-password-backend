package com.docauth;

import com.docauth.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class DocAuthApplication implements CommandLineRunner {

    @Autowired
    private ConfigService configService;

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

}
