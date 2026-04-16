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
}