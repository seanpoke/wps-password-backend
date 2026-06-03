package com.docauth.config;

import com.docauth.interceptor.TokenInterceptor;
import com.docauth.service.ConfigService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Autowired
    private ConfigService configService;

    /**
     * 应用启动时加载系统配置
     */
    @PostConstruct
    public void init() {
        configService.loadSysConfig();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 从数据库获取无需 Token 验证的 URL 列表
        var noTokenUrls = configService.getNoTokenUrls();

        // 合并数据库配置和硬编码的排除路径
        String[] excludePatterns = new String[noTokenUrls.size() + 4];
        for (int i = 0; i < noTokenUrls.size(); i++) {
            excludePatterns[i] = noTokenUrls.get(i);
        }
        excludePatterns[excludePatterns.length - 4] = "/account/logout";
        excludePatterns[excludePatterns.length - 3] = "/swagger-ui/**";
        excludePatterns[excludePatterns.length - 2] = "/v3/api-docs/**";
        excludePatterns[excludePatterns.length - 1] = "/webjars/**";

        // 注册Token拦截器，对所有请求进行拦截（除了排除的路径）
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(excludePatterns);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置跨域访问
        registry.addMapping("/**")
                .allowedOriginPatterns("*")  // 允许所有来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的HTTP方法
                .allowedHeaders("*")  // 允许所有请求头
                .allowCredentials(true)  // 允许携带凭证（Cookie、Token等）
                .maxAge(3600);  // 预检请求缓存时间（秒）
    }
}
