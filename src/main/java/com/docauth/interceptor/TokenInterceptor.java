package com.docauth.interceptor;

import com.docauth.context.UserContext;
import com.docauth.context.UserContextHolder;
import com.docauth.util.RedisUtil;
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
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头获取token
        String token = request.getHeader("token");
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("未授权：缺少token");
            return false;
        }
        
        // 从Redis中获取用户对象
        UserContext userContext = redisUtil.getObject(token, UserContext.class);
        if (userContext == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("未授权：token无效或已过期");
            return false;
        }
        
        // 刷新token过期时间（72小时）
        redisUtil.expire(token, 72, TimeUnit.HOURS);
        
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