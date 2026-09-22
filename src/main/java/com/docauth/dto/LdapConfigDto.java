package com.docauth.dto;

import lombok.Data;

import java.util.List;

/**
 * LDAP 配置（含连接信息、subTree 多值、定时同步周期与开关）。
 * 用于 GET /config/ldap 响应结构与 PUT /config/ldap 请求体。
 */
@Data
public class LdapConfigDto {

    /** LDAP 服务地址 */
    private String url;

    /** 目录根 DN（仅用于登录搜索基与目录边界，不作为同步范围） */
    private String baseDn;

    /** 绑定账号 */
    private String username;

    /**
     * 绑定密码。
     * 保存时为「非空才覆盖」语义：前端留空表示不修改现有密码。
     */
    private String password;

    /** subTree 多值：每条独立作为同步根（SUBTREE 搜索），逐根从 LDAP 拉取 */
    private List<String> trees;

    /**
     * 定时全量同步触发时刻（逗号分隔，如 "10:00,20:00"）。
     * 页面用时间选择器编辑，后端不暴露 cron。
     */
    private String syncTimes;

    /** 定时同步总开关（默认 true） */
    private Boolean syncEnabled;
}
