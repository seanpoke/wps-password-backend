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

    @Schema(description = "用户角色 (admin 超级管理员 / user 普通用户)", example = "user")
    private String role;
}