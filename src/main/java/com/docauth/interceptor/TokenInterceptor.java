package com.docauth.interceptor;

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
        // 跳过登录接口
        if (request.getRequestURI().equals("/account/login")) {
            return true;
        }
        
        // 从请求头获取token
        String token = request.getHeader("token");
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("未授权：缺少token");
            return false;
        }
        
        // 校验token是否存在
        String account = redisUtil.get(token);
        if (account == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("未授权：token无效或已过期");
            return false;
        }
        
        // 刷新token过期时间（72小时）
        redisUtil.expire(token, 72, TimeUnit.HOURS);
        
        // 将account存储到请求中，供后续接口使用
        request.setAttribute("account", account);
        
        return true;
    }
}