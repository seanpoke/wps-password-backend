package com.docauth.util;

import com.docauth.context.UserContextHolder;

/**
 * 用户信息工具类
 * 提供便捷方法获取当前登录用户信息
 */
public class UserUtil {

    /**
     * 获取当前登录用户账号
     *
     * @return 用户账号
     */
    public static String getCurrentAccount() {
        return UserContextHolder.getCurrentAccount();
    }

    /**
     * 获取当前登录用户名称
     *
     * @return 用户名称
     */
    public static String getCurrentName() {
        return UserContextHolder.getCurrentName();
    }

    /**
     * 检查当前用户是否已登录
     *
     * @return true-已登录，false-未登录
     */
    public static boolean isLogin() {
        return UserContextHolder.getUserContext() != null;
    }
}
