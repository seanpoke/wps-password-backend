package com.docauth.context;

/**
 * 用户上下文持有者
 * 使用ThreadLocal存储当前请求的用户信息
 */
public class UserContextHolder {
    
    private static final ThreadLocal<UserContext> userContextThreadLocal = new ThreadLocal<>();
    
    /**
     * 设置用户上下文
     * @param userContext 用户上下文
     */
    public static void setUserContext(UserContext userContext) {
        userContextThreadLocal.set(userContext);
    }
    
    /**
     * 获取用户上下文
     * @return 用户上下文
     */
    public static UserContext getUserContext() {
        return userContextThreadLocal.get();
    }
    
    /**
     * 获取当前用户账号
     * @return 用户账号
     */
    public static String getCurrentAccount() {
        UserContext userContext = getUserContext();
        return userContext != null ? userContext.getAccount() : null;
    }
    
    /**
     * 获取当前用户名称
     * @return 用户名称
     */
    public static String getCurrentName() {
        UserContext userContext = getUserContext();
        return userContext != null ? userContext.getName() : null;
    }
    
    /**
     * 清除用户上下文（防止内存泄漏）
     */
    public static void clear() {
        userContextThreadLocal.remove();
    }
}
