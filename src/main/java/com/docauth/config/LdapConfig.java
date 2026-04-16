package com.docauth.config;

import com.docauth.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LdapConfig {
    
    @Autowired
    private ConfigService configService;
    
    /**
     * 配置 LDAP 连接源
     * @return LdapContextSource
     */
    @Bean
    public LdapContextSource ldapContextSource() {
        // 确保配置已加载
        configService.loadLdapConfig();
        
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(configService.getLdapUrl());
        // 设置为空，让查询可以使用完整的 DN 路径
        contextSource.setBase("");
        // 设置管理员账号用于查询 LDAP
        contextSource.setUserDn(configService.getLdapUsername());
        contextSource.setPassword(configService.getLdapPassword());
        // 重要：设置为简单认证，避免使用 SASL
        contextSource.setPooled(false);
        
        // 添加额外的环境属性
        java.util.Map<String, Object> env = new java.util.HashMap<>();
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");
        contextSource.setBaseEnvironmentProperties(env);
        
        return contextSource;
    }
    
    /**
     * 配置 LDAP 模板
     * @param contextSource LDAP 连接源
     * @return LdapTemplate
     */
    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
        ldapTemplate.setIgnorePartialResultException(true);
        return ldapTemplate;
    }
}
