package com.docauth.context;

import lombok.Data;

/**
 * 用户信息上下文
 */
@Data
public class UserContext {
    /**
     * 用户账号
     */
    private String account;

    /**
     * 用户名称
     */
    private String name;
}
