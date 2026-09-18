package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录响应")
public class LoginResponse {
    @Schema(description = "访问令牌", example = "550e8400-e29b-41d4-a716-446655440000")
    private String token;

    @Schema(description = "用户账号", example = "zangsan")
    private String account;

    @Schema(description = "用户姓名", example = "张三")
    private String name;

    @Schema(description = "用户角色 (取优先级最高的角色 code，admin 超级管理员 / user 普通用户)", example = "user")
    private String role;

    @Schema(description = "用户拥有的全部角色 code 列表（权限计算取并集）", example = "[\"user\"]")
    private java.util.List<String> roles;

    @Schema(description = "身份源 (LDAP 内部用户 / LOCAL 本地外部用户)", example = "LDAP")
    private String source;

    @Schema(description = "是否需要首次修改密码(本地用户)", example = "false")
    private Boolean needChangePwd;
}