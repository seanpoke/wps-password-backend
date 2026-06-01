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
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class TokenInterceptor implements HandlerInterceptor {
    
    @Autowired
    private RedisUtil redisUtil;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private com.docauth.service.ConfigService configService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头获取token
        String token = request.getHeader("token");
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String jsonResponse = objectMapper.writeValueAsString(ApiResponse.error(401, "token invalid"));
            response.getWriter().write(jsonResponse);
            return false;
        }
        
        // 从Redis中获取用户对象
        UserContext userContext = redisUtil.getObject(token, UserContext.class);
        if (userContext == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String jsonResponse = objectMapper.writeValueAsString(ApiResponse.error(401, "token invalid"));
            response.getWriter().write(jsonResponse);
            return false;
        }
        
        // 从配置中获取Redis Token过期时间（单位：分钟）
        Long expireMinutes = configService.getRedisTokenExpireMinutes();
        // 刷新token过期时间
        redisUtil.expire(token, expireMinutes, TimeUnit.MINUTES);
        
        // 将用户上下文存储到ThreadLocal
        UserContextHolder.setUserContext(userContext);
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求完成后清除ThreadLocal，防止内存泄漏
        UserContextHolder.clear();
    }
}