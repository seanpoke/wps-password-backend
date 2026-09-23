package com.docauth.interceptor;

import com.docauth.context.UserContext;
import com.docauth.context.UserContextHolder;
import com.docauth.dto.ApiResponse;
import com.docauth.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.docauth.service.ConfigService configService;

    private final AntPathMatcher ant = new AntPathMatcher();
    // no-token 白名单带 TTL 缓存，避免每次请求打 DB
    private volatile List<String> cachedNoTokenUrls;
    private volatile long cachedAt;
    private static final long TTL_MILLS = 60_000L;

    private List<String> noTokenUrls() {
        long now = System.currentTimeMillis();
        if (cachedNoTokenUrls == null || now - cachedAt > TTL_MILLS) {
            synchronized (this) {
                long n2 = System.currentTimeMillis();
                if (cachedNoTokenUrls == null || n2 - cachedAt > TTL_MILLS) {
                    cachedNoTokenUrls = configService.getNoTokenUrls();
                    cachedAt = n2;
                }
            }
        }
        return cachedNoTokenUrls;
    }

    private boolean isOptional(String uri) {
        for (String p : noTokenUrls()) {
            if (p.endsWith("/**")) {
                if (ant.match(p, uri)) return true;
            } else if (p.equals(uri) || p.equals(uri + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean sendUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, "token invalid")));
        return false;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头获取token
        String token = request.getHeader("token");
        String uri = request.getRequestURI();
        boolean optional = isOptional(uri);

        if (token == null || token.trim().isEmpty()) {
            // 白名单路径（如 GET /config/ldap、/account/login）允许匿名访问
            if (optional) return true;
            return sendUnauthorized(response);
        }

        // 从Redis中获取用户对象
        UserContext userContext = redisUtil.getObject(token, UserContext.class);
        if (userContext == null) {
            // 白名单路径带无效 token 也放行（不填充上下文），其余路径拒绝
            if (optional) return true;
            return sendUnauthorized(response);
        }

        // 从配置中获取Redis Token过期时间（单位：分钟）
        Long expireMinutes = configService.getRedisTokenExpireMinutes();
        // 刷新token过期时间
        redisUtil.expire(token, expireMinutes, TimeUnit.MINUTES);

        // 将用户上下文存储到ThreadLocal（使 assertAdmin 等鉴权可用）
        UserContextHolder.setUserContext(userContext);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求完成后清除ThreadLocal，防止内存泄漏
        UserContextHolder.clear();
    }
}