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
        // 硬编码放行：接口类
        // 注意：不再把 noTokenUrls（/account/login、/config/**）加入 excludePathPatterns，
        // 改由 TokenInterceptor 以“可选鉴权”方式处理：白名单路径匿名可访问（如 GET /config/ldap），
        // 但携带 token 时仍填充 UserContext，使 ConfigController.assertAdmin 等鉴权生效。
        var excludes = new java.util.ArrayList<String>();
        // 硬编码放行：接口类
        excludes.add("/account/logout");
        excludes.add("/swagger-ui.html");
        excludes.add("/swagger-ui/**");
        excludes.add("/v3/api-docs/**");
        excludes.add("/webjars/**");
        // 硬编码放行：前端静态页面（验证页 + 新管理后台 SPA）
        excludes.add("/verify.html");
        excludes.add("/admin.html");
        excludes.add("/");
        excludes.add("/index.html");
        excludes.add("/assets/**");

        // 注册Token拦截器，对所有请求进行拦截（除了排除的路径）
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(excludes);
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
