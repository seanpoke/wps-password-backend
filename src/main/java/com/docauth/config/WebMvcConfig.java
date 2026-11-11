package com.docauth.config;

import com.docauth.interceptor.TokenInterceptor;
import com.docauth.service.AppVersionService;
import com.docauth.service.ConfigService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Autowired
    private ConfigService configService;

    /** 客户端版本安装包落盘目录（见 application.yml 的 app.version.file-dir） */
    @Value("${app.version.file-dir:./versionFile}")
    private String versionFileDir;

    /**
     * 应用启动时加载系统配置
     */
    @PostConstruct
    public void init() {
        configService.loadSysConfig();
    }

    /**
     * 客户端版本安装包静态下载映射：/downloads/** 对应 file:{versionFileDir}/。
     * versionFileDir/tmp/ 是重新上传的暂存目录（未发布），必须在这里显式拒绝：
     * 否则 /downloads/tmp/{uuid}__android__1.0.5.apk 会被匿名直接下载。
     * 线上 nginx 为纯反代（无 alias 直读盘），因此这一层拦截是唯一且足够的防线。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = StringUtils.trimTrailingCharacter(versionFileDir, '/');
        try {
            Files.createDirectories(Path.of(dir));
        } catch (Exception e) {
            // 目录创建失败不阻断启动，仅记录；真正写入时再报错
            org.slf4j.LoggerFactory.getLogger(WebMvcConfig.class)
                    .warn("[WebMvcConfig] 版本安装包目录创建失败: {}", dir, e);
        }
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations("file:" + dir + "/")
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 按路径段判断，兼容 ./tmp、..\tmp 等未经规范化的写法；
                        // 已发布包名均为 PasswordManager-*，不含 tmp 段，不会被误拒。
                        String normalized = resourcePath.replace('\\', '/');
                        for (String segment : normalized.split("/")) {
                            if (AppVersionService.STAGING_SUBDIR.equalsIgnoreCase(segment)) {
                                return null;
                            }
                        }
                        return super.getResource(resourcePath, location);
                    }
                });
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
        // 修改密码接口无需 token，按请求体中的 account 校验，放行拦截器
        excludes.add("/account/change-password");
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
        // 版本安装包静态下载（免鉴权，匿名可访问）
        excludes.add("/downloads/**");

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
