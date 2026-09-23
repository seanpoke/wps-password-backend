package com.docauth.context;

import lombok.Data;

import java.util.List;

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

    /**
     * 身份来源：LDAP(内部) / LOCAL(外部三方)
     */
    private String source;

    /**
     * 角色：admin(超级管理员) / user(普通用户) —— 取优先级最高的角色 code（仅用于默认展示）
     */
    private String role;

    /**
     * 用户拥有的全部角色 code 列表（权限计算取并集，不按优先级）
     */
    private List<String> roles;
}
